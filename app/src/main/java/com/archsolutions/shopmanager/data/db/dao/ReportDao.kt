package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import kotlinx.coroutines.flow.Flow

/** A DONE sale in a report period; `date` is its business day, so late sales count on the day they were made. */
data class ReportSaleRow(
    val id: Long,
    val date: String,
    val createdAt: Long,
    val method: PayMethod,
    val total: Long,
    val costTotal: Long,
    val customerName: String?,
)

data class ReportItemRow(
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitPrice: Long,
)

/** Reports and export (section 10): date ranges are business-day dates (yyyy-MM-dd), inclusive. */
@Dao
interface ReportDao {
    @Query("""
        SELECT s.id AS id, d.date AS date, s.createdAt AS createdAt, s.method AS method, s.total AS total,
               s.costTotal AS costTotal, c.name AS customerName
        FROM sale s
        JOIN business_day d ON d.id = s.dayId
        LEFT JOIN customer c ON c.id = s.customerId
        WHERE s.status = 'DONE' AND d.date BETWEEN :from AND :to
        ORDER BY s.createdAt ASC, s.id ASC
    """)
    fun getSales(from: String, to: String): Flow<List<ReportSaleRow>>

    @Query("""
        SELECT i.saleId AS saleId, i.productId AS productId, i.productName AS productName, i.qty AS qty, i.unitPrice AS unitPrice
        FROM sale_item i
        JOIN sale s ON s.id = i.saleId
        JOIN business_day d ON d.id = s.dayId
        WHERE s.status = 'DONE' AND d.date BETWEEN :from AND :to
        ORDER BY i.id ASC
    """)
    fun getItems(from: String, to: String): Flow<List<ReportItemRow>>

    @Query("SELECT * FROM business_day WHERE status = 'CLOSED' AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun getClosedDays(from: String, to: String): Flow<List<BusinessDayEntity>>
}
