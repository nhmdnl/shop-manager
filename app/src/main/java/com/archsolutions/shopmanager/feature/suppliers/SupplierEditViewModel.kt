package com.archsolutions.shopmanager.feature.suppliers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupplierForm(
    val name: String = "",
    val contact: String = "",
    val phone: String = "",
    val whatsappSame: Boolean = true,
    val whatsapp: String = "",
    val location: String = "",
    val notes: String = "",
    val productIds: List<Long> = emptyList(), // in the order they were linked
)

data class SupplierEditUiState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val form: SupplierForm = SupplierForm(),
    val products: List<ProductEntity> = emptyList(), // every active product, for "+ Link"
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val linked: List<ProductEntity> get() = form.productIds.mapNotNull { id -> products.firstOrNull { it.id == id } }
    val linkable: List<ProductEntity> get() = products.filter { it.id !in form.productIds }
    val canSave: Boolean get() = loaded && form.name.isNotBlank() && !saving
}

/** New / edit supplier (addendum A, SupplierEdit). */
@HiltViewModel
class SupplierEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val supplierRepo: SupplierRepo,
    productRepo: ProductRepo,
) : ViewModel() {
    private val supplierId = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val form = MutableStateFlow(SupplierForm())
    private val status = MutableStateFlow(Status(loaded = supplierId == null))
    private var existing: SupplierEntity? = null

    private data class Status(val loaded: Boolean, val saving: Boolean = false, val saved: Boolean = false, val failed: Boolean = false)

    init {
        if (supplierId != null) {
            viewModelScope.launch {
                val supplier = supplierRepo.getByIdDirect(supplierId) ?: return@launch
                existing = supplier
                val same = supplier.whatsapp == null || supplier.whatsapp == supplier.phone
                form.value = SupplierForm(
                    name = supplier.name,
                    contact = supplier.contactPerson.orEmpty(),
                    phone = supplier.phone.orEmpty(),
                    whatsappSame = same,
                    whatsapp = if (same) "" else supplier.whatsapp.orEmpty(),
                    location = supplier.location.orEmpty(),
                    notes = supplier.notes.orEmpty(),
                    productIds = supplierRepo.getSupplierProductsDirect(supplierId).map { it.productId },
                )
                status.update { it.copy(loaded = true) }
            }
        }
    }

    val state: StateFlow<SupplierEditUiState> = combine(form, productRepo.getAllActive(), status) { input, products, s ->
        SupplierEditUiState(
            isNew = supplierId == null,
            loaded = s.loaded,
            form = input,
            products = products,
            saving = s.saving,
            saved = s.saved,
            saveFailed = s.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplierEditUiState(isNew = supplierId == null))

    fun edit(change: (SupplierForm) -> SupplierForm) = form.update(change)

    fun link(productId: Long) = form.update { if (productId in it.productIds) it else it.copy(productIds = it.productIds + productId) }

    fun unlink(productId: Long) = form.update { it.copy(productIds = it.productIds - productId) }

    fun saveFailedShown() = status.update { it.copy(failed = false) }

    fun save() {
        if (!state.value.canSave) return
        status.update { it.copy(saving = true) }
        viewModelScope.launch {
            val input = form.value
            val phone = input.phone.trim().ifEmpty { null }
            val base = existing ?: SupplierEntity(name = "", contactPerson = null, phone = null, whatsapp = null, location = null, notes = null)
            val supplier = base.copy(
                name = input.name.trim(),
                contactPerson = input.contact.trim().ifEmpty { null },
                phone = phone,
                whatsapp = if (input.whatsappSame) phone else input.whatsapp.trim().ifEmpty { null },
                location = input.location.trim().ifEmpty { null },
                notes = input.notes.trim().ifEmpty { null },
            )
            runCatching { supplierRepo.saveSupplier(supplier, input.productIds.toSet()) }
                .onSuccess { status.update { it.copy(saving = false, saved = true) } }
                .onFailure { status.update { it.copy(saving = false, failed = true) } }
        }
    }
}
