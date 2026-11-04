package com.archsolutions.shopmanager.data.cloud.sync

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncWorkResultTest {
    private val waits = mutableListOf<Int>()

    private fun map(run: suspend () -> SyncResult): Result = runBlocking { syncWorkResult(run) { waits += it } }

    @Test fun `retry outcome retries`() {
        assertEquals(Result.retry(), map { SyncResult(SyncOutcome.RETRY) })
        assertTrue(waits.isEmpty())
    }

    @Test fun `wait plans a later run after the wait seconds and succeeds`() {
        assertEquals(Result.success(), map { SyncResult(SyncOutcome.WAIT, 90) })
        assertEquals(listOf(90), waits)
    }

    @Test fun `every other outcome succeeds without planning anything`() {
        for (o in SyncOutcome.entries - setOf(SyncOutcome.RETRY, SyncOutcome.WAIT)) {
            assertEquals(o.name, Result.success(), map { SyncResult(o) })
        }
        assertTrue(waits.isEmpty())
    }

    @Test fun `an exception fails the work instead of throwing`() {
        assertEquals(Result.failure(), map { error("boom") })
        assertTrue(waits.isEmpty())
    }

    @Test fun `an exception while planning the later run also fails`() {
        val r = runBlocking { syncWorkResult({ SyncResult(SyncOutcome.WAIT, 5) }) { error("no work manager") } }
        assertEquals(Result.failure(), r)
    }

    @Test fun `cancellation is rethrown`() {
        try {
            map { throw CancellationException("stopped") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("stopped", e.message)
        }
    }
}
