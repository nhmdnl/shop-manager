package com.archsolutions.shopmanager.feature.scan

import com.archsolutions.shopmanager.core.util.AppClock
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionTest {
    private class FakeClock(var now: Long = 10_000L) : AppClock {
        override fun nowMillis() = now
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private val clock = FakeClock()
    private val session = ScanSession(clock)

    @Test fun `the same code within 1500 ms is ignored`() {
        assertTrue(session.accept("6001"))
        clock.now += 1_499
        assertFalse(session.accept("6001"))
    }

    @Test fun `the same code at exactly 1500 ms is accepted`() {
        assertTrue(session.accept("6001"))
        clock.now += 1_500
        assertTrue(session.accept("6001"))
    }

    @Test fun `a different code is accepted at once`() {
        assertTrue(session.accept("6001"))
        assertTrue(session.accept("6002"))
        assertFalse(session.accept("6001"))
        assertFalse(session.accept("6002"))
    }

    @Test fun `UPC and EAN spellings count as the same code`() {
        assertTrue(session.accept("012345678905"))
        clock.now += 100
        assertFalse(session.accept("0012345678905"))
    }

    @Test fun `an ignored repeat does not extend the window`() {
        assertTrue(session.accept("6001"))
        clock.now += 1_000
        assertFalse(session.accept("6001"))
        clock.now += 500 // 1,500 ms after the accepted scan, 500 after the ignored one
        assertTrue(session.accept("6001"))
    }
}
