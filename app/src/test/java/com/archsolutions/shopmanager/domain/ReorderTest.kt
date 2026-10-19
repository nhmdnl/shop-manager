package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.dao.SupplierProductRow
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    private val english = OrderTemplates(greeting = "Hello %1\$s, order from %2\$s:", line = "- %1\$s × %2\$s", thanks = "Thank you.")

    private fun supplier(id: Long, name: String) =
        SupplierEntity(id = id, name = name, contactPerson = null, phone = null, whatsapp = null, location = null, notes = null)

    private fun row(supplierId: Long, productId: Long, name: String, stock: Double, low: Double) =
        SupplierProductRow(supplierId, productId, name, stockQty = stock, lowStockLevel = low, cost = 1_000L)

    @Test
    fun `order message matches the 5_6 format`() {
        val text = Reorder.message(english, "Peter", "Mary Shop", listOf("Milk 500ml" to 24L, "Yoghurt 250ml" to 12L))
        assertEquals("Hello Peter, order from Mary Shop:\n- Milk 500ml × 24\n- Yoghurt 250ml × 12\nThank you.", text)
    }

    @Test
    fun `suggested quantity is twice the alert level minus stock, at least one, rounded up`() {
        assertEquals(17L, Reorder.suggested(lowStockLevel = 10.0, stockQty = 3.0)) // seed Milk
        assertEquals(10L, Reorder.suggested(lowStockLevel = 6.0, stockQty = 2.0)) // seed Yoghurt
        assertEquals(4L, Reorder.suggested(lowStockLevel = 2.5, stockQty = 1.2)) // 3.8 kg -> 4
        assertEquals(1L, Reorder.suggested(lowStockLevel = 4.0, stockQty = 9.0)) // never below one
        assertEquals(2L, Reorder.suggested(lowStockLevel = 1.1, stockQty = 0.2)) // float noise must not add one
    }

    @Test
    fun `reorder lines are the supplier's low products only`() {
        val rows = listOf(
            row(2, 10, "Butter 250g", stock = 9.0, low = 4.0),
            row(2, 11, "Milk 500ml", stock = 3.0, low = 10.0),
            row(1, 11, "Milk 500ml", stock = 3.0, low = 10.0),
        )
        assertEquals(listOf(ReorderLine(11, "Milk 500ml", 3.0, 17L)), Reorder.lines(rows, supplierId = 2))
    }

    @Test
    fun `banner counts each low product once and ignores inactive suppliers`() {
        val rows = listOf(
            row(1, 11, "Milk 500ml", stock = 3.0, low = 10.0),
            row(2, 11, "Milk 500ml", stock = 3.0, low = 10.0), // the same product from a second supplier
            row(2, 12, "Bread", stock = 8.0, low = 10.0),
            row(2, 13, "Soap bar", stock = 40.0, low = 12.0),
            row(9, 14, "Eggs tray", stock = 1.0, low = 8.0), // supplier 9 is inactive (not in the list)
        )
        val summaries = Reorder.summaries(listOf(supplier(1, "City Wholesale"), supplier(2, "Fresh Dairy Co.")), rows)
        assertEquals(listOf(1 to 1, 3 to 2), summaries.map { it.productCount to it.lowCount })
        assertEquals(LowAcrossSuppliers(items = 2, suppliers = 2), Reorder.lowAcross(summaries, rows))
    }
}
