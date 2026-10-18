package com.archsolutions.shopmanager.app

import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.testutil.testDay
import org.junit.Assert.assertEquals
import org.junit.Test

// Spec 6.3 app start gate, cases 1–4.
class DayGateTest {

    @Test
    fun `case 1 - an earlier day still open blocks today`() {
        val yesterday = testDay("2026-09-29", DayStatus.OPEN, id = 1)

        assertEquals(
            DayGateState.PreviousDayStillOpen(yesterday),
            evaluateDayGate(previousOpenDay = yesterday, today = null, openDayOnStart = true),
        )
        // Wins over every other case, whatever today's row or the setting says.
        assertEquals(
            DayGateState.PreviousDayStillOpen(yesterday),
            evaluateDayGate(yesterday, testDay("2026-09-30", DayStatus.OPEN, id = 2), openDayOnStart = false),
        )
    }

    @Test
    fun `case 2 - no day for today shows Open the day`() {
        assertEquals(
            DayGateState.OpenDayRequired,
            evaluateDayGate(previousOpenDay = null, today = null, openDayOnStart = true),
        )
    }

    @Test
    fun `case 2 with openDayOnStart off keeps Home locked instead`() {
        assertEquals(
            DayGateState.DayNotOpen,
            evaluateDayGate(previousOpenDay = null, today = null, openDayOnStart = false),
        )
    }

    @Test
    fun `case 3 - today closed is read-only`() {
        val today = testDay("2026-09-30", DayStatus.CLOSED, id = 2)

        assertEquals(
            DayGateState.TodayClosed(today),
            evaluateDayGate(previousOpenDay = null, today = today, openDayOnStart = true),
        )
    }

    @Test
    fun `case 4 - today open is ready to trade`() {
        val today = testDay("2026-09-30", DayStatus.OPEN, id = 2)

        assertEquals(
            DayGateState.Ready(today),
            evaluateDayGate(previousOpenDay = null, today = today, openDayOnStart = true),
        )
    }
}
