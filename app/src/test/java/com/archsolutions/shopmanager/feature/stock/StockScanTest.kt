package com.archsolutions.shopmanager.feature.stock

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StockScanTest {
    private lateinit var db: AppDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) = StockViewModel(ProductRepo(db, FakeClock("2026-09-30")), handle)

    private suspend fun add(name: String, barcode: String?, active: Boolean = true): Long = db.productDao().insert(
        ProductEntity(name = name, categoryId = null, sellPrice = 1_000, costPrice = 500, barcode = barcode, active = active, createdAt = 0L, updatedAt = 0L),
    )

    @Test fun `a known code gives Found with the product id`() = runTest {
        val id = add("Soap", "6001")
        assertEquals(ScanLookup.Found(id), vm().onScanned("6001"))
    }

    @Test fun `an unknown code gives NotFound`() = runTest {
        add("Soap", "6001")
        assertEquals(ScanLookup.NotFound("9999"), vm().onScanned(" 9999 "))
    }

    @Test fun `an inactive product gives NotFound`() = runTest {
        add("Old soap", "6001", active = false)
        assertEquals(ScanLookup.NotFound("6001"), vm().onScanned("6001"))
    }

    @Test fun `the search query is unchanged by a scan`() = runTest {
        add("Soap", "6001")
        val vm = vm()
        vm.setQuery("sug")
        vm.onScanned("6001")
        vm.onScanned("9999")
        assertEquals("sug", vm.state.first { it.loaded }.query)
    }

    @Test fun `a handed-back code is looked up once and the key is cleared`() = runTest {
        val id = add("Soap", "6001")
        val handle = SavedStateHandle()
        val vm = vm(handle)
        handle[SCANNED_BARCODE_KEY] = "6001"
        assertEquals(ScanLookup.Found(id), vm.scan.first { it != null })
        assertNull(handle.get<String>(SCANNED_BARCODE_KEY))
        vm.clearScan()
        assertNull(vm.scan.value)
    }
}
