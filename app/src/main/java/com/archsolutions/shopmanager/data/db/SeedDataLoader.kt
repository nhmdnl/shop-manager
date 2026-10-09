package com.archsolutions.shopmanager.data.db

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
import com.archsolutions.shopmanager.data.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedDataLoader @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
    private val settingsStore: SettingsStore,
) {
    suspend fun loadSeedDataIfEmpty() = db.withTransaction {
        val categoryDao = db.categoryDao()
        val productDao = db.productDao()
        val supplierDao = db.supplierDao()
        val customerDao = db.customerDao()
        val dayDao = db.dayDao()
        val stockMovementDao = db.stockMovementDao()

        // Check if data already exists
        if (categoryDao.getByName("Food") != null) return@withTransaction

        val now = clock.nowMillis()
        val today = clock.todayDateString()

        // 1. Settings
        settingsStore.setShopName("Mary Shop")

        // 2. Categories
        val foodId = categoryDao.insert(CategoryEntity(name = "Food", sortOrder = 0))
        val drinksId = categoryDao.insert(CategoryEntity(name = "Drinks", sortOrder = 1))
        val dairyId = categoryDao.insert(CategoryEntity(name = "Dairy", sortOrder = 2))
        val householdId = categoryDao.insert(CategoryEntity(name = "Household", sortOrder = 3))

        // 3. Suppliers
        val cityWholesaleId = supplierDao.insert(
            SupplierEntity(
                name = "City Wholesale",
                contactPerson = "Robert",
                phone = "0772 123 456",
                whatsapp = "0772 123 456",
                location = "Kikuubo, Kampala",
                notes = "General dry goods and provisions",
            )
        )
        val freshDairyId = supplierDao.insert(
            SupplierEntity(
                name = "Fresh Dairy Co.",
                contactPerson = "Grace",
                phone = "0701 987 654",
                whatsapp = "0701 987 654",
                location = "Industrial Area, Kampala",
                notes = "Daily morning delivery at 07:00",
            )
        )
        val bakeryExpressId = supplierDao.insert(
            SupplierEntity(
                name = "Bakery Express",
                contactPerson = "David",
                phone = "0782 555 123",
                whatsapp = "0782 555 123",
                location = "Ntinda, Kampala",
                notes = "Bread and pastry deliveries",
            )
        )
        val cleanHomeId = supplierDao.insert(
            SupplierEntity(
                name = "Clean Home Supplies",
                contactPerson = "Sarah",
                phone = "0750 444 888",
                whatsapp = "0750 444 888",
                location = "Nakasero, Kampala",
                notes = "Cleaning and household detergents",
            )
        )

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
            ProductSeed("Sugar 1kg", foodId, Unit.PCS, 5000, 4200, 24.0, 5.0, cityWholesaleId),
            ProductSeed("Rice 1kg", foodId, Unit.PCS, 4500, 3800, 30.0, 10.0, cityWholesaleId),
            ProductSeed("Cooking oil 1L", foodId, Unit.LITRE, 8500, 7200, 12.0, 4.0, cityWholesaleId),
            ProductSeed("Bread", foodId, Unit.PCS, 4500, 3800, 8.0, 5.0, bakeryExpressId),
            ProductSeed("Milk 500ml", dairyId, Unit.PCS, 2000, 1600, 18.0, 6.0, freshDairyId),
            ProductSeed("Yoghurt 250ml", dairyId, Unit.PCS, 2500, 2000, 10.0, 4.0, freshDairyId),
            ProductSeed("Butter 250g", dairyId, Unit.PCS, 7000, 5800, 6.0, 3.0, freshDairyId),
            ProductSeed("Eggs tray", foodId, Unit.PACK, 12000, 10000, 5.0, 3.0, cityWholesaleId),
            ProductSeed("Soap bar", householdId, Unit.PCS, 3500, 2800, 20.0, 5.0, cleanHomeId),
            ProductSeed("Salt 500g", foodId, Unit.PCS, 1500, 1100, 25.0, 8.0, cityWholesaleId),
            ProductSeed("Soda 500ml", drinksId, Unit.PCS, 1500, 1200, 24.0, 12.0, cityWholesaleId),
            ProductSeed("Maize flour 1kg", foodId, Unit.PCS, 3000, 2400, 20.0, 6.0, cityWholesaleId),
            ProductSeed("Water 1.5L", drinksId, Unit.PCS, 1500, 1000, 16.0, 6.0, cityWholesaleId),
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

            // Initial stock movement
            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    type = MovementType.ADJUSTMENT,
                    qtyChange = p.stockQty,
                    refId = null,
                    reason = "Initial inventory",
                    createdAt = now,
                )
            )
        }

        // 5. Customers
        customerDao.insert(
            CustomerEntity(
                name = "Sarah N.",
                phone = "0772 111 222",
                note = "Pays weekly on Fridays",
            )
        )
        customerDao.insert(
            CustomerEntity(
                name = "Joseph K.",
                phone = "0702 333 444",
                note = "Boda boda rider, pays daily",
            )
        )

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
    }
}
