package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedDataLoaderTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

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
    fun `seed matches spec 13 and runs only once`() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("seed.preferences_pb") },
            )
        )
        val loader = SeedDataLoader(db, FakeClock("2026-09-30"), settingsStore, context)

        loader.seedIfEmpty()
        loader.seedIfEmpty()

        val products = db.productDao().getAllActive().first()
        assertEquals(13, products.size)
        assertEquals(4, db.supplierDao().getAllActive().first().size)
        assertEquals(2, db.customerDao().getAll().first().size)
        assertEquals("Mary Shop", settingsStore.settings.first().shopName)

        // Cooking oil, Bread, Milk, Yoghurt and Eggs start below their alert level.
        assertEquals(
            setOf("Cooking oil 1L", "Bread", "Milk 500ml", "Yoghurt 250ml", "Eggs tray"),
            products.filter { it.stockQty < it.lowStockLevel }.map { it.name }.toSet(),
        )
        // Stock invariant: movements sum to the cached quantity.
        products.forEach { product ->
            assertEquals(product.name, product.stockQty, db.stockMovementDao().getSumQuantityChanges(product.id), 0.0)
        }

        val day = db.dayDao().getDayByDateDirect("2026-09-30")!!
        assertEquals(DayStatus.OPEN, day.status)
        assertEquals(150_000L, day.openingCash)
        assertEquals(40_000L, day.openingMobile)
    }
}
