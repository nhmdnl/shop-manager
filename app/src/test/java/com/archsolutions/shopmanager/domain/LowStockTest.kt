package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowStockTest {
    private fun product(qty: Double, level: Double) = ProductEntity(
        name = "Sugar", categoryId = null, sellPrice = 5_000, costPrice = 4_000,
        stockQty = qty, lowStockLevel = level, createdAt = 0L, updatedAt = 0L,
    )

    @Test fun `stock equal to the alert level is low`() = assertTrue(product(2.0, 2.0).isLowStock)

    @Test fun `stock below the alert level is low`() = assertTrue(product(1.5, 2.0).isLowStock)

    @Test fun `stock above the alert level is not low`() = assertFalse(product(2.5, 2.0).isLowStock)
}
