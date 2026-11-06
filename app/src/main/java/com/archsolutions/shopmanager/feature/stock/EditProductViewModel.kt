package com.archsolutions.shopmanager.feature.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.domain.staff.Permission
import com.archsolutions.shopmanager.feature.pin.pinNeededFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Key on the opening NavBackStackEntry's savedStateHandle under which the single-shot scanner hands a code back (read by ScannedCodeHandoff in app/ScanRoutes.kt, never by a ViewModel: a Hilt ViewModel has its own SavedStateHandle). */
const val SCANNED_BARCODE_KEY = "scanned_barcode"

data class SupplierLinkDraft(val supplierId: Long, val name: String, val cost: Long?, val preferred: Boolean)

/** The editable fields; stock itself is read live and only changes through movements. */
data class ProductForm(
    val name: String = "",
    val categoryId: Long? = null,
    val barcode: String = "",
    val unit: ProductUnit = ProductUnit.PCS,
    val sellPrice: Long? = null,
    val costPrice: Long? = null,
    val lowStockLevel: Double = 5.0,
    val startingStock: Double = 0.0,
    val links: List<SupplierLinkDraft> = emptyList(),
)

data class EditProductUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val form: ProductForm = ProductForm(),
    val stockQty: Double = 0.0,
    val lowStock: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    /** The stock correction will ask for a PIN: the real gate decision. */
    val pinNeeded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Name of another active product using the typed or scanned barcode; a warning only, saving is not blocked. */
    val duplicateOf: String? = null,
) {
    val canSave: Boolean get() = form.name.isNotBlank() && (form.sellPrice ?: 0L) > 0L && !saving

    /** Suppliers not linked yet, offered by "+ Add". */
    val unlinkedSuppliers: List<SupplierEntity>
        get() = suppliers.filter { supplier -> form.links.none { it.supplierId == supplier.id } }
}

/** Edit product and New product (7.3, addendum NewProduct), plus Correct stock (addendum CorrectStock). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EditProductViewModel @Inject constructor(
    internal val handle: SavedStateHandle,
    private val productRepo: ProductRepo,
    private val supplierRepo: SupplierRepo,
    settingsStore: SettingsStore,
    staffSession: StaffSession,
    staffRepo: StaffRepo,
) : ViewModel() {
    // EditProductRoute(id) uses 0 for a new product.
    private val productId: Long = handle.get<Long>("id") ?: 0L
    // A scanned code from "Add this product" fills a NEW product only; an existing one ignores it.
    private val routeBarcode: String = handle.get<String>("barcode").orEmpty().trim()
    private val form = MutableStateFlow<ProductForm?>(
        if (productId == 0L) ProductForm(barcode = routeBarcode) else null
    )
    private val flags = MutableStateFlow(Pair(false, false)) // saving, saved

    private val liveProduct = if (productId == 0L) flowOf(null) else productRepo.getById(productId)

    // Checked on every barcode change; a failing lookup shows no warning (the save path does not depend on it).
    private val duplicateOf: Flow<String?> = form.map { it?.barcode.orEmpty() }.distinctUntilChanged().mapLatest { code ->
        try {
            productRepo.productUsingBarcode(code, productId)?.name
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    val state: StateFlow<EditProductUiState> = combine(
        combine(form, flags, duplicateOf, ::Triple),
        liveProduct,
        productRepo.getCategories(),
        supplierRepo.getAllActive(),
        pinNeededFlow(settingsStore.settings, staffSession.current, staffRepo.staffActive, Permission.CORRECT_STOCK),
    ) { (currentForm, flag, duplicate), product, categories, suppliers, pinNeeded ->
        EditProductUiState(
            loaded = currentForm != null,
            isNew = productId == 0L,
            form = currentForm ?: ProductForm(),
            stockQty = product?.stockQty ?: 0.0,
            lowStock = product != null && product.stockQty <= product.lowStockLevel,
            categories = categories,
            suppliers = suppliers,
            pinNeeded = pinNeeded,
            saving = flag.first,
            saved = flag.second,
            duplicateOf = duplicate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditProductUiState())

    init {
        if (productId != 0L) {
            viewModelScope.launch {
                val product = productRepo.getByIdDirect(productId) ?: return@launch
                val links = supplierRepo.getSupplierLinks(productId).first()
                form.value = ProductForm(
                    name = product.name,
                    categoryId = product.categoryId,
                    barcode = product.barcode.orEmpty(),
                    unit = product.unit,
                    sellPrice = product.sellPrice,
                    costPrice = product.costPrice,
                    lowStockLevel = product.lowStockLevel,
                    links = links.map { SupplierLinkDraft(it.supplierId, it.name, it.supplierCost, it.isPreferred) },
                )
            }
        }
    }

    /** A code handed back by the scanner (see [ScannedCodeHandoff]): waits for an existing product's form, then fills the field. */
    fun onScannedBarcode(code: String) {
        viewModelScope.launch {
            form.first { it != null }
            setScannedBarcode(code)
        }
    }

    /** A code from the scanner: trimmed into the barcode field. */
    fun setScannedBarcode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        edit { it.copy(barcode = trimmed) }
    }

    fun edit(transform: (ProductForm) -> ProductForm) = form.update { it?.let(transform) }

    /** "+ Add" / "Link a supplier": the first linked supplier becomes the preferred one. */
    fun linkSupplier(supplier: SupplierEntity) = edit { current ->
        if (current.links.any { it.supplierId == supplier.id }) return@edit current
        val draft = SupplierLinkDraft(supplier.id, supplier.name, current.costPrice, preferred = current.links.isEmpty())
        current.copy(links = current.links + draft)
    }

    fun makePreferred(supplierId: Long) = edit { current ->
        current.copy(links = current.links.map { it.copy(preferred = it.supplierId == supplierId) })
    }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        val currentForm = current.form
        flags.value = true to false
        viewModelScope.launch {
            try {
                val product = ProductEntity(
                    id = productId,
                    name = currentForm.name.trim(),
                    categoryId = currentForm.categoryId,
                    unit = currentForm.unit,
                    sellPrice = currentForm.sellPrice ?: 0L,
                    costPrice = currentForm.costPrice ?: 0L,
                    lowStockLevel = currentForm.lowStockLevel,
                    barcode = currentForm.barcode.trim().ifEmpty { null },
                    createdAt = 0L,
                    updatedAt = 0L,
                )
                val links = currentForm.links.map {
                    ProductSupplierEntity(
                        productId = productId,
                        supplierId = it.supplierId,
                        supplierCost = it.cost ?: currentForm.costPrice,
                        isPreferred = it.preferred,
                    )
                }
                if (productId == 0L) {
                    productRepo.createProduct(product, currentForm.startingStock, links)
                } else {
                    productRepo.updateProduct(product, links)
                }
                flags.value = false to true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flags.value = false to false
            }
        }
    }

    /** Correct stock: the counted amount becomes the stock, saved as one ADJUSTMENT movement. */
    fun correctStock(counted: Double, reason: String, note: String) {
        if (productId == 0L) return
        viewModelScope.launch {
            productRepo.adjustStock(productId, counted, if (note.isBlank()) reason else "$reason — ${note.trim()}")
        }
    }
}
