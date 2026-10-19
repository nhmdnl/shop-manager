package com.archsolutions.shopmanager.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.domain.CsvProduct
import com.archsolutions.shopmanager.domain.ProductCsv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [added] new products; [skipped] rows already in the shop by name or unreadable. */
data class ImportResult(val added: Int, val skipped: Int)

/** Products from a CSV file (7.8), from onboarding or Settings. */
@Singleton
class ProductImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val productRepo: ProductRepo,
) {
    /** Null when the file can't be read at all. */
    suspend fun importFrom(uri: Uri): ImportResult? {
        val text = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        } ?: return null
        val parsed = ProductCsv.parse(text)
        val result = import(parsed.products)
        return result.copy(skipped = result.skipped + parsed.errors.size)
    }

    /** One transaction: the file goes in whole or not at all. Starting stock follows rule C1. */
    suspend fun import(rows: List<CsvProduct>): ImportResult = db.withTransaction {
        val categoryDao = db.categoryDao()
        val names = db.productDao().getAllActive().first().mapTo(HashSet()) { it.name.trim().lowercase() }
        var added = 0
        rows.forEach { row ->
            if (!names.add(row.name.lowercase())) return@forEach
            val categoryId = row.category?.let { name -> categoryDao.getByName(name)?.id ?: categoryDao.insert(CategoryEntity(name = name)) }
            productRepo.createProduct(
                ProductEntity(
                    name = row.name,
                    categoryId = categoryId,
                    unit = row.unit,
                    sellPrice = row.sellPrice,
                    costPrice = row.costPrice,
                    lowStockLevel = row.lowStockLevel,
                    barcode = row.barcode,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                startingStock = row.stock,
            )
            added++
        }
        ImportResult(added = added, skipped = rows.size - added)
    }
}
