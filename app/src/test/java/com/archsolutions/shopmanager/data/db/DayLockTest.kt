package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.DayNotOpenException
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.PurchaseLineItem
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SaleCartItem
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.testutil.FakeClock
import com.archsolutions.shopmanager.testutil.testDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Locking rules: writes need an OPEN day, and a CLOSED day's records are read-only (spec 6.3, 6.7).
@RunWith(RobolectricTestRunner::class)
class DayLockTest {

    private lateinit var db: AppDatabase
    private lateinit var saleRepo: SaleRepo
    private lateinit var purchaseRepo: PurchaseRepo
    private lateinit var moneyRepo: MoneyRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        val clock = FakeClock()
        saleRepo = SaleRepo(db, clock)
        purchaseRepo = PurchaseRepo(db, clock)
        moneyRepo = MoneyRepo(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertSugar(): Long = db.productDao().insert(
        ProductEntity(
            name = "Sugar 1kg",
            categoryId = null,
            sellPrice = 5000L,
            costPrice = 4200L,
            stockQty = 24.0,
            createdAt = 0L,
            updatedAt = 0L,
        )
    )

    @Test
    fun `sales purchases and money entries are refused on a closed day`() = runTest {
        val closedDayId = db.dayDao().insertDay(testDay("2026-09-29", DayStatus.CLOSED))
        val productId = insertSugar()
        val cart = listOf(SaleCartItem(productId, "Sugar 1kg", qty = 1.0, unitPrice = 5000L, unitCost = 4200L))

        val saleError = runCatching { saleRepo.createSale(closedDayId, PayMethod.CASH, cart) }.exceptionOrNull()
        val purchaseError = runCatching {
            purchaseRepo.savePurchase(closedDayId, null, PurchasePaid.CASH, listOf(PurchaseLineItem(productId, 6.0, 4200L)))
        }.exceptionOrNull()
        val expenseError = runCatching {
            moneyRepo.addExpense(closedDayId, 5_000L, MoneyMethod.CASH, category = "Lunch")
        }.exceptionOrNull()

        assertTrue(saleError is DayNotOpenException)
        assertTrue(purchaseError is DayNotOpenException)
        assertTrue(expenseError is DayNotOpenException)
        assertTrue(db.saleDao().getSalesForDay(closedDayId).first().isEmpty())
        assertTrue(db.purchaseDao().getPurchasesForDay(closedDayId).first().isEmpty())
        assertTrue(moneyRepo.getEntriesForDayDirect(closedDayId).isEmpty())
        assertEquals(24.0, db.productDao().getByIdDirect(productId)!!.stockQty, 0.0)
    }

    @Test
    fun `money entries are refused for a day that was never opened`() = runTest {
        val error = runCatching { moneyRepo.addCashIn(dayId = 999L, amount = 5_000L, method = MoneyMethod.CASH) }.exceptionOrNull()

        assertTrue(error is DayNotOpenException)
        assertTrue(moneyRepo.getEntriesForDayDirect(999L).isEmpty())
    }

    @Test
    fun `a sale from a closed day cannot be voided`() = runTest {
        val dayDao = db.dayDao()
        val dayId = dayDao.insertDay(testDay("2026-09-30", DayStatus.OPEN))
        val productId = insertSugar()
        val saleId = saleRepo.createSale(
            dayId,
            PayMethod.CASH,
            listOf(SaleCartItem(productId, "Sugar 1kg", qty = 2.0, unitPrice = 5000L, unitCost = 4200L)),
        )
        dayDao.updateDay(dayDao.getDayByIdDirect(dayId)!!.copy(status = DayStatus.CLOSED))

        val error = runCatching { saleRepo.voidSale(saleId, "Wrong item") }.exceptionOrNull()

        assertTrue(error is DayNotOpenException)
        assertEquals(SaleStatus.DONE, db.saleDao().getSaleByIdDirect(saleId)!!.status)
        assertEquals(22.0, db.productDao().getByIdDirect(productId)!!.stockQty, 0.0)
    }
}
