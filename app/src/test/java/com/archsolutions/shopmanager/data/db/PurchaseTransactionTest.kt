package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.db.entities.Unit
import com.archsolutions.shopmanager.data.repo.PurchaseLineItem
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
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
class PurchaseTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var purchaseRepo: PurchaseRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testClock = object : AppClock {
        override fun nowMillis(): Long = 1727680000000L
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        purchaseRepo = PurchaseRepo(db, testClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `saving purchase updates stock, cost price, and records movement`() = runTest {
        val productDao = db.productDao()
        val supplierDao = db.supplierDao()
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

        val supplierId = supplierDao.insert(
            SupplierEntity(
                name = "City Wholesale",
                contactPerson = "Robert",
                phone = "0772 123 456",
                whatsapp = null,
                location = "Kikuubo",
                notes = null,
            )
        )

        val prodId = productDao.insert(
            ProductEntity(
                name = "Rice 1kg",
                categoryId = null,
                unit = Unit.PCS,
                sellPrice = 4500L,
                costPrice = 3500L, // Old cost
                stockQty = 10.0,
                lowStockLevel = 5.0,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val purchaseId = purchaseRepo.savePurchase(
            dayId = dayId,
            supplierId = supplierId,
            paid = PurchasePaid.CASH,
            items = listOf(
                PurchaseLineItem(
                    productId = prodId,
                    qty = 20.0,
                    unitCost = 3800L, // New cost
                )
            ),
            note = "Weekly bulk buy",
        )

        // 1. Verify purchase
        val purchaseWithItems = purchaseRepo.getPurchaseWithItems(purchaseId).first()
        assertNotNull(purchaseWithItems)
        assertEquals(76000L, purchaseWithItems!!.purchase.total)
        assertEquals(1, purchaseWithItems.items.size)

        // 2. Verify stock increased from 10.0 to 30.0
        val updatedProduct = productDao.getByIdDirect(prodId)
        assertNotNull(updatedProduct)
        assertEquals(30.0, updatedProduct!!.stockQty, 0.001)

        // 3. Verify cost price updated to 3800L
        assertEquals(3800L, updatedProduct.costPrice)

        // 4. Verify movement recorded (+20.0, PURCHASE)
        val movements = stockMovementDao.getMovementsForProductDirect(prodId)
        assertEquals(1, movements.size)
        assertEquals(MovementType.PURCHASE, movements[0].type)
        assertEquals(20.0, movements[0].qtyChange, 0.001)
        assertEquals(purchaseId, movements[0].refId)
    }
}
