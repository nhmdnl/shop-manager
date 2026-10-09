package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.Unit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductDaoTest {

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
    fun `insert and retrieve products and categories`() = runTest {
        val categoryDao = db.categoryDao()
        val productDao = db.productDao()

        val foodCatId = categoryDao.insert(CategoryEntity(name = "Food", sortOrder = 0))
        val drinksCatId = categoryDao.insert(CategoryEntity(name = "Drinks", sortOrder = 1))

        val sugarId = productDao.insert(
            ProductEntity(
                name = "Sugar 1kg",
                categoryId = foodCatId,
                unit = Unit.PCS,
                sellPrice = 5000L,
                costPrice = 4200L,
                stockQty = 24.0,
                lowStockLevel = 5.0,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val waterId = productDao.insert(
            ProductEntity(
                name = "Water 1.5L",
                categoryId = drinksCatId,
                unit = Unit.PCS,
                sellPrice = 1500L,
                costPrice = 1000L,
                stockQty = 2.0, // Low stock (<= 6.0)
                lowStockLevel = 6.0,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // Check active products
        val activeProducts = productDao.getAllActive().first()
        assertEquals(2, activeProducts.size)

        // Check low stock products
        val lowStock = productDao.getLowStock().first()
        assertEquals(1, lowStock.size)
        assertEquals(waterId, lowStock[0].id)
        assertEquals("Water 1.5L", lowStock[0].name)

        // Check total stock value: Sugar (24 * 4200 = 100800) + Water (2 * 1000 = 2000) = 102800
        val totalStockValue = productDao.getTotalStockValue().first()
        assertEquals(102800L, totalStockValue)
    }
}
