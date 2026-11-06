package com.archsolutions.shopmanager.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductBarcodeLookupTest {

    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        products = ProductRepo(db, FakeClock())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(name: String, barcode: String?, active: Boolean = true): Long =
        db.productDao().insert(
            ProductEntity(
                name = name, categoryId = null, sellPrice = 1000, costPrice = 500,
                barcode = barcode, active = active, createdAt = 1, updatedAt = 1,
            )
        )

    @Test
    fun `finds an active product by exact code`() = runTest {
        val id = add("Soda", "5449000000996")
        assertEquals(id, products.findByBarcode("5449000000996")?.id)
        assertNull(products.findByBarcode("1111111111111"))
    }

    @Test
    fun `finds it by its UPC spelling when stored as EAN-13 and the reverse`() = runTest {
        val ean = add("Gum", "0123456789012")
        val upc = add("Mints", "987654321098")
        assertEquals(ean, products.findByBarcode("123456789012")?.id)
        assertEquals(upc, products.findByBarcode("0987654321098")?.id)
    }

    @Test
    fun `inactive product is not found`() = runTest {
        add("Old", "5449000000996", active = false)
        assertNull(products.findByBarcode("5449000000996"))
    }

    @Test
    fun `duplicates return the lowest id`() = runTest {
        val first = add("A", "5449000000996")
        add("B", "5449000000996")
        assertEquals(first, products.findByBarcode("5449000000996")?.id)
    }

    @Test
    fun `productUsingBarcode ignores the product itself`() = runTest {
        val a = add("A", "5449000000996")
        assertNull(products.productUsingBarcode("5449000000996", exceptId = a))
        val b = add("B", "5449000000996")
        assertEquals(b, products.productUsingBarcode("5449000000996", exceptId = a)?.id)
    }

    @Test
    fun `blank code returns null`() = runTest {
        add("Blank", "")
        assertNull(products.findByBarcode("  "))
        assertNull(products.productUsingBarcode("", exceptId = 0))
    }
}
