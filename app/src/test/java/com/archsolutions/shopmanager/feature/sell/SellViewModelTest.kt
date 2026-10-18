package com.archsolutions.shopmanager.feature.sell

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.feature.home.HomeViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// M4 "done when": a sale updates stock, day totals and Recent sales.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SellViewModelTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dayRepo: DayRepo
    private lateinit var saleRepo: SaleRepo
    private lateinit var customerRepo: CustomerRepo
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
        dayRepo = DayRepo(db, clock)
        saleRepo = SaleRepo(db, clock)
        customerRepo = CustomerRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insertProduct(name: String, price: Long, stock: Double): Long = db.productDao().insert(
        ProductEntity(
            name = name,
            categoryId = null,
            sellPrice = price,
            costPrice = price - 800,
            stockQty = stock,
            lowStockLevel = 5.0,
            createdAt = 0L,
            updatedAt = 0L,
        )
    )

    private fun TestScope.newDayGate(): DayGate {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("sell_${System.nanoTime()}.preferences_pb") },
            )
        )
        return DayGate(dayRepo, settingsStore, clock)
    }

    // The screen keeps the state flow subscribed; tests do the same.
    private fun TestScope.newViewModel(
        dayGate: DayGate = newDayGate(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SellViewModel {
        val viewModel = SellViewModel(ProductRepo(db, clock), customerRepo, saleRepo, dayGate, savedStateHandle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `cash sale updates stock, day totals and recent sales`() = runTest {
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 150_000L, openingMobile = 40_000L)
        val sugar = insertProduct("Sugar 1kg", price = 5_000L, stock = 34.0)
        val milk = insertProduct("Milk 500ml", price = 1_500L, stock = 3.0)
        val dayGate = newDayGate()
        val viewModel = newViewModel(dayGate)
        val home = HomeViewModel(dayGate, saleRepo, customerRepo, ProductRepo(db, clock), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.recentSales.collect {} }
        viewModel.state.first { it.loaded }

        viewModel.addOne(sugar)
        viewModel.addOne(sugar)
        viewModel.setQty(milk, 3.0)
        viewModel.setCashReceived(25_000L)
        val ready = viewModel.state.first { it.lines.size == 2 && it.cashReceived == 25_000L }
        assertEquals(14_500L, ready.total)
        assertEquals(10_500L, ready.change)

        viewModel.completeSale()
        val saved = viewModel.savedSale.first { it != null }!!

        assertEquals(PayMethod.CASH, saved.method)
        assertEquals(14_500L, saved.total)
        assertEquals(10_500L, saved.change)
        assertEquals(32.0, db.productDao().getByIdDirect(sugar)!!.stockQty, 0.0)
        assertEquals(0.0, db.productDao().getByIdDirect(milk)!!.stockQty, 0.0)
        val daySales = saleRepo.getSalesWithItemsForDay(dayId).first()
        assertEquals(1, daySales.size)
        assertEquals(25_000L, daySales.single().sale.cashReceived)
        assertEquals(listOf(saved.saleId), home.recentSales.first { it.isNotEmpty() }.map { it.saleId })
        // The cart is cleared for the next customer.
        assertTrue(viewModel.state.first { it.lines.isEmpty() }.lines.isEmpty())
    }

    @Test
    fun `credit sale needs a customer and raises what they owe`() = runTest {
        dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val bread = insertProduct("Bread", price = 4_000L, stock = 8.0)
        val joseph = customerRepo.saveCustomer("Joseph K.")
        db.moneyEntryDao().insertEntry(
            MoneyEntryEntity(
                dayId = dayRepo.getDayByDateDirect("2026-09-30")!!.id,
                createdAt = 0L,
                type = MoneyEntryType.CREDIT_REPAYMENT,
                method = MoneyMethod.CASH,
                amount = 1_000L,
                customerId = joseph,
            )
        )
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.addOne(bread)
        viewModel.setMethod(PayMethod.CREDIT)
        assertFalse(viewModel.state.first { it.method == PayMethod.CREDIT }.canComplete)

        viewModel.selectCustomer(joseph)
        val picked = viewModel.state.first { it.selectedCustomer != null }
        // Owed starts at -1,000 (a repayment with no credit sale): after the sale, 3,000.
        assertEquals(-1_000L, picked.selectedCustomer!!.owed)
        assertEquals(3_000L, picked.owedAfterSale)
        assertTrue(picked.canComplete)

        viewModel.completeSale()
        val saved = viewModel.savedSale.first { it != null }!!

        assertEquals(PayMethod.CREDIT, saved.method)
        assertEquals(3_000L, customerRepo.getCustomerBalanceOwedDirect(joseph))
        assertEquals(joseph, db.saleDao().getSaleByIdDirect(saved.saleId)!!.customerId)
    }

    @Test
    fun `quick-added customer is selected`() = runTest {
        dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.setMethod(PayMethod.CREDIT)
        viewModel.addCustomer("  Grace A. ", phone = "")

        val state = viewModel.state.first { it.selectedCustomer != null }
        assertEquals("Grace A.", state.selectedCustomer!!.customer.name)
        assertEquals(null, state.selectedCustomer!!.customer.phone)
    }

    @Test
    fun `undo voids the sale and puts the stock back`() = runTest {
        dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val soda = insertProduct("Soda 500ml", price = 1_500L, stock = 48.0)
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.setQty(soda, 12.0)
        viewModel.state.first { it.lines.isNotEmpty() }
        viewModel.completeSale()
        val saved = viewModel.savedSale.first { it != null }!!
        assertEquals(36.0, db.productDao().getByIdDirect(soda)!!.stockQty, 0.0)

        viewModel.undoSale(saved.saleId)

        val sale = saleRepo.getSaleWithItems(saved.saleId).first { it?.sale?.status == SaleStatus.VOIDED }!!.sale
        assertEquals(SellViewModel.UNDO_REASON, sale.voidReason)
        assertEquals(48.0, db.productDao().getByIdDirect(soda)!!.stockQty, 0.0)
    }

    @Test
    fun `the cart survives the process being killed`() = runTest {
        dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val rice = insertProduct("Rice 1kg", price = 4_500L, stock = 50.0)
        val handle = SavedStateHandle()
        val first = newViewModel(savedStateHandle = handle)
        first.state.first { it.loaded }
        first.addOne(rice)
        first.setMethod(PayMethod.MOBILE_MONEY)

        val restored = newViewModel(savedStateHandle = handle).state.first { it.lines.isNotEmpty() }

        assertEquals(rice, restored.lines.single().product.id)
        assertEquals(1.0, restored.lines.single().qty, 0.0)
        assertEquals(PayMethod.MOBILE_MONEY, restored.method)
    }
}
