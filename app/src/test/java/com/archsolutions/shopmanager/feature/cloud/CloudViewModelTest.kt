package com.archsolutions.shopmanager.feature.cloud

import androidx.work.WorkInfo
import com.archsolutions.cloud.contract.PlanState
import com.archsolutions.cloud.contract.ShopStatus
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.cloud.CloudLink
import com.archsolutions.shopmanager.data.cloud.CloudState
import com.archsolutions.shopmanager.data.cloud.FixedClock
import com.archsolutions.shopmanager.data.cloud.sync.CloudSync
import com.archsolutions.shopmanager.data.cloud.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

private val ZONE = ZoneId.of("Africa/Kampala")

private fun at(month: Int, day: Int, hour: Int = 19, minute: Int = 42): Long =
    ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

private fun status(
    state: PlanState,
    trialEnd: Long? = null,
    periodEnd: Long? = null,
    graceEnd: Long? = null,
    features: List<String> = listOf("backup"),
) = ShopStatus(plan = "basic", state = state, trialEnd = trialEnd, periodEnd = periodEnd, graceEnd = graceEnd, features = features)

private class FakeLink(initial: CloudState) : CloudLink {
    val flow = MutableStateFlow(initial)
    override val state: Flow<CloudState> = flow
    var refreshes = 0
    var unlinks = 0
    var refreshResult: ShopStatus? = null

    override suspend fun refreshStatus(): ShopStatus? {
        refreshes++
        return refreshResult
    }

    override suspend fun unlink() {
        unlinks++
        flow.value = CloudState.SignedOut
    }
}

private class FakeWork : CloudWork {
    val infos = MutableStateFlow<List<WorkInfo>>(emptyList())
    override val workInfos: Flow<List<WorkInfo>> = infos
    var backUps = 0
    override fun backUpNow() {
        backUps++
    }
}

private class FakeCloudSync(initial: SyncStatus = SyncStatus(hasOutbox = false, fillPending = false, lastSyncAt = null)) : CloudSync {
    val flow = MutableStateFlow(initial)
    override val status: Flow<SyncStatus> = flow
}

/** Plain English texts shaped like the plan's examples; the Android one reads strings.xml. */
private class FakeTexts : CloudTexts {
    override fun replaced() = "replaced"
    override fun paused() = "paused"
    override fun waiting() = "waiting"
    override fun noBackup() = "none"
    override fun lastBackup(day: BackupDay, at: Long, zone: ZoneId): String {
        val time = DayFormat.time(at, zone)
        return when (day) {
            BackupDay.TODAY -> "today $time"
            BackupDay.YESTERDAY -> "yesterday $time"
            BackupDay.EARLIER -> "${DayFormat.dateOf(at, zone, "d MMM", Locale.ENGLISH)} $time"
        }
    }
    override fun ownerViewOn(day: BackupDay, at: Long, zone: ZoneId) = "owner on $day ${DayFormat.time(at, zone)}"
    override fun ownerViewOnNever() = "owner on"
    override fun ownerViewFilling() = "owner filling"
    override fun ownerViewWaiting() = "owner waiting"
    override fun trialUntil(at: Long, zone: ZoneId) = "trial ${DayFormat.dateOf(at, zone, "d MMM", Locale.ENGLISH)}"
    override fun activeUntil(at: Long, zone: ZoneId) = "active ${DayFormat.dateOf(at, zone, "d MMM", Locale.ENGLISH)}"
    override fun payBy(at: Long, zone: ZoneId) = "pay ${DayFormat.dateOf(at, zone, "d MMM", Locale.ENGLISH)}"
}

private fun workInfo(state: WorkInfo.State) = WorkInfo(UUID.randomUUID(), state, setOf("cloud-backup"))

@OptIn(ExperimentalCoroutinesApi::class)
class CloudViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(at(11, 20, 12, 0))
    private val work = FakeWork()
    private val sync = FakeCloudSync()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(link: FakeLink) = CloudViewModel(link, work, sync, FakeTexts(), clock)

    private fun linked(status: ShopStatus? = null, lastBackup: Long? = null) = CloudState.Linked(status, lastBackup)

    private suspend fun CloudViewModel.cardNow(): CloudCardState = settled(card)

    private suspend fun CloudViewModel.noticeNow(): CloudNotice? = settled(notice)

    /** The flows are WhileSubscribed: collect while the dispatcher runs, then read the value. */
    private suspend fun <T> settled(flow: kotlinx.coroutines.flow.StateFlow<T>): T = coroutineScope {
        val job = launch { flow.collect { } }
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()
        flow.value
    }

    @Test fun `Off hides the card`() = runTest(dispatcher) {
        val vm = viewModel(FakeLink(CloudState.Off))
        val card = vm.cardNow()
        assertFalse(card.visible)
        assertFalse(card.canBackUpNow)
    }

    @Test fun `signed out and replaced show the card without a plan`() = runTest(dispatcher) {
        val signedOut = viewModel(FakeLink(CloudState.SignedOut)).cardNow()
        assertTrue(signedOut.visible)
        assertFalse(signedOut.signedIn)
        assertEquals("none", signedOut.backupLine)
        assertNull(signedOut.planLine)
        val replaced = viewModel(FakeLink(CloudState.Replaced)).cardNow()
        assertFalse(replaced.signedIn)
        assertEquals("replaced", replaced.backupLine)
    }

    @Test fun `line precedence replaced over paused over waiting over last backup`() = runTest(dispatcher) {
        work.infos.value = listOf(workInfo(WorkInfo.State.ENQUEUED))
        val last = at(11, 20, 8, 5)

        assertEquals("replaced", viewModel(FakeLink(CloudState.Replaced)).cardNow().backupLine)

        val lapsed = viewModel(FakeLink(linked(status(PlanState.LAPSED), last))).cardNow()
        assertEquals("paused", lapsed.backupLine)
        assertNull(lapsed.planLine)

        val waiting = viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(12, 20)), last))).cardNow()
        assertEquals("waiting", waiting.backupLine)

        work.infos.value = listOf(workInfo(WorkInfo.State.RUNNING), workInfo(WorkInfo.State.SUCCEEDED))
        assertEquals("today 08:05", viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(12, 20)), last))).cardNow().backupLine)

        assertEquals("none", viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(12, 20))))).cardNow().backupLine)
    }

    @Test fun `a paused plan disables Back up now, other plan states allow it`() = runTest(dispatcher) {
        assertFalse(viewModel(FakeLink(linked(status(PlanState.LAPSED)))).cardNow().canBackUpNow)
        assertTrue(viewModel(FakeLink(linked(status(PlanState.GRACE, graceEnd = at(12, 20))))).cardNow().canBackUpNow)
        assertTrue(viewModel(FakeLink(linked(null))).cardNow().canBackUpNow)
    }

    @Test fun `last backup reads today yesterday or date in shop zone`() = runTest(dispatcher) {
        fun line(last: Long) = viewModel(FakeLink(linked(null, last)))
        assertEquals("today 19:42", line(at(11, 20, 19, 42)).cardNow().backupLine)
        assertEquals("yesterday 19:42", line(at(11, 19, 19, 42)).cardNow().backupLine)
        assertEquals("12 Nov 19:42", line(at(11, 12, 19, 42)).cardNow().backupLine)
        // 22:30 UTC on the 19th is 01:30 on the 20th in Kampala: today, not yesterday
        val utcEvening = ZonedDateTime.of(2026, 11, 19, 22, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("today 01:30", line(utcEvening).cardNow().backupLine)
    }

    @Test fun `plan lines follow the plan state`() = runTest(dispatcher) {
        val end = at(11, 23)
        assertEquals("trial 23 Nov", viewModel(FakeLink(linked(status(PlanState.TRIAL, trialEnd = end)))).cardNow().planLine)
        assertEquals("active 23 Nov", viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = end)))).cardNow().planLine)
        assertEquals("pay 7 Dec", viewModel(FakeLink(linked(status(PlanState.GRACE, periodEnd = end, graceEnd = at(12, 7))))).cardNow().planLine)
    }

    @Test fun `trial ending in 2 days gives ENDS_SOON notice`() = runTest(dispatcher) {
        val vm = viewModel(FakeLink(linked(status(PlanState.TRIAL, trialEnd = at(11, 22, 12, 0)))))
        assertEquals(CloudNotice.ENDS_SOON, vm.noticeNow())
    }

    @Test fun `active with 10 days left gives no notice`() = runTest(dispatcher) {
        val vm = viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(11, 30, 12, 0)))))
        assertNull(vm.noticeNow())
    }

    @Test fun `grace and lapsed give their notices and signed out gives none`() = runTest(dispatcher) {
        assertEquals(CloudNotice.GRACE, viewModel(FakeLink(linked(status(PlanState.GRACE, graceEnd = at(12, 7))))).noticeNow())
        assertEquals(CloudNotice.PAUSED, viewModel(FakeLink(linked(status(PlanState.LAPSED)))).noticeNow())
        assertNull(viewModel(FakeLink(CloudState.SignedOut)).noticeNow())
        assertNull(viewModel(FakeLink(CloudState.Off)).noticeNow())
        assertNull(viewModel(FakeLink(linked(null))).noticeNow())
    }

    @Test fun `refresh failure keeps cached status`() = runTest(dispatcher) {
        val link = FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(11, 23))))
        link.refreshResult = null // offline: refreshStatus returns null and the cache stays
        val vm = viewModel(link)
        vm.refresh()
        val card = vm.cardNow()
        assertEquals(1, link.refreshes)
        assertEquals("active 23 Nov", card.planLine)
    }

    @Test fun `every refresh asks again, so a payment made meanwhile shows`() = runTest(dispatcher) {
        val link = FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(11, 23))))
        val vm = viewModel(link)
        assertEquals(0, link.refreshes)
        vm.refresh()
        vm.cardNow()
        vm.refresh()
        vm.cardNow()
        assertEquals(2, link.refreshes)
    }

    @Test fun `a refresh that throws is ignored`() = runTest(dispatcher) {
        val link = object : CloudLink by FakeLink(linked(null)) {
            override suspend fun refreshStatus(): ShopStatus? = throw java.io.IOException("offline")
        }
        val vm = CloudViewModel(link, work, sync, FakeTexts(), clock)
        vm.refresh()
        assertTrue(vm.cardNow().visible)
    }

    @Test fun `backUpNow and unlink reach their collaborators`() = runTest(dispatcher) {
        val link = FakeLink(linked(null))
        val vm = viewModel(link)
        vm.backUpNow()
        assertEquals(1, work.backUps)
        vm.unlink()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, link.unlinks)
        assertFalse(vm.cardNow().signedIn)
    }

    private val live = listOf("backup", "live")

    @Test fun `owner view line is absent without live`() = runTest(dispatcher) {
        val withoutLive = viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(12, 20))))).cardNow()
        assertNull(withoutLive.ownerViewLine)
        assertNull(viewModel(FakeLink(CloudState.SignedOut)).cardNow().ownerViewLine)
    }

    private suspend fun liveLine(s: SyncStatus): String? {
        sync.flow.value = s
        return viewModel(FakeLink(linked(status(PlanState.ACTIVE, periodEnd = at(12, 20), features = live)))).cardNow().ownerViewLine
    }

    @Test fun `filling wins over waiting`() = runTest(dispatcher) {
        assertEquals("owner filling", liveLine(SyncStatus(hasOutbox = true, fillPending = true, lastSyncAt = null)))
    }

    @Test fun `entries waiting 31 minutes after the last sync read waiting`() = runTest(dispatcher) {
        val last = clock.nowMillis() - 31 * 60_000L
        assertEquals("owner waiting", liveLine(SyncStatus(hasOutbox = true, fillPending = false, lastSyncAt = last)))
        assertEquals("owner waiting", liveLine(SyncStatus(hasOutbox = true, fillPending = false, lastSyncAt = null)))
    }

    @Test fun `entries waiting 10 minutes after the last sync read on with the time`() = runTest(dispatcher) {
        val last = clock.nowMillis() - 10 * 60_000L
        assertEquals("owner on TODAY 11:50", liveLine(SyncStatus(hasOutbox = true, fillPending = false, lastSyncAt = last)))
    }

    @Test fun `never synced and nothing waiting reads on`() = runTest(dispatcher) {
        assertEquals("owner on", liveLine(SyncStatus(hasOutbox = false, fillPending = false, lastSyncAt = null)))
    }
}
