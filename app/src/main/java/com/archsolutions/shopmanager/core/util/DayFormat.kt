package com.archsolutions.shopmanager.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DayFormat {
    private val clockTime = DateTimeFormatter.ofPattern("HH:mm")

    /** Wall-clock time such as "07:58" in the shop's time zone. */
    fun time(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(clockTime)

    /** A business date (yyyy-MM-dd) in [pattern], e.g. "EEEE d MMM yyyy" gives "Wednesday 30 Sep 2026". */
    fun date(date: String, pattern: String, locale: Locale): String =
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, locale))
}
