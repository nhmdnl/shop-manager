package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyRepo @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val moneyEntryDao = db.moneyEntryDao()

    fun getEntriesForDay(dayId: Long): Flow<List<MoneyEntryEntity>> =
        moneyEntryDao.getEntriesForDay(dayId)

    suspend fun getEntriesForDayDirect(dayId: Long): List<MoneyEntryEntity> =
        moneyEntryDao.getEntriesForDayDirect(dayId)

    private suspend fun insertForOpenDay(entry: MoneyEntryEntity): Long = db.withTransaction {
        db.requireOpenDay(entry.dayId)
        moneyEntryDao.insertEntry(entry)
    }

    suspend fun addExpense(
        dayId: Long,
        amount: Long,
        method: MoneyMethod,
        category: String,
        note: String? = null,
    ): Long {
        require(amount > 0) { "Amount must be positive" }
        return insertForOpenDay(
            MoneyEntryEntity(
                dayId = dayId,
                createdAt = clock.nowMillis(),
                type = MoneyEntryType.EXPENSE,
                method = method,
                amount = amount,
                category = category,
                note = note,
            )
        )
    }

    suspend fun addCreditRepayment(
        dayId: Long,
        customerId: Long,
        amount: Long,
        method: MoneyMethod,
        note: String? = null,
    ): Long {
        require(amount > 0) { "Amount must be positive" }
        return insertForOpenDay(
            MoneyEntryEntity(
                dayId = dayId,
                createdAt = clock.nowMillis(),
                type = MoneyEntryType.CREDIT_REPAYMENT,
                method = method,
                amount = amount,
                customerId = customerId,
                note = note,
            )
        )
    }

    suspend fun addCashOut(
        dayId: Long,
        amount: Long,
        method: MoneyMethod,
        note: String? = null,
    ): Long {
        require(amount > 0) { "Amount must be positive" }
        return insertForOpenDay(
            MoneyEntryEntity(
                dayId = dayId,
                createdAt = clock.nowMillis(),
                type = MoneyEntryType.CASH_OUT,
                method = method,
                amount = amount,
                note = note,
            )
        )
    }

    suspend fun addCashIn(
        dayId: Long,
        amount: Long,
        method: MoneyMethod,
        note: String? = null,
    ): Long {
        require(amount > 0) { "Amount must be positive" }
        return insertForOpenDay(
            MoneyEntryEntity(
                dayId = dayId,
                createdAt = clock.nowMillis(),
                type = MoneyEntryType.CASH_IN,
                method = method,
                amount = amount,
                note = note,
            )
        )
    }
}
