package com.archsolutions.shopmanager.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Edits must keep every synced row's uid; the outbox then holds only UPSERTs (no DELETE + new uid). */
@RunWith(RobolectricTestRunner::class)
class UidStabilityTest {

    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepo
    private lateinit var days: DayRepo
    private lateinit var sales: SaleRepo
    private lateinit var purchases: PurchaseRepo
    private lateinit var money: MoneyRepo
    private lateinit var customers: CustomerRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()

    private data class Out(val tbl: String, val uid: String, val op: String)

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        products = ProductRepo(db, clock)
        days = DayRepo(db, clock)
        sales = SaleRepo(db, clock)
        purchases = PurchaseRepo(db, clock)
        money = MoneyRepo(db, clock)
        customers = CustomerRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newProduct(name: String, categoryId: Long? = null) = ProductEntity(
        name = name, categoryId = categoryId, sellPrice = 8_000, costPrice = 6_000,
        lowStockLevel = 2.0, createdAt = 0, updatedAt = 0,
    )

    private suspend fun startSync() {
        val sync = db.syncDao()
        sync.setState(true, "f")
        sync.clearOutbox()
    }

    private fun outbox(): List<Out> =
        db.openHelper.writableDatabase.query("SELECT tbl, uid, op FROM sync_outbox ORDER BY seq").use { c ->
            buildList { while (c.moveToNext()) add(Out(c.getString(0), c.getString(1), c.getString(2))) }
        }

    private fun uidOf(table: String, id: Long): String =
        db.openHelper.writableDatabase.query("SELECT uid FROM $table WHERE id = $id").use { it.moveToFirst(); it.getString(0) }

    /** Only UPSERTs, and at least one of them for [table]/[uid] (an empty outbox must not pass). */
    private fun assertOnlyUpserts(table: String, uid: String) {
        val out = outbox()
        assertTrue("outbox: $out", out.none { it.op != "UPSERT" })
        assertTrue("no UPSERT for $table/$uid in $out", Out(table, uid, "UPSERT") in out)
    }

    @Test
    fun `editing a product keeps its uid`() = runTest {
        val cat = products.saveCategory("Food")
        val other = products.saveCategory("Drinks")
        val p = products.createProduct(newProduct("Sugar", cat), 5.0)
        startSync()
        val before = uidOf("product", p)

        products.updateProduct(
            ProductEntity(
                id = p, name = "Sugar 2kg", categoryId = other, sellPrice = 9_000, costPrice = 7_000,
                lowStockLevel = 3.0, createdAt = 0, updatedAt = 0,
            )
        )

        assertEquals(before, uidOf("product", p))
        assertEquals(listOf(Out("product", before, "UPSERT")), outbox())
    }

    @Test
    fun `adjusting stock keeps uids`() = runTest {
        val p = products.createProduct(newProduct("Sugar"), 5.0)
        startSync()
        val before = uidOf("product", p)
        products.adjustStock(p, 3.0, "Recount")
        assertEquals(before, uidOf("product", p))
        assertOnlyUpserts("product", before)
    }

    @Test
    fun `voiding a sale keeps uids`() = runTest {
        val p = products.createProduct(newProduct("Sugar"), 5.0)
        val day = days.openDay("2026-09-30", 0, 0)
        val sale = sales.createSale(day, PayMethod.CASH, listOf(SaleCartItem(p, "Sugar", 1.0, 8_000, 6_000)))
        startSync()
        val saleUid = uidOf("sale", sale)
        val productUid = uidOf("product", p)
        sales.voidSale(sale, "mistake")
        assertEquals(saleUid, uidOf("sale", sale))
        assertEquals(productUid, uidOf("product", p))
        assertOnlyUpserts("sale", saleUid)
    }

    @Test
    fun `closing then reopening a day keeps its uid`() = runTest {
        val day = days.openDay("2026-09-30", 0, 0)
        startSync()
        val before = uidOf("business_day", day)
        days.closeDay(day, 0, 0, 0, 0, 0, 0)
        days.reopenDay(day)
        assertEquals(before, uidOf("business_day", day))
        assertOnlyUpserts("business_day", before)
    }

    @Test
    fun `a purchase that changes a product's cost keeps its uid`() = runTest {
        val p = products.createProduct(newProduct("Sugar"), 5.0)
        val day = days.openDay("2026-09-30", 0, 0)
        startSync()
        val before = uidOf("product", p)
        purchases.savePurchase(day, null, PurchasePaid.CASH, listOf(PurchaseLineItem(p, 2.0, 6_500)))
        assertEquals(before, uidOf("product", p))
        assertOnlyUpserts("product", before)
    }

    @Test
    fun `a credit repayment keeps the customer and day uids`() = runTest {
        val c = customers.saveCustomer("Sarah N.")
        val day = days.openDay("2026-09-30", 0, 0)
        startSync()
        val customerUid = uidOf("customer", c)
        val dayUid = uidOf("business_day", day)
        money.addCreditRepayment(day, c, 1_000, MoneyMethod.CASH)
        assertEquals(customerUid, uidOf("customer", c))
        assertEquals(dayUid, uidOf("business_day", day))
        val entryUid = db.openHelper.writableDatabase.query("SELECT uid FROM money_entry ORDER BY id DESC LIMIT 1").use { it.moveToFirst(); it.getString(0) }
        assertOnlyUpserts("money_entry", entryUid)
    }

    @Test
    fun `saving an existing customer or category name writes nothing`() = runTest {
        customers.saveCustomer("Sarah N.")
        products.saveCategory("Food")
        startSync()
        customers.saveCustomer("Sarah N.")
        products.saveCategory("Food")
        assertEquals(emptyList<Out>(), outbox())
    }

    @Test
    fun `a product made from a copy of another gets its own uid`() = runTest {
        val a = products.createProduct(newProduct("Sugar"), 0.0)
        val existing = products.getByIdDirect(a)!!
        val b = products.createProduct(existing.copy(name = "Copy"), 0.0)
        assertNotEquals(a, b)
        assertNotEquals(uidOf("product", a), uidOf("product", b))
    }
}
