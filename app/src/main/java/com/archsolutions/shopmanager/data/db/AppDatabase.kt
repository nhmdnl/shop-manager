package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.archsolutions.shopmanager.data.db.converters.Converters
import com.archsolutions.shopmanager.data.db.dao.CategoryDao
import com.archsolutions.shopmanager.data.db.dao.CustomerDao
import com.archsolutions.shopmanager.data.db.dao.DayDao
import com.archsolutions.shopmanager.data.db.dao.MoneyEntryDao
import com.archsolutions.shopmanager.data.db.dao.ProductDao
import com.archsolutions.shopmanager.data.db.dao.PurchaseDao
import com.archsolutions.shopmanager.data.db.dao.ReportDao
import com.archsolutions.shopmanager.data.db.dao.SaleDao
import com.archsolutions.shopmanager.data.db.dao.StockMovementDao
import com.archsolutions.shopmanager.data.db.dao.SupplierDao
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.CashCountEntity
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.PurchaseEntity
import com.archsolutions.shopmanager.data.db.entities.PurchaseItemEntity
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.shopmanager.data.db.entities.SaleItemEntity
import com.archsolutions.shopmanager.data.db.entities.StockMovementEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        SupplierEntity::class,
        ProductSupplierEntity::class,
        CustomerEntity::class,
        BusinessDayEntity::class,
        CashCountEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        StockMovementEntity::class,
        MoneyEntryEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun supplierDao(): SupplierDao
    abstract fun customerDao(): CustomerDao
    abstract fun dayDao(): DayDao
    abstract fun saleDao(): SaleDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun moneyEntryDao(): MoneyEntryDao
    abstract fun reportDao(): ReportDao

    companion object {
        const val DATABASE_NAME = "shop_manager.db"

        /** Backups record it; a restore refuses a backup made by a newer schema. */
        const val VERSION = 1

        fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        ).build()
    }

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideSupplierDao(db: AppDatabase): SupplierDao = db.supplierDao()

    @Provides
    fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideDayDao(db: AppDatabase): DayDao = db.dayDao()

    @Provides
    fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()

    @Provides
    fun providePurchaseDao(db: AppDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun provideStockMovementDao(db: AppDatabase): StockMovementDao = db.stockMovementDao()

    @Provides
    fun provideMoneyEntryDao(db: AppDatabase): MoneyEntryDao = db.moneyEntryDao()
}
