package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SupplierRepoTest {

    private lateinit var db: AppDatabase
    private lateinit var supplierRepo: SupplierRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        supplierRepo = SupplierRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun product(name: String, stock: Double, low: Double, cost: Long): Long = db.productDao().insert(
        ProductEntity(name = name, categoryId = null, sellPrice = cost + 500, costPrice = cost, stockQty = stock, lowStockLevel = low, createdAt = 0L, updatedAt = 0L)
    )

    private fun supplier(name: String, id: Long = 0L) =
        SupplierEntity(id = id, name = name, contactPerson = "Peter", phone = "0701000000", whatsapp = "0701000000", location = null, notes = null)

    @Test
    fun `editing a supplier keeps the links that stay and swaps the rest`() = runTest {
        val milk = product("Milk 500ml", stock = 3.0, low = 10.0, cost = 1_100L)
        val butter = product("Butter 250g", stock = 9.0, low = 4.0, cost = 5_200L)
        val cheese = product("Cheese 200g", stock = 7.0, low = 4.0, cost = 9_000L)
        val id = supplierRepo.saveSupplier(supplier("Fresh Dairy Co."), setOf(milk, butter))
        supplierRepo.linkProductToSupplier(milk, id, supplierCost = 1_000L, isPreferred = true)

        supplierRepo.saveSupplier(supplier("Fresh Dairy Company", id = id), setOf(milk, cheese))

        assertEquals("Fresh Dairy Company", supplierRepo.getByIdDirect(id)?.name)
        val links = db.supplierDao().getSupplierProductsDirect(id).associateBy { it.productId }
        assertEquals(setOf(milk, cheese), links.keys)
        assertEquals(1_000L, links.getValue(milk).supplierCost) // kept, with its preferred flag
        assertEquals(true, links.getValue(milk).isPreferred)
        assertNull(links.getValue(cheese).supplierCost)
    }

    @Test
    fun `rows give the supplier's cost when known, else the product's`() = runTest {
        val milk = product("Milk 500ml", stock = 3.0, low = 10.0, cost = 1_100L)
        val bread = product("Bread", stock = 8.0, low = 10.0, cost = 3_200L)
        val id = supplierRepo.saveSupplier(supplier("Fresh Dairy Co."), setOf(milk, bread))
        supplierRepo.linkProductToSupplier(milk, id, supplierCost = 1_000L)

        val rows = supplierRepo.getSupplierProductRows().first()

        assertEquals(listOf("Bread" to 3_200L, "Milk 500ml" to 1_000L), rows.map { it.name to it.cost })
    }
}
