package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.PurchaseLineItem
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SaleCartItem
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

// M5 "done when": a purchase raises stock and cost, and movements always sum to stockQty.
@RunWith(RobolectricTestRunner::class)
class StockRulesTest {

    private lateinit var db: AppDatabase
    private lateinit var productRepo: ProductRepo
    private lateinit var purchaseRepo: PurchaseRepo
    private lateinit var saleRepo: SaleRepo
    private lateinit var dayRepo: DayRepo
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        productRepo = ProductRepo(db, clock)
        purchaseRepo = PurchaseRepo(db, clock)
        saleRepo = SaleRepo(db, clock)
        dayRepo = DayRepo(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun product(name: String, cost: Long) = ProductEntity(
        name = name,
        categoryId = null,
        sellPrice = cost + 400,
        costPrice = cost,
        lowStockLevel = 10.0,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private suspend fun assertStockMatchesMovements(productId: Long) {
        val product = db.productDao().getByIdDirect(productId)!!
        assertEquals(product.name, product.stockQty, db.stockMovementDao().getSumQuantityChanges(productId), 1e-9)
    }

    @Test
    fun `new product records its starting stock as one opening adjustment`() = runTest {
        val milk = productRepo.createProduct(product("Milk 500ml", 1_100L), startingStock = 12.0)
        val salt = productRepo.createProduct(product("Salt 500g", 700L), startingStock = 0.0)

        val movements = db.stockMovementDao().getMovementsForProductDirect(milk)
        assertEquals(1, movements.size)
        assertEquals(MovementType.ADJUSTMENT, movements.single().type)
        assertEquals(ProductRepo.OPENING_STOCK_REASON, movements.single().reason)
        assertEquals(12.0, db.productDao().getByIdDirect(milk)!!.stockQty, 0.0)
        assertTrue(db.stockMovementDao().getMovementsForProductDirect(salt).isEmpty())
    }

    @Test
    fun `editing a product never changes its stock`() = runTest {
        val id = productRepo.createProduct(product("Bread", 3_200L), startingStock = 8.0)

        productRepo.updateProduct(product("Bread loaf", 3_300L).copy(id = id, stockQty = 999.0))

        val saved = db.productDao().getByIdDirect(id)!!
        assertEquals("Bread loaf", saved.name)
        assertEquals(3_300L, saved.costPrice)
        assertEquals(8.0, saved.stockQty, 0.0)
        assertStockMatchesMovements(id)
    }

    @Test
    fun `purchase raises stock and cost and keeps the preferred supplier`() = runTest {
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val dairy = db.supplierDao().insert(
            SupplierEntity(name = "Fresh Dairy Co.", contactPerson = null, phone = null, whatsapp = null, location = null, notes = null)
        )
        val milk = productRepo.createProduct(
            product("Milk 500ml", 1_100L),
            startingStock = 3.0,
            suppliers = listOf(ProductSupplierEntity(productId = 0, supplierId = dairy, supplierCost = 1_100L, isPreferred = true)),
        )

        val purchaseId = purchaseRepo.savePurchase(
            dayId = dayId,
            supplierId = dairy,
            paid = PurchasePaid.NOT_PAID,
            items = listOf(PurchaseLineItem(milk, qty = 24.0, unitCost = 1_150L)),
        )

        val saved = db.productDao().getByIdDirect(milk)!!
        assertEquals(27.0, saved.stockQty, 0.0)
        assertEquals(1_150L, saved.costPrice)
        val link = db.supplierDao().getProductSupplier(milk, dairy)!!
        assertEquals(1_150L, link.supplierCost)
        assertTrue("purchasing must not drop the preferred flag", link.isPreferred)
        // "Not paid yet" raises stock without touching cash: it is stored as NOT_PAID.
        val purchase = db.purchaseDao().getPurchaseWithItems(purchaseId).first()!!.purchase
        assertEquals(PurchasePaid.NOT_PAID, purchase.paid)
        assertEquals(27_600L, purchase.total)
        assertStockMatchesMovements(milk)
    }

    @Test
    fun `random sales, voids, purchases and corrections keep movements equal to stock`() = runTest {
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val random = Random(42)
        val products = (1..5).map { n ->
            productRepo.createProduct(product("P$n", 1_000L * n), startingStock = random.nextInt(0, 30).toDouble())
        }
        val sales = mutableListOf<Long>()

        repeat(200) {
            val productId = products.random(random)
            val current = db.productDao().getByIdDirect(productId)!!
            when (random.nextInt(4)) {
                0 -> sales += saleRepo.createSale(
                    dayId = dayId,
                    method = PayMethod.CASH,
                    items = listOf(SaleCartItem(productId, current.name, random.nextInt(1, 6).toDouble(), current.sellPrice, current.costPrice)),
                )
                1 -> if (sales.isNotEmpty()) saleRepo.voidSale(sales.random(random), "Wrong item")
                2 -> purchaseRepo.savePurchase(
                    dayId = dayId,
                    supplierId = null,
                    paid = PurchasePaid.CASH,
                    items = listOf(PurchaseLineItem(productId, random.nextInt(1, 12).toDouble(), current.costPrice)),
                )
                else -> productRepo.adjustStock(productId, random.nextInt(0, 40) + random.nextInt(0, 2) * 0.5, "Count correction")
            }
            products.forEach { assertStockMatchesMovements(it) }
        }
    }
}
