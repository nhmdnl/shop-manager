package com.archsolutions.shopmanager.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.settings.PinGuard
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneId

class PinTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private class TickingClock(var now: Long = 1_000_000L) : AppClock {
        override fun nowMillis(): Long = now
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @Test
    fun `hash is salted and only the right PIN matches`() {
        val stored = Pin.hash("1234")
        assertTrue(Pin.matches("1234", stored))
        assertFalse(Pin.matches("1235", stored))
        assertFalse(Pin.matches("1234", ""))
        assertNotEquals(stored, Pin.hash("1234")) // a new salt every time
    }

    @Test
    fun `five wrong tries lock the PIN for 30 seconds, then it works again`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("pin.preferences_pb") }))
        store.setPin(enabled = true, hash = Pin.hash("2580"))
        val clock = TickingClock()
        val guard = PinGuard(store, clock)

        repeat(4) { assertEquals(PinResult.Wrong(triesLeft = 4 - it), guard.check("0000")) }
        assertEquals(PinResult.Locked(Pin.LOCKOUT_MILLIS), guard.check("0000"))
        // Even the right PIN waits out the lock.
        assertTrue(guard.check("2580") is PinResult.Locked)

        clock.now += Pin.LOCKOUT_MILLIS
        assertEquals(PinResult.Ok, guard.check("2580"))
        assertEquals(PinResult.Wrong(triesLeft = 4), guard.check("1111")) // count starts over
    }
}
