package com.archsolutions.shopmanager.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CloseReminderTest {

    private val kampala = ZoneId.of("Africa/Kampala")
    private val nine = LocalTime.of(21, 0)

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, kampala)

    @Test
    fun `before the reminder time it fires this evening`() {
        assertEquals(at(18, 21, 0), nextReminderAt(at(18, 8, 15), nine))
    }

    @Test
    fun `at or after the reminder time it waits for tomorrow`() {
        assertEquals(at(19, 21, 0), nextReminderAt(at(18, 21, 0), nine))
        assertEquals(at(19, 21, 0), nextReminderAt(at(18, 23, 40), nine))
    }

    @Test
    fun `a run that fires a moment early plans tomorrow, not tonight again`() {
        // The worker plans from a minute ahead (scheduleNext).
        assertEquals(at(19, 21, 0), nextReminderAt(at(18, 20, 59, 30).plusMinutes(1), nine))
    }

    @Test
    fun `reads the settings time and falls back to 21_00`() {
        assertEquals(LocalTime.of(7, 30), parseReminderTime("07:30"))
        assertEquals(nine, parseReminderTime("21:00"))
        assertEquals(nine, parseReminderTime("nine pm"))
    }
}
