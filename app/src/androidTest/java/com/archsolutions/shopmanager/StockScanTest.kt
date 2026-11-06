package com.archsolutions.shopmanager

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.feature.stock.StockScreen
import com.archsolutions.shopmanager.feature.stock.StockViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stock's scan icon against the screen's own handoff (the scanner itself is covered by ScanScreenTest): a code handed to the ViewModel
 * by the route (as the scanner handoff does) opens the product or shows Not found + Add this product.
 */
@RunWith(AndroidJUnit4::class)
class StockScanTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val queryExecutor = Executors.newFixedThreadPool(4)
    private val db = AppDatabase.configure(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java))
        .allowMainThreadQueries().setQueryExecutor(queryExecutor).build()
    private val clock = object : AppClock {
        override fun nowMillis(): Long = System.currentTimeMillis()
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @After fun tearDown() {
        queryExecutor.shutdown()
        queryExecutor.awaitTermination(5, TimeUnit.SECONDS)
        db.close()
    }

    private class Taps {
        var scans = 0
        var edited: Long? = null
        var added: String? = null
    }

    private fun show(taps: Taps, hasCamera: Boolean = true): Pair<Long, StockViewModel> {
        val id = runBlocking {
            db.productDao().insert(
                ProductEntity(name = "Sugar 1kg", categoryId = null, sellPrice = 5_000, costPrice = 4_200, barcode = "6001", createdAt = 0L, updatedAt = 0L),
            )
        }
        val vm = StockViewModel(ProductRepo(db, clock))
        rule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                StockScreen(
                    isExpanded = false,
                    gateState = DayGateState.Loading,
                    zoneId = clock.zoneId,
                    onOpenDay = {},
                    onCloseDay = {},
                    onNewProduct = {},
                    onEditProduct = { taps.edited = it },
                    onAddPurchase = {},
                    onScan = { taps.scans++ },
                    onAddScanned = { taps.added = it },
                    hasCamera = hasCamera,
                    viewModel = vm,
                )
            }
        }
        return id to vm
    }

    @Test
    fun scanIconOpensTheScanner() {
        val taps = Taps()
        show(taps)
        rule.onNodeWithText(context.getString(R.string.scan_action)).performClick()
        assertEquals(1, taps.scans)
    }

    @Test
    fun noScanIconWithoutACamera() {
        show(Taps(), hasCamera = false)
        assertEquals(0, rule.onAllNodesWithText(context.getString(R.string.scan_action)).fetchSemanticsNodes().size)
    }

    @Test
    fun knownCodeOpensThatProduct() {
        val taps = Taps()
        val (id, vm) = show(taps)
        rule.runOnIdle { vm.handleScanned("6001") }
        rule.waitUntil(5_000) { taps.edited != null }
        assertEquals(id, taps.edited)
    }

    @Test
    fun unknownCodeShowsNotFoundAndTheAddButton() {
        val taps = Taps()
        val (_, vm) = show(taps)
        rule.runOnIdle { vm.handleScanned("7770001") }
        val message = context.getString(R.string.scan_not_found, "7770001")
        rule.waitUntil(5_000) { rule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(message).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.scan_add_product)).performClick()
        assertEquals("7770001", taps.added)
        assertEquals(null, taps.edited)
    }
}
