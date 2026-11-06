package com.archsolutions.shopmanager.feature.stock

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditProductViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.vm(id: Long, barcode: String?): EditProductViewModel {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("e_${System.nanoTime()}.preferences_pb") }),
        )
        val handle = SavedStateHandle(buildMap { put("id", id); barcode?.let { put("barcode", it) } })
        val vm = EditProductViewModel(
            handle, ProductRepo(db, clock), SupplierRepo(db), settings, StaffSession(clock),
            StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test fun `a new product opened with a barcode starts with it filled in`() = runTest {
        val state = vm(0L, "6001234567890").state.first { it.loaded }
        assertEquals("6001234567890", state.form.barcode)
        assertEquals(true, state.isNew)
    }

    @Test fun `a new product without a barcode starts empty`() = runTest {
        assertEquals("", vm(0L, null).state.first { it.loaded }.form.barcode)
    }

    @Test fun `an existing product ignores the route barcode`() = runTest {
        val id = db.productDao().insert(
            ProductEntity(name = "Soap", categoryId = null, sellPrice = 1_000, costPrice = 500, barcode = "6001", createdAt = 0L, updatedAt = 0L),
        )
        val state = vm(id, "9999").state.first { it.loaded }
        assertEquals("6001", state.form.barcode)
    }

    private fun product(name: String, barcode: String?) = ProductEntity(
        name = name, categoryId = null, sellPrice = 1_000, costPrice = 500, barcode = barcode, createdAt = 0L, updatedAt = 0L,
    )

    @Test fun `a scanned code fills the barcode field trimmed`() = runTest {
        val vm = vm(0L, null)
        vm.setScannedBarcode("  6001234567890 \n")
        assertEquals("6001234567890", vm.state.first { it.loaded }.form.barcode)
    }

    @Test fun `a code handed back through saved state fills the field once`() = runTest {
        val vm = vm(0L, null)
        vm.handle[SCANNED_BARCODE_KEY] = " 777 "
        assertEquals("777", vm.state.first { it.loaded }.form.barcode)
        assertEquals(null, vm.handle.get<String>(SCANNED_BARCODE_KEY))
    }

    @Test fun `duplicateOf names the other active product using the barcode`() = runTest {
        db.productDao().insert(product("Soap", "6001"))
        val vm = vm(0L, null)
        vm.edit { it.copy(barcode = "6001") }
        assertEquals("Soap", vm.state.first { it.duplicateOf != null }.duplicateOf)
    }

    @Test fun `duplicateOf is null for the product's own barcode`() = runTest {
        val id = db.productDao().insert(product("Soap", "6001"))
        val state = vm(id, null).state.first { it.loaded }
        assertEquals("6001", state.form.barcode)
        assertEquals(null, state.duplicateOf)
    }

    @Test fun `saving with a duplicate barcode still saves`() = runTest {
        db.productDao().insert(product("Soap", "6001"))
        val vm = vm(0L, "6001")
        vm.edit { it.copy(name = "Other soap", sellPrice = 2_000) }
        assertEquals("Soap", vm.state.first { it.duplicateOf != null }.duplicateOf)
        vm.save()
        assertEquals(true, vm.state.first { it.saved }.saved)
        assertEquals(2, db.productDao().getAllActive().first().count { it.barcode == "6001" })
    }
}
