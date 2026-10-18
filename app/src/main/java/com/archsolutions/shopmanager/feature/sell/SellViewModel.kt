package com.archsolutions.shopmanager.feature.sell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.data.db.dao.CustomerWithOwed
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SaleCartItem
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.domain.CartLine
import com.archsolutions.shopmanager.domain.CartMath
import com.archsolutions.shopmanager.domain.CashQuickAmounts
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

data class SellUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val categoryId: Long? = null,
    val products: List<ProductEntity> = emptyList(),
    val lines: List<CartLine> = emptyList(),
    val method: PayMethod = PayMethod.CASH,
    val cashReceived: Long? = null,
    val customerQuery: String = "",
    val customers: List<CustomerWithOwed> = emptyList(),
    val selectedCustomer: CustomerWithOwed? = null,
    val saving: Boolean = false,
) {
    val total: Long get() = CartMath.total(lines)
    val itemCount: Int get() = CartMath.itemCount(lines)
    val quickAmounts: List<Long> get() = CashQuickAmounts.forTotal(total)
    val change: Long? get() = if (method == PayMethod.CASH) CartMath.change(total, cashReceived) else null
    val owedAfterSale: Long? get() = selectedCustomer?.let { it.owed + total }
    val canComplete: Boolean
        get() = lines.isNotEmpty() && !saving && (method != PayMethod.CREDIT || selectedCustomer != null)

    fun qtyOf(productId: Long): Double? = lines.firstOrNull { it.product.id == productId }?.qty
}

/** A completed sale, shown in the 6-second "Sale saved" snackbar with Undo. */
data class SavedSale(
    val saleId: Long,
    val method: PayMethod,
    val total: Long,
    val change: Long?,
    // Monotonic time of the save, so the Undo window stays 6 s even if the screen is left and reopened.
    val savedAtMillis: Long = System.nanoTime() / 1_000_000,
)

// Everything the user has picked, in one place so it can be saved and restored as a whole.
private data class SellInputs(
    val query: String = "",
    val categoryId: Long? = null,
    val cart: Map<Long, Double> = emptyMap(), // productId -> qty, in the order added
    val method: PayMethod = PayMethod.CASH,
    val cashReceived: Long? = null,
    val customerQuery: String = "",
    val customerId: Long? = null,
    val saving: Boolean = false,
)

/** Shared by Sell and phone Checkout: scoped to their navigation graph, cart kept in SavedStateHandle (7.9). */
@HiltViewModel
class SellViewModel @Inject constructor(
    productRepo: ProductRepo,
    private val customerRepo: CustomerRepo,
    private val saleRepo: SaleRepo,
    dayGate: DayGate,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val inputs = MutableStateFlow(restoreInputs())
    private val gateState = dayGate.state.stateIn(viewModelScope, SharingStarted.Eagerly, DayGateState.Loading)

    private val _savedSale = MutableStateFlow<SavedSale?>(null)
    val savedSale: StateFlow<SavedSale?> = _savedSale.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    val state: StateFlow<SellUiState> = combine(
        inputs,
        productRepo.getAllActive(),
        productRepo.getCategories(),
        customerRepo.getAllWithOwed(),
    ) { input, products, categories, customers ->
        val productsById = products.associateBy { it.id }
        val query = input.query.trim()
        val customerQuery = input.customerQuery.trim()
        SellUiState(
            loaded = true,
            query = input.query,
            categories = categories,
            categoryId = input.categoryId,
            products = products.filter { product ->
                (input.categoryId == null || product.categoryId == input.categoryId) &&
                    (query.isEmpty() || product.name.contains(query, ignoreCase = true) || product.barcode == query)
            },
            lines = input.cart.mapNotNull { (id, qty) -> productsById[id]?.let { CartLine(it, qty) } },
            method = input.method,
            cashReceived = input.cashReceived,
            customerQuery = input.customerQuery,
            customers = customers.filter {
                customerQuery.isEmpty() ||
                    it.customer.name.contains(customerQuery, ignoreCase = true) ||
                    it.customer.phone?.contains(customerQuery) == true
            },
            selectedCustomer = customers.firstOrNull { it.customer.id == input.customerId },
            saving = input.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellUiState())

    fun setQuery(query: String) = edit { it.copy(query = query) }

    fun setCategory(categoryId: Long?) = edit { it.copy(categoryId = categoryId) }

    /** Tile tap: one more of this product (7.2). */
    fun addOne(productId: Long) = edit { it.copy(cart = it.cart + (productId to (it.cart[productId] ?: 0.0) + 1.0)) }

    /** Stepper or quantity dialog; zero or less removes the line. */
    fun setQty(productId: Long, qty: Double) = edit {
        it.copy(cart = if (qty <= 0.0) it.cart - productId else it.cart + (productId to qty))
    }

    fun clear() = edit { SellInputs(query = it.query, categoryId = it.categoryId) }

    fun setMethod(method: PayMethod) = edit { it.copy(method = method) }

    fun setCashReceived(amount: Long?) = edit { it.copy(cashReceived = amount) }

    fun setCustomerQuery(query: String) = edit { it.copy(customerQuery = query) }

    fun selectCustomer(customerId: Long) = edit { it.copy(customerId = customerId) }

    /** Quick add from the credit picker: saves the customer and selects them. */
    fun addCustomer(name: String, phone: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = customerRepo.saveCustomer(trimmed, phone.trim().ifEmpty { null })
            edit { it.copy(customerId = id, customerQuery = "") }
        }
    }

    fun completeSale() {
        val current = state.value
        if (!current.canComplete) return
        edit { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                // Wait for the gate's first answer rather than dropping a tap made while it loads.
                val gate = gateState.first { it != DayGateState.Loading }
                val day = (gate as? DayGateState.Ready)?.day ?: throw IllegalStateException("The day is not open")
                val saleId = saleRepo.createSale(
                    dayId = day.id,
                    method = current.method,
                    items = current.lines.map {
                        SaleCartItem(
                            productId = it.product.id,
                            productName = it.product.name,
                            qty = it.qty,
                            unitPrice = it.product.sellPrice,
                            unitCost = it.product.costPrice,
                        )
                    },
                    customerId = if (current.method == PayMethod.CREDIT) current.selectedCustomer?.customer?.id else null,
                    cashReceived = if (current.method == PayMethod.CASH) current.cashReceived else null,
                )
                _savedSale.value = SavedSale(saleId, current.method, current.total, current.change)
                clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 7.9: keep the cart and say so.
                edit { it.copy(saving = false) }
                _saveFailed.value = true
            }
        }
    }

    /** Addendum C2: Undo inside the snackbar voids without a PIN. */
    fun undoSale(saleId: Long) {
        viewModelScope.launch {
            try {
                saleRepo.voidSale(saleId, UNDO_REASON)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveFailed.value = true
            }
        }
    }

    fun savedSaleShown() {
        _savedSale.value = null
    }

    fun saveFailedShown() {
        _saveFailed.value = false
    }

    private fun edit(transform: (SellInputs) -> SellInputs) {
        inputs.update(transform)
        persist(inputs.value)
    }

    private fun persist(input: SellInputs) {
        savedStateHandle[KEY_CART_IDS] = input.cart.keys.toLongArray()
        savedStateHandle[KEY_CART_QTY] = input.cart.values.toDoubleArray()
        savedStateHandle[KEY_METHOD] = input.method.name
        savedStateHandle[KEY_CASH] = input.cashReceived
        savedStateHandle[KEY_CUSTOMER] = input.customerId
    }

    private fun restoreInputs(): SellInputs {
        val ids = savedStateHandle.get<LongArray>(KEY_CART_IDS) ?: longArrayOf()
        val qty = savedStateHandle.get<DoubleArray>(KEY_CART_QTY) ?: doubleArrayOf()
        return SellInputs(
            cart = ids.zip(qty.toList()).toMap(),
            method = savedStateHandle.get<String>(KEY_METHOD)?.let { PayMethod.valueOf(it) } ?: PayMethod.CASH,
            cashReceived = savedStateHandle[KEY_CASH],
            customerId = savedStateHandle[KEY_CUSTOMER],
        )
    }

    companion object {
        /** Spec 7.2: Undo voids the sale with this reason. */
        const val UNDO_REASON = "Undo"

        private const val KEY_CART_IDS = "cart_ids"
        private const val KEY_CART_QTY = "cart_qty"
        private const val KEY_METHOD = "method"
        private const val KEY_CASH = "cash_received"
        private const val KEY_CUSTOMER = "customer_id"
    }
}
