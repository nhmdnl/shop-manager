package com.archsolutions.shopmanager.feature.scan

import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanControllerTest {
    private class FakeClock(var now: Long = 10_000L) : AppClock {
        override fun nowMillis() = now
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private fun product(id: Long, name: String, active: Boolean = true) = ProductEntity(
        id = id, name = name, categoryId = null, sellPrice = 1000, costPrice = 500,
        barcode = "6001", active = active, createdAt = 0, updatedAt = 0,
    )

    private val clock = FakeClock()
    private var queries = 0
    private val catalog = mutableListOf<ProductEntity>()

    private fun controller() = ScanController(ScanSession(clock)) { code ->
        queries++
        catalog.firstOrNull { it.active && it.barcode == code }
    }

    @Test fun `a known code gives Known`() = runBlocking {
        val p = product(1, "Soap").also { catalog += it }
        assertEquals(ScanOutcome.Known(p), controller().handle(ScannedCode("6001")))
    }

    @Test fun `an unknown code gives NotFound with the scanned text`() = runBlocking {
        assertEquals(ScanOutcome.NotFound("9999"), controller().handle(ScannedCode("9999")))
    }

    @Test fun `an inactive-only product gives NotFound`() = runBlocking {
        catalog += product(1, "Old", active = false)
        assertEquals(ScanOutcome.NotFound("6001"), controller().handle(ScannedCode("6001")))
    }

    @Test fun `a repeat inside the window gives Ignored and does not query`() = runBlocking {
        catalog += product(1, "Soap")
        val c = controller()
        c.handle(ScannedCode("6001"))
        assertEquals(1, queries)
        assertEquals(ScanOutcome.Ignored, c.handle(ScannedCode("6001")))
        assertEquals(1, queries)
    }

    @Test fun `reduce counts known scans and keeps the last line`() {
        val soap = product(1, "Soap")
        val salt = product(2, "Salt")
        val s = ScanUiState()
            .reduce(ScanOutcome.Known(soap)) { it.name }
            .reduce(ScanOutcome.Known(salt)) { it.name }
            .reduce(ScanOutcome.Ignored) { "x" }
        assertEquals(2, s.count)
        assertEquals("Salt", s.last)
    }

    @Test fun `reduce sets and clears notFound`() {
        val s1 = ScanUiState(count = 1, last = "Soap").reduce(ScanOutcome.NotFound("9999")) { it.name }
        assertEquals("9999", s1.notFound)
        assertEquals(1, s1.count)
        assertEquals("Soap", s1.last)
        val s2 = s1.reduce(ScanOutcome.Known(product(1, "Soap"))) { it.name }
        assertNull(s2.notFound)
        assertEquals(2, s2.count)
    }
}
