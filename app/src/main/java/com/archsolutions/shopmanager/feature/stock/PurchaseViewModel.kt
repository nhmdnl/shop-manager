package com.archsolutions.shopmanager.feature.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.PurchaseLineItem
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

data class PurchaseLineUi(val product: ProductEntity, val qty: Double, val unitCost: Long?) {
    val lineTotal: Long get() = (qty * (unitCost ?: 0L)).roundToLong()
}

data class PurchaseUiState(
    val loaded: Boolean = false,
    val suppliers: List<SupplierEntity> = emptyList(),
    val supplierId: Long? = null,
    val products: List<ProductEntity> = emptyList(),
    val lines: List<PurchaseLineUi> = emptyList(),
    val paid: PurchasePaid = PurchasePaid.CASH,
    val saving: Boolean = false,
) {
    val total: Long get() = lines.sumOf { it.qty * (it.unitCost ?: 0L) }.roundToLong()

    /** "Saving adds 54 items to stock" (5.3). */
    val itemsAdded: Double get() = lines.sumOf { it.qty }
    val canSave: Boolean
        get() = lines.isNotEmpty() && lines.all { it.qty > 0.0 && (it.unitCost ?: 0L) > 0L } && !saving

    fun contains(productId: Long): Boolean = lines.any { it.product.id == productId }
}

private data class PurchaseLineInput(val qty: Double, val unitCost: Long?)

private data class PurchaseInputs(
    val supplierId: Long? = null,
    val lines: Map<Long, PurchaseLineInput> = emptyMap(), // productId -> line, in the order added
    val paid: PurchasePaid = PurchasePaid.CASH,
    val saving: Boolean = false,
)

/** Add purchase form: the tablet Stock panel and the phone screen (AddPurchaseRoute). */
@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val productRepo: ProductRepo,
    supplierRepo: SupplierRepo,
    private val purchaseRepo: PurchaseRepo,
    dayGate: DayGate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // AddPurchaseRoute(productId, supplierId) uses 0 for "none"; the tablet panel has neither key.
    private val inputs = MutableStateFlow(
        PurchaseInputs(supplierId = savedStateHandle.get<Long>("supplierId")?.takeIf { it > 0 })
    )
    private val gateState = dayGate.state.stateIn(viewModelScope, SharingStarted.Eagerly, DayGateState.Loading)

    private val _savedTotal = MutableStateFlow<Long?>(null)

    /** Total of the purchase just saved, for the confirmation message. */
    val savedTotal: StateFlow<Long?> = _savedTotal.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    val state: StateFlow<PurchaseUiState> = combine(
        inputs,
        productRepo.getAllActive(),
        supplierRepo.getAllActive(),
    ) { input, products, suppliers ->
        val productsById = products.associateBy { it.id }
        PurchaseUiState(
            loaded = true,
            suppliers = suppliers,
            supplierId = input.supplierId,
            products = products,
            lines = input.lines.mapNotNull { (id, line) ->
                productsById[id]?.let { PurchaseLineUi(it, line.qty, line.unitCost) }
            },
            paid = input.paid,
            saving = input.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseUiState())

    init {
        savedStateHandle.get<Long>("productId")?.takeIf { it > 0 }?.let(::addProduct)
    }

    /** "+ Purchase" on a row or a pick from "Add product": one more, at the product's current cost. */
    fun addProduct(productId: Long) {
        viewModelScope.launch {
            val product = productRepo.getByIdDirect(productId) ?: return@launch
            inputs.update { input ->
                val existing = input.lines[productId]
                val line = existing?.copy(qty = existing.qty + 1.0) ?: PurchaseLineInput(1.0, product.costPrice)
                input.copy(lines = input.lines + (productId to line))
            }
        }
    }

    fun setQty(productId: Long, qty: Double) = inputs.update { input ->
        val line = input.lines[productId] ?: return@update input
        input.copy(lines = if (qty <= 0.0) input.lines - productId else input.lines + (productId to line.copy(qty = qty)))
    }

    fun setCost(productId: Long, cost: Long?) = inputs.update { input ->
        val line = input.lines[productId] ?: return@update input
        input.copy(lines = input.lines + (productId to line.copy(unitCost = cost)))
    }

    fun setSupplier(supplierId: Long?) = inputs.update { it.copy(supplierId = supplierId) }

    fun setPaid(paid: PurchasePaid) = inputs.update { it.copy(paid = paid) }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        inputs.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val gate = gateState.first { it != DayGateState.Loading }
                val day = (gate as? DayGateState.Ready)?.day ?: throw IllegalStateException("The day is not open")
                purchaseRepo.savePurchase(
                    dayId = day.id,
                    supplierId = current.supplierId,
                    paid = current.paid,
                    items = current.lines.map { PurchaseLineItem(it.product.id, it.qty, it.unitCost ?: 0L) },
                )
                _savedTotal.value = current.total
                inputs.update { PurchaseInputs(supplierId = it.supplierId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                inputs.update { it.copy(saving = false) }
                _saveFailed.value = true
            }
        }
    }

    fun savedShown() {
        _savedTotal.value = null
    }

    fun saveFailedShown() {
        _saveFailed.value = false
    }
}
