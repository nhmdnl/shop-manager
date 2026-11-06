package com.archsolutions.shopmanager.feature.scan

import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private class FakeClock(var now: Long = 10_000L) : AppClock {
        override fun nowMillis() = now
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private fun product(id: Long, name: String, barcode: String) = ProductEntity(
        id = id, name = name, categoryId = null, sellPrice = 1000, costPrice = 500,
        barcode = barcode, active = true, createdAt = 0, updatedAt = 0,
    )

    private val clock = FakeClock()
    private val catalog = listOf(product(1, "Soap", "6001"), product(2, "Salt", "6002"))
    private val known = mutableListOf<ProductEntity>()

    private fun vm() = ScanViewModel({ code -> catalog.firstOrNull { it.barcode == code } }, clock)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `a known code calls onKnown and increments the count`() {
        val vm = vm()
        vm.onCode(ScannedCode("6001")) { known += it }
        assertEquals(listOf(catalog[0]), known)
        assertEquals(1, vm.state.value.count)
        assertEquals("Soap", vm.state.value.last)
        assertNull(vm.state.value.notFound)
    }

    @Test fun `an unknown code sets notFound and does not call onKnown`() {
        val vm = vm()
        vm.onCode(ScannedCode("9999")) { known += it }
        assertTrue(known.isEmpty())
        assertEquals("9999", vm.state.value.notFound)
        assertEquals(0, vm.state.value.count)
    }

    @Test fun `a repeat inside 1500 ms is ignored`() {
        val vm = vm()
        vm.onCode(ScannedCode("6001")) { known += it }
        clock.now += 1_000
        vm.onCode(ScannedCode("6001")) { known += it }
        assertEquals(1, known.size)
        assertEquals(1, vm.state.value.count)
        clock.now += 600
        vm.onCode(ScannedCode("6001")) { known += it }
        assertEquals(2, vm.state.value.count)
    }

    @Test fun `a second known scan of another code is counted`() {
        val vm = vm()
        vm.onCode(ScannedCode("6001")) { known += it }
        vm.onCode(ScannedCode("6002")) { known += it }
        assertEquals(2, vm.state.value.count)
        assertEquals("Salt", vm.state.value.last)
        assertEquals(2, known.size)
    }
}
