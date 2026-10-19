package com.archsolutions.shopmanager

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.feature.sell.CheckoutScreen
import com.archsolutions.shopmanager.feature.sell.SellScreen
import com.archsolutions.shopmanager.feature.sell.SellViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId

/** Checklist 15: a phone sale takes 3 taps, a tablet sale 2 (cash, nothing typed). */
@RunWith(AndroidJUnit4::class)
class SaleTapCountTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = AppDatabase.createInMemory(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = object : AppClock {
        override fun nowMillis(): Long = System.currentTimeMillis()
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun openShop(): Pair<SellViewModel, BusinessDayEntity> = runBlocking {
        val dayRepo = DayRepo(db, clock)
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 150_000L, openingMobile = 40_000L)
        db.productDao().insert(
            ProductEntity(name = "Sugar 1kg", categoryId = null, sellPrice = 5_000, costPrice = 4_200, stockQty = 34.0, lowStockLevel = 15.0, createdAt = 0L, updatedAt = 0L)
        )
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.cacheDir, "taps_${System.nanoTime()}.preferences_pb") }))
        val viewModel = SellViewModel(ProductRepo(db, clock), CustomerRepo(db), SaleRepo(db, clock), DayGate(dayRepo, settings, clock), SavedStateHandle())
        viewModel to dayRepo.getDayByIdDirect(dayId)!!
    }

    private fun waitForOneSale(dayId: Long) =
        composeRule.waitUntil(timeoutMillis = 5_000) { runBlocking { db.saleDao().getSalesForDayDirect(dayId).size == 1 } }

    @Test
    fun tabletSaleTakesTwoTaps() {
        val (viewModel, day) = openShop()
        composeRule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                SellScreen(
                    isExpanded = true,
                    gateState = DayGateState.Ready(day),
                    zoneId = clock.zoneId,
                    viewModel = viewModel,
                    onOpenDay = {},
                    onCloseDay = {},
                    onCheckout = {},
                )
            }
        }

        composeRule.onNodeWithText("Sugar 1kg").performClick() // 1: add to the sale
        composeRule.onNodeWithText(context.getString(R.string.action_complete_sale)).performClick() // 2: cash, complete

        waitForOneSale(day.id)
    }

    @Test
    fun phoneSaleTakesThreeTaps() {
        val (viewModel, day) = openShop()
        composeRule.setContent {
            var checkout by remember { mutableStateOf(false) }
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                if (!checkout) {
                    SellScreen(
                        isExpanded = false,
                        gateState = DayGateState.Ready(day),
                        zoneId = clock.zoneId,
                        viewModel = viewModel,
                        onOpenDay = {},
                        onCloseDay = {},
                        onCheckout = { checkout = true },
                    )
                } else {
                    CheckoutScreen(viewModel = viewModel, onBack = { checkout = false }, onDone = { checkout = false })
                }
            }
        }

        composeRule.onNodeWithText("Sugar 1kg").performClick() // 1: add to the sale
        composeRule.onNodeWithText(context.getString(R.string.action_charge)).performClick() // 2: open checkout
        composeRule.onNodeWithText(context.getString(R.string.action_complete_sale)).performClick() // 3: cash, complete

        waitForOneSale(day.id)
    }
}
