package com.archsolutions.shopmanager.feature.scan

import androidx.lifecycle.SavedStateHandle
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // Log.w in the failure paths
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

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), failLookup: Boolean = false) =
        ScanViewModel({ code -> if (failLookup) error("db") else catalog.firstOrNull { it.barcode == code } }, clock, saved)

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

    @Test fun `the last item keeps its price`() {
        val vm = vm()
        vm.onCode(ScannedCode("6001")) { }
        assertEquals(1000L, vm.state.value.lastPrice)
    }

    @Test fun `a new view model from the same SavedStateHandle restores the state`() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onCode(ScannedCode("6001")) { }
        clock.now += 2_000
        first.onCode(ScannedCode("9999")) { }
        val second = vm(saved)
        assertEquals(first.state.value, second.state.value)
        assertEquals(1, second.state.value.count)
        assertEquals("9999", second.state.value.notFound)
    }

    @Test fun `a failing onKnown is not counted and does not crash`() {
        val vm = vm()
        vm.onCode(ScannedCode("6001")) { error("cart full") }
        assertEquals(0, vm.state.value.count)
    }

    @Test fun `a failing lookup does not crash or change the state`() {
        val vm = vm(failLookup = true)
        vm.onCode(ScannedCode("6001")) { known += it }
        assertEquals(ScanUiState(), vm.state.value)
        assertTrue(known.isEmpty())
    }

    @Test fun `clearNotFound empties the banner and a restore keeps it empty`() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.onCode(ScannedCode("9999")) { known += it }
        assertEquals("9999", vm.state.value.notFound)
        vm.clearNotFound()
        assertNull(vm.state.value.notFound)
        assertNull(vm(saved).state.value.notFound)
    }
}
