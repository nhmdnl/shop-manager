package com.archsolutions.shopmanager.data.db

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.StockMovementEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.db.entities.Unit
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Demo data from spec section 13 (all phone numbers are placeholders).
@Singleton
class SeedDataLoader @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
    private val settingsStore: SettingsStore,
    @ApplicationContext private val context: Context,
) {
    /** Seeds an empty database, in debug builds only (spec 13). True when it seeded just now (a fresh install). */
    suspend fun loadSeedDataIfEmpty(): Boolean {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return debuggable && seedIfEmpty()
    }

    internal suspend fun seedIfEmpty(): Boolean = db.withTransaction {
        val categoryDao = db.categoryDao()
        val productDao = db.productDao()
        val supplierDao = db.supplierDao()
        val customerDao = db.customerDao()
        val dayDao = db.dayDao()
        val stockMovementDao = db.stockMovementDao()

        // Check if data already exists
        if (categoryDao.getByName("Food") != null) return@withTransaction false

        val now = clock.nowMillis()
        val today = clock.todayDateString()

        // 1. Settings
        settingsStore.setShopName("Mary Shop")

        // 2. Categories
        val foodId = categoryDao.insert(CategoryEntity(name = "Food", sortOrder = 0))
        val drinksId = categoryDao.insert(CategoryEntity(name = "Drinks", sortOrder = 1))
        val dairyId = categoryDao.insert(CategoryEntity(name = "Dairy", sortOrder = 2))
        val householdId = categoryDao.insert(CategoryEntity(name = "Household", sortOrder = 3))

        // 3. Suppliers (WhatsApp on the same number as the phone)
        fun supplier(name: String, contactPerson: String, phone: String, location: String? = null) =
            SupplierEntity(
                name = name,
                contactPerson = contactPerson,
                phone = phone,
                whatsapp = phone,
                location = location,
                notes = null,
            )

        val cityWholesaleId = supplierDao.insert(supplier("City Wholesale", "Grace", "+256772000000"))
        val freshDairyId = supplierDao.insert(supplier("Fresh Dairy Co.", "Peter", "+256701000000", location = "Ntinda"))
        val bakeryExpressId = supplierDao.insert(supplier("Bakery Express", "Amina", "+256753000000"))
        val cleanHomeId = supplierDao.insert(supplier("Clean Home Supplies", "Joseph", "+256782000000"))

        // 4. Products definition
        data class ProductSeed(
            val name: String,
            val categoryId: Long,
            val unit: Unit,
            val sellPrice: Long,
            val costPrice: Long,
            val stockQty: Double,
            val lowStockLevel: Double,
            val supplierId: Long,
        )

        val productsToSeed = listOf(
            ProductSeed("Sugar 1kg", foodId, Unit.PCS, 5000, 4200, 34.0, 15.0, cityWholesaleId),
            ProductSeed("Rice 1kg", foodId, Unit.PCS, 4500, 3800, 50.0, 10.0, cityWholesaleId),
            ProductSeed("Cooking oil 1L", foodId, Unit.LITRE, 8000, 6900, 9.0, 10.0, cityWholesaleId),
            ProductSeed("Bread", foodId, Unit.PCS, 4000, 3200, 8.0, 10.0, bakeryExpressId),
            ProductSeed("Milk 500ml", dairyId, Unit.PCS, 1500, 1100, 3.0, 10.0, freshDairyId),
            ProductSeed("Yoghurt 250ml", dairyId, Unit.PCS, 2000, 1400, 2.0, 6.0, freshDairyId),
            ProductSeed("Butter 250g", dairyId, Unit.PCS, 6500, 5200, 9.0, 4.0, freshDairyId),
            ProductSeed("Eggs tray", foodId, Unit.PACK, 13000, 11000, 6.0, 8.0, cityWholesaleId),
            ProductSeed("Soap bar", householdId, Unit.PCS, 2500, 1900, 40.0, 12.0, cleanHomeId),
            ProductSeed("Salt 500g", foodId, Unit.PCS, 1000, 700, 25.0, 10.0, cityWholesaleId),
            ProductSeed("Soda 500ml", drinksId, Unit.PCS, 1500, 1100, 48.0, 12.0, cityWholesaleId),
            ProductSeed("Maize flour 1kg", foodId, Unit.PCS, 3500, 2800, 30.0, 10.0, cityWholesaleId),
            ProductSeed("Water 1.5L", drinksId, Unit.PCS, 2000, 1400, 36.0, 12.0, cityWholesaleId),
        )

        for (p in productsToSeed) {
            val productId = productDao.insert(
                ProductEntity(
                    name = p.name,
                    categoryId = p.categoryId,
                    unit = p.unit,
                    sellPrice = p.sellPrice,
                    costPrice = p.costPrice,
                    stockQty = p.stockQty,
                    lowStockLevel = p.lowStockLevel,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            // Preferred supplier link
            supplierDao.upsertProductSupplier(
                ProductSupplierEntity(
                    productId = productId,
                    supplierId = p.supplierId,
                    supplierCost = p.costPrice,
                    isPreferred = true,
                )
            )

            // Starting stock is one ADJUSTMENT movement (addendum C1)
            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    type = MovementType.ADJUSTMENT,
                    qtyChange = p.stockQty,
                    refId = null,
                    reason = ProductRepo.OPENING_STOCK_REASON,
                    createdAt = now,
                )
            )
        }

        // 5. Customers
        customerDao.insert(CustomerEntity(name = "Sarah N.", phone = null, note = null))
        customerDao.insert(CustomerEntity(name = "Joseph K.", phone = null, note = null))

        // 6. Business Day
        dayDao.insertDay(
            BusinessDayEntity(
                date = today,
                status = DayStatus.OPEN,
                openedAt = now,
                openingCash = 150_000L,
                openingMobile = 40_000L,
            )
        )
        true
    }
}
