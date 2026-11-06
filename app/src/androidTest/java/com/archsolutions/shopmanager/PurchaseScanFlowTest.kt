package com.archsolutions.shopmanager

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.CameraPermissionState
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.feature.scan.ScanHost
import com.archsolutions.shopmanager.feature.scan.ScanViewModel
import com.archsolutions.shopmanager.feature.stock.AddPurchaseScreen
import com.archsolutions.shopmanager.feature.stock.PurchaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Purchase scanning against a fake camera (NO real camera runs on the VM): Scan opens the scanner, scanned items
 * become purchase lines, Done goes back to the form with them, an unknown code offers Add this product. Navigation
 * itself is wired in AppNavHost; this drives the same AddPurchaseScreen (-> PurchaseForm, shared with the tablet
 * Stock pane) + ScanHost pair.
 */
@RunWith(AndroidJUnit4::class)
class PurchaseScanFlowTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val queryExecutor = Executors.newFixedThreadPool(4)
    private val db = AppDatabase.configure(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java))
        .allowMainThreadQueries().setQueryExecutor(queryExecutor).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = object : AppClock {
        override fun nowMillis(): Long = System.currentTimeMillis()
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private class FakeSource : ScanSource {
        override val unavailable: StateFlow<Boolean> = MutableStateFlow(false)
        override fun retry() {}
        override val codes = MutableSharedFlow<ScannedCode>(extraBufferCapacity = 4)
        override val hasTorch: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setTorch(on: Boolean) {}

        @Composable
        override fun Preview(modifier: Modifier) {
            Box(modifier.testTag("preview"))
        }
    }

    private object QuietFeedback : ScanFeedback {
        override fun success() {}
        override fun failure() {}
    }

    private object Granted : CameraPermission {
        override val state = CameraPermissionState.Granted
        override fun request() {}
        override fun openSettings() {}
    }

    @After fun tearDown() {
        scope.cancel()
        queryExecutor.shutdown()
        queryExecutor.awaitTermination(5, TimeUnit.SECONDS)
        db.close()
    }

    private fun shop(): Pair<PurchaseViewModel, BusinessDayEntity> = runBlocking {
        val dayRepo = DayRepo(db, clock)
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        db.productDao().insert(
            ProductEntity(name = "Sugar 1kg", categoryId = null, sellPrice = 5_000, costPrice = 4_200, stockQty = 34.0, lowStockLevel = 15.0, barcode = "6001", createdAt = 0L, updatedAt = 0L),
        )
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.cacheDir, "purchscan_${System.nanoTime()}.preferences_pb") }))
        val vm = PurchaseViewModel(ProductRepo(db, clock), SupplierRepo(db), PurchaseRepo(db, clock), DayGate(dayRepo, settings, clock), SavedStateHandle())
        vm to dayRepo.getDayByIdDirect(dayId)!!
    }

    private fun show(
        gate: (BusinessDayEntity) -> DayGateState,
        purchase: PurchaseViewModel,
        day: BusinessDayEntity,
        source: FakeSource,
        hasCamera: Boolean = true,
        onAdd: (String) -> Unit = {},
    ) {
        val products = ProductRepo(db, clock)
        val scanVm = ScanViewModel({ products.findByBarcode(it) }, clock, SavedStateHandle())
        rule.setContent {
            var scanning by remember { mutableStateOf(false) }
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                if (scanning) {
                    ScanHost(
                        continuous = true,
                        onKnown = { purchase.addOrIncrement(it.id) },
                        onAddProduct = onAdd,
                        onDone = { scanning = false },
                        viewModel = scanVm,
                        source = source,
                        feedback = QuietFeedback,
                        permission = Granted,
                    )
                } else {
                    AddPurchaseScreen(
                        gateState = gate(day),
                        zoneId = clock.zoneId,
                        onOpenDay = {},
                        onCloseDay = {},
                        onBack = {},
                        onDone = {},
                        onScan = { scanning = true },
                        hasCamera = hasCamera,
                        viewModel = purchase,
                    )
                }
            }
        }
    }

    @Test
    fun purchaseScanAddsLinesAndDoneReturnsToTheFormWithThem() {
        val (purchase, day) = shop()
        val source = FakeSource()
        show({ DayGateState.Ready(it) }, purchase, day, source)

        rule.onNodeWithText(context.getString(R.string.scan_action)).performClick()
        rule.onNodeWithTag("preview").assertIsDisplayed()
        rule.runOnIdle { source.codes.tryEmit(ScannedCode("6001")) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("1 item scanned", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(context.getString(R.string.scan_done)).performClick()

        rule.onNodeWithText("Sugar 1kg").assertIsDisplayed()
        rule.waitUntil(5_000) { purchase.state.value.lines.size == 1 }
        assertEquals(1.0, purchase.state.value.lines.single().qty, 0.0)
    }

    @Test
    fun scanButtonIsNotOfferedWhenTheDayIsNotOpen() {
        val (purchase, day) = shop()
        show({ DayGateState.DayNotOpen }, purchase, day, FakeSource())
        rule.onNodeWithText(context.getString(R.string.scan_action)).assertDoesNotExist()
    }

    @Test
    fun scanButtonIsNotOfferedWithoutACamera() {
        val (purchase, day) = shop()
        show({ DayGateState.Ready(it) }, purchase, day, FakeSource(), hasCamera = false)
        rule.onNodeWithText(context.getString(R.string.scan_action)).assertDoesNotExist()
    }

    @Test
    fun unknownCodeThenAddThisProductHandsOverTheCode() {
        val (purchase, day) = shop()
        val source = FakeSource()
        val added = mutableListOf<String>()
        show({ DayGateState.Ready(it) }, purchase, day, source) { added += it }

        rule.onNodeWithText(context.getString(R.string.scan_action)).performClick()
        rule.runOnIdle { source.codes.tryEmit(ScannedCode("9999")) }
        rule.onNodeWithText(context.getString(R.string.scan_not_found, "9999")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.scan_add_product)).performClick()

        assertEquals(listOf("9999"), added)
        assertEquals(0, purchase.state.value.lines.size)
    }
}
