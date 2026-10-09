package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.db.entities.Unit
import com.archsolutions.shopmanager.data.repo.SaleCartItem
import com.archsolutions.shopmanager.data.repo.SaleRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SaleTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var saleRepo: SaleRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testClock = object : AppClock {
        override fun nowMillis(): Long = 1727680000000L
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        saleRepo = SaleRepo(db, testClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `completing sale decreases stock and creates movements within transaction`() = runTest {
        val productDao = db.productDao()
        val dayDao = db.dayDao()
        val stockMovementDao = db.stockMovementDao()

        val dayId = dayDao.insertDay(
            BusinessDayEntity(
                date = "2026-09-30",
                status = DayStatus.OPEN,
                openedAt = 1000L,
                openingCash = 100000L,
                openingMobile = 20000L,
            )
        )

        val prodId = productDao.insert(
            ProductEntity(
                name = "Sugar 1kg",
                categoryId = null,
                unit = Unit.PCS,
                sellPrice = 5000L,
                costPrice = 4200L,
                stockQty = 10.0,
                lowStockLevel = 2.0,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val saleId = saleRepo.createSale(
            dayId = dayId,
            method = PayMethod.CASH,
            items = listOf(
                SaleCartItem(
                    productId = prodId,
                    productName = "Sugar 1kg",
                    qty = 3.0,
                    unitPrice = 5000L,
                    unitCost = 4200L,
                )
            ),
            cashReceived = 20000L,
        )

        // 1. Verify sale record
        val saleWithItems = saleRepo.getSaleWithItems(saleId).first()
        assertNotNull(saleWithItems)
        assertEquals(15000L, saleWithItems!!.sale.total)
        assertEquals(12600L, saleWithItems.sale.costTotal)
        assertEquals(SaleStatus.DONE, saleWithItems.sale.status)
        assertEquals(1, saleWithItems.items.size)

        // 2. Verify product stock decreased from 10.0 to 7.0
        val updatedProduct = productDao.getByIdDirect(prodId)
        assertNotNull(updatedProduct)
        assertEquals(7.0, updatedProduct!!.stockQty, 0.001)

        // 3. Verify stock movement recorded
        val movements = stockMovementDao.getMovementsForProductDirect(prodId)
        assertEquals(1, movements.size)
        assertEquals(MovementType.SALE, movements[0].type)
        assertEquals(-3.0, movements[0].qtyChange, 0.001)
        assertEquals(saleId, movements[0].refId)
    }

    @Test
    fun `voiding sale restores stock and writes VOID_RETURN movement`() = runTest {
        val productDao = db.productDao()
        val dayDao = db.dayDao()
        val stockMovementDao = db.stockMovementDao()

        val dayId = dayDao.insertDay(
            BusinessDayEntity(
                date = "2026-09-30",
                status = DayStatus.OPEN,
                openedAt = 1000L,
                openingCash = 100000L,
                openingMobile = 20000L,
            )
        )

        val prodId = productDao.insert(
            ProductEntity(
                name = "Milk 500ml",
                categoryId = null,
                unit = Unit.PCS,
                sellPrice = 2000L,
                costPrice = 1600L,
                stockQty = 5.0,
                lowStockLevel = 2.0,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val saleId = saleRepo.createSale(
            dayId = dayId,
            method = PayMethod.CASH,
            items = listOf(
                SaleCartItem(
                    productId = prodId,
                    productName = "Milk 500ml",
                    qty = 2.0,
                    unitPrice = 2000L,
                    unitCost = 1600L,
                )
            ),
        )

        assertEquals(3.0, productDao.getByIdDirect(prodId)!!.stockQty, 0.001)

        // Void the sale
        saleRepo.voidSale(saleId, reason = "Customer changed mind")

        // 1. Verify sale status is VOIDED
        val voidedSale = db.saleDao().getSaleByIdDirect(saleId)
        assertNotNull(voidedSale)
        assertEquals(SaleStatus.VOIDED, voidedSale!!.status)
        assertEquals("Customer changed mind", voidedSale.voidReason)

        // 2. Verify stock restored to original 5.0
        val restoredProduct = productDao.getByIdDirect(prodId)
        assertNotNull(restoredProduct)
        assertEquals(5.0, restoredProduct!!.stockQty, 0.001)

        // 3. Verify stock movement recorded for VOID_RETURN (+2.0)
        val movements = stockMovementDao.getMovementsForProductDirect(prodId)
        assertEquals(2, movements.size) // SALE (-2) and VOID_RETURN (+2)
        assertEquals(MovementType.VOID_RETURN, movements[0].type)
        assertEquals(2.0, movements[0].qtyChange, 0.001)
    }
}
