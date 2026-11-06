package com.archsolutions.shopmanager.feature.stock

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.feature.scan.ScanViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

/** The purchase scanner's wiring: a known code adds one unit through PurchaseViewModel.addOrIncrement. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PurchaseScanTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private val clock = FakeClock("2026-09-30")
    private val scanClock = object : AppClock {
        var now = 10_000L
        override fun nowMillis() = now
        override fun todayDateString() = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insert(name: String, barcode: String, cost: Long = 500, active: Boolean = true) = db.productDao().insert(
        ProductEntity(
            name = name, categoryId = null, sellPrice = 1_000, costPrice = cost, stockQty = 10.0, barcode = barcode,
            active = active, createdAt = 0L, updatedAt = 0L,
        ),
    )

    private class Rig(val purchase: PurchaseViewModel, val scan: ScanViewModel) {
        /** Same call the Scan purchase destination makes. */
        fun scan(code: String) = scan.onCode(ScannedCode(code)) { purchase.addOrIncrement(it.id) }
    }

    private fun TestScope.rig(): Rig {
        val dayRepo = DayRepo(db, clock)
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("p_${System.nanoTime()}.preferences_pb") }),
        )
        val products = ProductRepo(db, clock)
        val purchase = PurchaseViewModel(products, SupplierRepo(db), PurchaseRepo(db, clock), DayGate(dayRepo, settings, clock), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { purchase.state.collect {} }
        return Rig(purchase, ScanViewModel({ products.findByBarcode(it) }, scanClock, SavedStateHandle()))
    }

    @Test fun `a known code adds a purchase line with quantity 1 at the product cost`() = runTest {
        val soap = insert("Soap", "6001", cost = 700)
        val rig = rig()
        rig.scan("6001")
        val line = rig.purchase.state.first { it.lines.isNotEmpty() }.lines.single()
        assertEquals(soap, line.product.id)
        assertEquals(1.0, line.qty, 0.0)
        assertEquals(700L, line.unitCost)
        assertEquals(1, rig.scan.state.value.count)
    }

    @Test fun `the same product again increments the quantity`() = runTest {
        insert("Soap", "6001")
        val rig = rig()
        rig.scan("6001")
        scanClock.now += 2_000
        rig.scan("6001")
        val lines = rig.purchase.state.first { it.lines.isNotEmpty() }.lines
        assertEquals(1, lines.size)
        assertEquals(2.0, lines.single().qty, 0.0)
    }

    @Test fun `a second product gets its own line`() = runTest {
        insert("Soap", "6001")
        insert("Sugar", "6002")
        val rig = rig()
        rig.scan("6001")
        scanClock.now += 2_000
        rig.scan("6002")
        assertEquals(2, rig.purchase.state.first { it.lines.size == 2 }.lines.size)
    }

    @Test fun `an unknown code adds nothing`() = runTest {
        insert("Soap", "6001")
        val rig = rig()
        rig.scan("9999")
        assertTrue(rig.purchase.state.first { it.loaded }.lines.isEmpty())
        assertEquals("9999", rig.scan.state.value.notFound)
    }

    @Test fun `an archived product adds nothing`() = runTest {
        insert("Old soap", "6001", active = false)
        val rig = rig()
        rig.scan("6001")
        assertTrue(rig.purchase.state.first { it.loaded }.lines.isEmpty())
    }

    @Test fun `addOrIncrement for a missing product adds nothing`() = runTest {
        val rig = rig()
        rig.purchase.addOrIncrement(12345L)
        assertTrue(rig.purchase.state.first { it.loaded }.lines.isEmpty())
    }
}
