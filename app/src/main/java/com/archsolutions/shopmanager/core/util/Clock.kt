package com.archsolutions.shopmanager.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

interface AppClock {
    fun nowMillis(): Long
    fun todayDateString(): String
    val zoneId: ZoneId
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun todayDateString(): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis()), zoneId).format(dateFormatter)
}
