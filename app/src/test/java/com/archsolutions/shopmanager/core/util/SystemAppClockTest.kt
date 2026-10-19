package com.archsolutions.shopmanager.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SystemAppClockTest {

    private val clock = SystemAppClock()

    @Test
    fun `business date turns over at Kampala midnight, not UTC midnight`() {
        // Kampala is UTC+3 all year.
        assertEquals("2026-09-30", clock.dateString(Instant.parse("2026-09-29T21:00:00Z").toEpochMilli()))
        assertEquals("2026-09-30", clock.dateString(Instant.parse("2026-09-30T20:59:59Z").toEpochMilli()))
        assertEquals("2026-10-01", clock.dateString(Instant.parse("2026-09-30T21:00:00Z").toEpochMilli()))
    }
}
