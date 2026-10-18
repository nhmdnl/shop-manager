package com.archsolutions.shopmanager.testutil

import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import java.time.ZoneId

/** Clock whose business date a test can move, e.g. past midnight. */
class FakeClock(var date: String = "2026-09-30") : AppClock {
    override fun nowMillis(): Long = 1_727_680_000_000L
    override fun todayDateString(): String = date
    override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
}

fun testDay(
    date: String,
    status: DayStatus,
    id: Long = 0,
    countedCash: Long? = null,
    countedMobile: Long? = null,
) = BusinessDayEntity(
    id = id,
    date = date,
    status = status,
    openedAt = 0L,
    openingCash = 0L,
    openingMobile = 0L,
    closedAt = if (status == DayStatus.CLOSED) 0L else null,
    countedCash = countedCash,
    countedMobile = countedMobile,
)
