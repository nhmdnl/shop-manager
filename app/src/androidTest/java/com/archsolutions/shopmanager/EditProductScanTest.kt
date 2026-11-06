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
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.CameraPermissionState
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.feature.pin.PinGate
import com.archsolutions.shopmanager.feature.scan.ScanOnceHost
import com.archsolutions.shopmanager.feature.stock.EditProductScreen
import com.archsolutions.shopmanager.feature.stock.EditProductViewModel
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
 * Edit product's barcode scan against a fake camera (NO real camera on the VM): the Scan icon opens the single-shot
 * scanner, the first code fills the field and the scanner closes, and a code another product uses shows a warning.
 * Navigation itself is wired in AppNavHost; this drives the same EditProductScreen + ScanOnceHost pair.
 */
@RunWith(AndroidJUnit4::class)
class EditProductScanTest {
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

    private fun viewModel(): EditProductViewModel = runBlocking {
        db.productDao().insert(
            ProductEntity(name = "Sugar 1kg", categoryId = null, sellPrice = 5_000, costPrice = 4_200, barcode = "6001", createdAt = 0L, updatedAt = 0L),
        )
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.cacheDir, "editscan_${System.nanoTime()}.preferences_pb") }))
        EditProductViewModel(
            SavedStateHandle(mapOf("id" to 0L)), ProductRepo(db, clock), SupplierRepo(db), settings, StaffSession(clock),
            StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000),
        )
    }

    private fun show(vm: EditProductViewModel, source: FakeSource) {
        val gate = PinGate(MutableStateFlow(null), scope, { _, action -> action() })
        rule.setContent {
            var scanning by remember { mutableStateOf(false) }
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                if (scanning) {
                    ScanOnceHost(
                        onCode = { code -> vm.setScannedBarcode(code); scanning = false },
                        onDone = { scanning = false },
                        source = source,
                        feedback = QuietFeedback,
                        permission = Granted,
                    )
                } else {
                    EditProductScreen(isExpanded = false, onBack = {}, onScan = { scanning = true }, hasCamera = true, pinGate = gate, viewModel = vm)
                }
            }
        }
    }

    @Test
    fun scanIconFillsTheField() {
        val vm = viewModel()
        val source = FakeSource()
        show(vm, source)

        rule.onNodeWithText(context.getString(R.string.barcode_scan)).performClick()
        rule.onNodeWithTag("preview").assertIsDisplayed()
        // Two frames of the same code arrive; only the first counts, and the scanner closes.
        rule.runOnIdle {
            source.codes.tryEmit(ScannedCode(" 7770001 "))
            source.codes.tryEmit(ScannedCode("8880002"))
        }
        rule.onNodeWithText(context.getString(R.string.barcode_scan)).assertIsDisplayed()
        rule.waitUntil(5_000) { vm.state.value.form.barcode == "7770001" }
        assertEquals("7770001", vm.state.value.form.barcode)
        rule.onNodeWithText("7770001").assertIsDisplayed()
    }

    @Test
    fun duplicateWarningShowsTheOtherProductsName() {
        val vm = viewModel()
        val source = FakeSource()
        show(vm, source)

        rule.onNodeWithText(context.getString(R.string.barcode_scan)).performClick()
        rule.runOnIdle { source.codes.tryEmit(ScannedCode("6001")) }
        val warning = context.getString(R.string.barcode_in_use, "Sugar 1kg")
        rule.waitUntil(5_000) { rule.onAllNodesWithText(warning).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(warning).assertIsDisplayed()
    }
}
