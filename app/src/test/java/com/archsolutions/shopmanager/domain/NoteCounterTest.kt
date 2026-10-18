package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.CountPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCounterTest {

    @Test
    fun `total adds every note and the coin amount`() {
        // The counts on the "Open the day" design board.
        val notes = mapOf(50_000L to 2, 20_000L to 1, 10_000L to 2, 5_000L to 1, 2_000L to 2, 1_000L to 1)

        assertEquals(150_000L, NoteCounter.total(notes, coins = 0L))
        assertEquals(150_500L, NoteCounter.total(notes, coins = 500L))
    }

    @Test
    fun `cash count rows skip empty notes and keep coins as an amount`() {
        val rows = NoteCounter.toCashCounts(mapOf(50_000L to 2, 20_000L to 0), coins = 700L, phase = CountPhase.CLOSE)

        assertEquals(listOf(50_000L to 2, NoteCounter.COINS to 700), rows.map { it.denomination to it.count })
        assertTrue(rows.all { it.phase == CountPhase.CLOSE })
    }
}
