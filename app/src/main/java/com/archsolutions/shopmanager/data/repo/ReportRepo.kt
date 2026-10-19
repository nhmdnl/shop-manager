package com.archsolutions.shopmanager.data.repo

import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.ReportItemRow
import com.archsolutions.shopmanager.data.db.dao.ReportSaleRow
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Report rows by business-day date (section 10); the math lives in `domain/Reports`. */
@Singleton
class ReportRepo @Inject constructor(
    db: AppDatabase,
) {
    private val reportDao = db.reportDao()

    fun getSales(from: LocalDate, to: LocalDate): Flow<List<ReportSaleRow>> = reportDao.getSales(from.toString(), to.toString())

    fun getItems(from: LocalDate, to: LocalDate): Flow<List<ReportItemRow>> = reportDao.getItems(from.toString(), to.toString())

    fun getClosedDays(from: LocalDate, to: LocalDate): Flow<List<BusinessDayEntity>> =
        reportDao.getClosedDays(from.toString(), to.toString())
}
