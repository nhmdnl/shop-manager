package com.archsolutions.shopmanager.core.util

import java.time.Instant
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

    override fun todayDateString(): String = dateString(nowMillis())

    // atZone().toLocalDate() instead of LocalDate.ofInstant(), which needs API 34 (minSdk is 26).
    internal fun dateString(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().format(dateFormatter)
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class ClockModule {
    @dagger.Binds
    @Singleton
    abstract fun bindAppClock(clock: SystemAppClock): AppClock
}

