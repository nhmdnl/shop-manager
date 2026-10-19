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

    /** A report period: "24–30 Sep", "28 Aug – 3 Sep", or across years "29 Dec 2025 – 4 Jan 2026". */
    fun range(from: LocalDate, to: LocalDate, locale: Locale): String {
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        return when {
            from == to -> from.format(dayMonth)
            from.year != to.year -> "${from.format(full)} – ${to.format(full)}"
            from.month == to.month -> "${from.dayOfMonth}–${to.format(dayMonth)}"
            else -> "${from.format(dayMonth)} – ${to.format(dayMonth)}"
        }
    }

    /** A moment's calendar date in the shop's time zone, e.g. "20 Sep". */
    fun dateOf(millis: Long, zone: ZoneId, pattern: String, locale: Locale): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))

    /** Whole days between two moments in the shop's time zone (calendar days, not 24 h blocks). */
    fun daysBetween(fromMillis: Long, toMillis: Long, zone: ZoneId): Long =
        java.time.temporal.ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate(),
        )
}
