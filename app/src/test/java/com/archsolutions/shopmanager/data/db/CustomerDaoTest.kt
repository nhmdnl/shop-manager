package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomerDaoTest {

    private lateinit var db: AppDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `customer balance owed is sum of credit sales minus credit repayments`() = runTest {
        val customerDao = db.customerDao()
        val saleDao = db.saleDao()
        val moneyEntryDao = db.moneyEntryDao()
        val dayDao = db.dayDao()

        val dayId = dayDao.insertDay(
            BusinessDayEntity(
                date = "2026-09-30",
                status = DayStatus.OPEN,
                openedAt = 1000L,
                openingCash = 100000L,
                openingMobile = 20000L,
            )
        )

        val customerId = customerDao.insert(
            CustomerEntity(
                name = "Sarah N.",
                phone = "0772 111 222",
                note = "Weekly payer",
            )
        )

        // Initial balance should be 0
        assertEquals(0L, customerDao.getCustomerBalanceOwed(customerId).first())

        // 1. Credit sale of 25,000 UGX
        saleDao.insertSale(
            SaleEntity(
                dayId = dayId,
                createdAt = 1000L,
                total = 25000L,
                costTotal = 20000L,
                method = PayMethod.CREDIT,
                customerId = customerId,
                status = SaleStatus.DONE,
            )
        )

        // Balance should be 25,000
        assertEquals(25000L, customerDao.getCustomerBalanceOwed(customerId).first())

        // 2. Second credit sale of 10,000 UGX
        saleDao.insertSale(
            SaleEntity(
                dayId = dayId,
                createdAt = 2000L,
                total = 10000L,
                costTotal = 8000L,
                method = PayMethod.CREDIT,
                customerId = customerId,
                status = SaleStatus.DONE,
            )
        )

        // Balance should be 35,000
        assertEquals(35000L, customerDao.getCustomerBalanceOwed(customerId).first())

        // 3. Customer repays 15,000 UGX in Cash
        moneyEntryDao.insertEntry(
            MoneyEntryEntity(
                dayId = dayId,
                createdAt = 3000L,
                type = MoneyEntryType.CREDIT_REPAYMENT,
                method = MoneyMethod.CASH,
                amount = 15000L,
                customerId = customerId,
            )
        )

        // Balance should now be 20,000
        assertEquals(20000L, customerDao.getCustomerBalanceOwed(customerId).first())
        assertEquals(20000L, customerDao.getTotalOwedToShop().first())
    }
}
