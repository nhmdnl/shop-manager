package com.archsolutions.shopmanager.data.staff

import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class StaffSessionTest {
    private class FakeClock(var now: Long = 1_000_000L) : AppClock {
        override fun nowMillis() = now
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private fun person(id: Long) = StaffEntity(id = id, name = "P$id", role = "CASHIER", pinHash = "", createdAt = 0)

    @Test
    fun `idle for the timeout signs out, and a touch before it resets the timer`() {
        val clock = FakeClock()
        val session = StaffSession(clock)
        session.signIn(person(1))
        clock.now += 5 * 60_000L - 1
        session.checkIdle(5)
        assertNotNull(session.current.value)
        session.touch()
        clock.now += 5 * 60_000L - 1
        session.checkIdle(5)
        assertNotNull(session.current.value)
        clock.now += 1
        session.checkIdle(5)
        assertNull(session.current.value)
        assertNull(session.currentId)
    }

    @Test
    fun `onDeactivated signs out only that person`() {
        val session = StaffSession(FakeClock())
        session.signIn(person(1))
        session.onDeactivated(2)
        assertEquals(1L, session.currentId)
        session.onDeactivated(1)
        assertNull(session.current.value)
    }

    @Test
    fun `actingId is approvedBy when set, else the current person`() {
        val session = StaffSession(FakeClock())
        assertNull(session.actingId)
        session.signIn(person(1))
        assertEquals(1L, session.actingId)
        session.approvedBy = 9L
        assertEquals(9L, session.actingId)
        session.approvedBy = null
        assertEquals(1L, session.actingId)
    }
}
