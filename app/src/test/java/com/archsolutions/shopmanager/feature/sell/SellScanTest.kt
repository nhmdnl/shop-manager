package com.archsolutions.shopmanager.feature.sell

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import java.time.ZoneId
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffSession
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

/** The Sell scanner's wiring: a known code adds one unit through SellViewModel.addOne, anything else leaves the cart alone. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SellScanTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private val clock = FakeClock("2026-09-30")
    private val scanClock = object : AppClock { // the debounce window runs on its own clock
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

    private suspend fun insert(name: String, barcode: String, active: Boolean = true) = db.productDao().insert(
        ProductEntity(
            name = name, categoryId = null, sellPrice = 1_000, costPrice = 500, stockQty = 10.0, barcode = barcode,
            active = active, createdAt = 0L, updatedAt = 0L,
        ),
    )

    private class Rig(val sell: SellViewModel, val scan: ScanViewModel) {
        /** Same call the Sell scan destination makes. */
        fun scan(code: String) = scan.onCode(ScannedCode(code)) { sell.addOne(it.id) }
    }

    private fun TestScope.rig(): Rig {
        val dayRepo = DayRepo(db, clock)
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("s_${System.nanoTime()}.preferences_pb") }),
        )
        val products = ProductRepo(db, clock)
        val sell = SellViewModel(products, CustomerRepo(db), SaleRepo(db, clock), DayGate(dayRepo, settings, clock), SavedStateHandle(), StaffSession(clock))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sell.state.collect {} }
        return Rig(sell, ScanViewModel({ products.findByBarcode(it) }, scanClock, SavedStateHandle()))
    }

    @Test fun `a known code adds one unit through SellViewModel addOne (cart qty 1)`() = runTest {
        val soap = insert("Soap", "6001")
        val rig = rig()
        rig.scan("6001")
        val line = rig.sell.state.first { it.lines.isNotEmpty() }.lines.single()
        assertEquals(soap, line.product.id)
        assertEquals(1.0, line.qty, 0.0)
        assertEquals(1, rig.scan.state.value.count)
    }

    @Test fun `scanning the same product twice outside the debounce window gives qty 2`() = runTest {
        insert("Soap", "6001")
        val rig = rig()
        rig.scan("6001")
        scanClock.now += 2_000
        rig.scan("6001")
        assertEquals(2.0, rig.sell.state.first { it.lines.isNotEmpty() }.lines.single().qty, 0.0)
    }

    @Test fun `an unknown code leaves the cart unchanged`() = runTest {
        insert("Soap", "6001")
        val rig = rig()
        rig.scan("9999")
        assertTrue(rig.sell.state.first { it.loaded }.lines.isEmpty())
        assertEquals("9999", rig.scan.state.value.notFound)
    }

    @Test fun `an inactive product leaves the cart unchanged`() = runTest {
        insert("Old soap", "6001", active = false)
        val rig = rig()
        rig.scan("6001")
        assertTrue(rig.sell.state.first { it.loaded }.lines.isEmpty())
        assertEquals("6001", rig.scan.state.value.notFound)
    }
}
