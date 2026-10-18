package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.CashCountEntity
import com.archsolutions.shopmanager.data.db.entities.CountPhase

/** UGX note counter for opening (6.4) and closing (6.6) the day. */
object NoteCounter {
    val DENOMINATIONS: List<Long> = listOf(50_000L, 20_000L, 10_000L, 5_000L, 2_000L, 1_000L)

    /** `cash_count.denomination` used for coins; the row's `count` holds the coin amount in shillings. */
    const val COINS: Long = 0L

    fun total(notes: Map<Long, Int>, coins: Long): Long =
        DENOMINATIONS.sumOf { it * (notes[it] ?: 0) } + coins

    /** `cash_count` rows for the non-zero entries. DayRepo assigns the dayId when it saves them. */
    fun toCashCounts(notes: Map<Long, Int>, coins: Long, phase: CountPhase): List<CashCountEntity> {
        val noteRows = DENOMINATIONS.mapNotNull { denomination ->
            val count = notes[denomination] ?: 0
            if (count > 0) CashCountEntity(dayId = 0, phase = phase, denomination = denomination, count = count) else null
        }
        val coinRow = if (coins > 0) CashCountEntity(dayId = 0, phase = phase, denomination = COINS, count = coins.toInt()) else null
        return noteRows + listOfNotNull(coinRow)
    }
}
