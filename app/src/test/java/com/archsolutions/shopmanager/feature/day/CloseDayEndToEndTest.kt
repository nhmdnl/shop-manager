package com.archsolutions.shopmanager.feature.day

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.PurchaseLineItem
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SaleCartItem
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.BalanceStatus
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// M6 "done when": worked example 6.2 passes end to end.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CloseDayEndToEndTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun product(name: String, price: Long): ProductEntity {
        val id = db.productDao().insert(
            ProductEntity(name = name, categoryId = null, sellPrice = price, costPrice = price / 2, stockQty = 10.0, createdAt = 0L, updatedAt = 0L)
        )
        return db.productDao().getByIdDirect(id)!!
    }

    @Test
    fun `worked example 6_2 from opening to a closed day`() = runTest {
        val dayRepo = DayRepo(db, clock)
        val saleRepo = SaleRepo(db, clock)
        val moneyRepo = MoneyRepo(db, clock)
        val purchaseRepo = PurchaseRepo(db, clock)
        val customerRepo = CustomerRepo(db)
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 150_000L, openingMobile = 40_000L)
        val joseph = customerRepo.saveCustomer("Joseph K.")

        suspend fun sell(price: Long, method: PayMethod, customer: Long? = null) {
            val item = product("Item $price", price)
            saleRepo.createSale(dayId, method, listOf(SaleCartItem(item.id, item.name, 1.0, item.sellPrice, item.costPrice)), customerId = customer)
        }
        sell(100_000L, PayMethod.CASH)
        sell(26_800L, PayMethod.CASH)
        sell(59_700L, PayMethod.MOBILE_MONEY)
        sell(12_000L, PayMethod.CREDIT, customer = joseph)
        moneyRepo.addCreditRepayment(dayId, joseph, 20_000L, MoneyMethod.CASH)
        moneyRepo.addExpense(dayId, 18_500L, MoneyMethod.CASH, category = "Transport")
        moneyRepo.addExpense(dayId, 5_000L, MoneyMethod.MOBILE_MONEY, category = "Airtime")
        val stockItem = product("Sugar 1kg", 4_200L)
        purchaseRepo.savePurchase(dayId, null, PurchasePaid.CASH, listOf(PurchaseLineItem(stockItem.id, 1.0, 82_400L)))

        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("close.preferences_pb") })
        )
        val viewModel = CloseDayViewModel(
            SavedStateHandle(mapOf("date" to "2026-09-30")),
            dayRepo, saleRepo, moneyRepo, purchaseRepo, customerRepo, settingsStore,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val check = viewModel.state.first { it.balance != null }
        assertEquals(195_900L, check.balance!!.cash.expected)
        assertEquals(94_700L, check.balance!!.mobile.expected)
        assertEquals(198_500L, check.balance!!.totalSales)
        assertEquals(1, check.step)

        viewModel.setCountedCash(195_000L)
        viewModel.setCountedMobile(94_700L)
        val counted = viewModel.state.first { it.countedCash != null && it.countedMobile != null }
        assertEquals(-900L, counted.cashDifference)
        assertEquals(BalanceStatus.SHORT, counted.cashStatus)
        assertEquals(BalanceStatus.BALANCED, counted.mobileStatus)
        // Short needs a reason before the day can close.
        assertTrue(counted.needsReason)
        assertFalse(counted.canClose)

        viewModel.setReason("Change error")
        assertTrue(viewModel.state.first { it.reason != null }.canClose)
        viewModel.closeDay()
        viewModel.state.first { it.closed }

        val closed = dayRepo.getDayByIdDirect(dayId)!!
        assertEquals(DayStatus.CLOSED, closed.status)
        assertEquals(195_900L, closed.expectedCash)
        assertEquals(94_700L, closed.expectedMobile)
        assertEquals(195_000L, closed.countedCash)
        assertEquals(-900L, closed.diffCash)
        assertEquals(0L, closed.diffMobile)
        assertEquals("Change error", closed.diffReason)
        // Credit sales add to what Joseph owes, never to cash: 12,000 sold − 20,000 repaid.
        assertEquals(-8_000L, customerRepo.getCustomerBalanceOwedDirect(joseph))
    }
}
