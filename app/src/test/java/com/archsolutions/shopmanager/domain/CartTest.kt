package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartTest {

    private fun product(id: Long, price: Long) = ProductEntity(
        id = id,
        name = "P$id",
        categoryId = null,
        sellPrice = price,
        costPrice = price - 500,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `quick cash amounts are exact, next 5k, next 10k and 50k`() {
        // The design board: total 22,500 gives Exact, 25,000, 30,000, 50,000.
        assertEquals(listOf(22_500L, 25_000L, 30_000L, 50_000L), CashQuickAmounts.forTotal(22_500L))
        // Round totals don't repeat a chip.
        assertEquals(listOf(25_000L, 30_000L, 50_000L), CashQuickAmounts.forTotal(25_000L))
        // Above 50,000 the last chip moves to the next 50,000.
        assertEquals(listOf(63_000L, 65_000L, 70_000L, 100_000L), CashQuickAmounts.forTotal(63_000L))
        assertEquals(emptyList<Long>(), CashQuickAmounts.forTotal(0L))
    }

    @Test
    fun `total rounds the exact sum once and item count treats fractions as one`() {
        val lines = listOf(
            CartLine(product(1, 5_000), qty = 2.0),
            CartLine(product(2, 8_000), qty = 1.0),
            CartLine(product(3, 1_500), qty = 3.0),
        )
        assertEquals(22_500L, CartMath.total(lines))
        assertEquals(6, CartMath.itemCount(lines))

        // 1.5 kg at 3,333 = 4,999.5 rounds to 5,000.
        val withKg = lines + CartLine(product(4, 3_333), qty = 1.5)
        assertEquals(27_500L, CartMath.total(withKg))
        assertEquals(7, CartMath.itemCount(withKg))
    }

    @Test
    fun `change is shown only when the cash covers the total`() {
        assertEquals(2_500L, CartMath.change(total = 22_500L, cashReceived = 25_000L))
        assertEquals(0L, CartMath.change(total = 22_500L, cashReceived = 22_500L))
        assertNull(CartMath.change(total = 22_500L, cashReceived = 20_000L))
        assertNull(CartMath.change(total = 22_500L, cashReceived = null))
    }
}
