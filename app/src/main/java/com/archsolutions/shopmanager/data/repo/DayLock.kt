package com.archsolutions.shopmanager.data.repo

import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.DayStatus

/** Thrown when a write targets a business day that is not OPEN (never opened, or already closed). */
class DayNotOpenException(val dayId: Long) : IllegalStateException("Business day $dayId is not open")

// Locking rules (spec 6.3, 6.7): sales, purchases and money entries need an OPEN day, and
// records of a CLOSED day are read-only. Call inside the write's transaction.
internal suspend fun AppDatabase.requireOpenDay(dayId: Long) {
    if (dayDao().getDayByIdDirect(dayId)?.status != DayStatus.OPEN) throw DayNotOpenException(dayId)
}
