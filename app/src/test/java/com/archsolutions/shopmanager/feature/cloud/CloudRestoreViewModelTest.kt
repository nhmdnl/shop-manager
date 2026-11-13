package com.archsolutions.shopmanager.feature.cloud

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.cloud.contract.BackupDto
import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.shopmanager.data.backup.BackupManager
import com.archsolutions.shopmanager.data.backup.PendingRestore
import com.archsolutions.shopmanager.data.backup.RestoreResult
import com.archsolutions.shopmanager.data.cloud.CloudBackups
import com.archsolutions.shopmanager.data.cloud.CloudException
import com.archsolutions.shopmanager.data.cloud.CloudStore
import com.archsolutions.shopmanager.data.cloud.FakeTokenCipher
import com.archsolutions.shopmanager.data.cloud.cloudDataStoreFile
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

private val OLD = BackupDto("b1", backupTime = 1_000, sizeBytes = 2_000, uploadedAt = 1_100)
private val MID = BackupDto("b2", backupTime = 5_000, sizeBytes = 3_000, uploadedAt = 5_100)
private val NEW = BackupDto("b3", backupTime = 9_000, sizeBytes = 4_000, uploadedAt = 9_100)

private class FakeBackups(private val temp: File) : CloudBackups {
    var backups = listOf(OLD, NEW, MID)
    var listFailure: Throwable? = null
    var downloadFailure: Throwable? = null
    var restoreResult = RestoreResult.OK
    var downloaded: String? = null
    var restoredFrom: File? = null
    var tempExistedDuringRestore = false

    override fun tempFile() = temp

    override suspend fun list(): List<BackupDto> {
        listFailure?.let { throw it }
        return backups
    }

    override suspend fun download(id: String, dest: File) {
        dest.writeText("partial")
        downloadFailure?.let { throw it }
        downloaded = id
    }

    override suspend fun restore(file: File): RestoreResult {
        restoredFrom = file
        tempExistedDuringRestore = file.exists()
        return restoreResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CloudRestoreViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeBackups

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeBackups(File(tmp.root, "cloud-restore.zip"))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm() = CloudRestoreViewModel(fake, FakeClock()).also { runCurrent() }

    @Test fun `lists newest first`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(listOf(NEW, MID, OLD), vm.state.value.backups)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.error)
    }

    @Test fun `empty account lists nothing without an error`() = runTest(dispatcher) {
        fake.backups = emptyList()
        val vm = vm()
        assertTrue(vm.state.value.backups.isEmpty())
        assertFalse(vm.state.value.error)
    }

    @Test fun `a failed list shows error`() = runTest(dispatcher) {
        fake.listFailure = IOException("offline")
        val vm = vm()
        assertTrue(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `pick asks first and cancel drops it`() = runTest(dispatcher) {
        val vm = vm()
        vm.pick(MID)
        assertEquals(MID, vm.state.value.confirm)
        assertNull(fake.downloaded)
        vm.cancel()
        assertNull(vm.state.value.confirm)
    }

    @Test fun `a sign-out dismissal drops the pending confirm so the next person cannot finish the restore`() = runTest(dispatcher) {
        val vm = vm()
        vm.pick(MID)
        vm.cancel() // what the screen calls on sign-out
        vm.confirmRestore(); runCurrent()

        assertNull(vm.state.value.confirm)
        assertNull(fake.downloaded)
        assertNull(fake.restoredFrom)
    }

    @Test fun `confirm downloads and stages restore`() = runTest(dispatcher) {
        val vm = vm()
        vm.pick(MID)
        vm.confirmRestore(); runCurrent()

        assertEquals("b2", fake.downloaded)
        assertEquals(fake.tempFile(), fake.restoredFrom)
        assertTrue(fake.tempExistedDuringRestore)
        assertFalse(fake.tempFile().exists())
        assertEquals(RestoreResult.OK, vm.state.value.result)
        assertNull(vm.state.value.confirm)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.error)
    }

    @Test fun `too new shows the existing too-new message`() = runTest(dispatcher) {
        fake.restoreResult = RestoreResult.TOO_NEW
        val vm = vm()
        vm.pick(NEW); vm.confirmRestore(); runCurrent()

        assertEquals(RestoreResult.TOO_NEW, vm.state.value.result)
        assertFalse(fake.tempFile().exists())
    }

    @Test fun `not a backup shows the existing message and clears`() = runTest(dispatcher) {
        fake.restoreResult = RestoreResult.NOT_A_BACKUP
        val vm = vm()
        vm.pick(NEW); vm.confirmRestore(); runCurrent()
        assertEquals(RestoreResult.NOT_A_BACKUP, vm.state.value.result)

        vm.messageShown()
        assertNull(vm.state.value.result)
    }

    @Test fun `download failure shows error and keeps current data`() = runTest(dispatcher) {
        fake.downloadFailure = CloudException(ErrorCode.NOT_FOUND, 404, null, "gone")
        val vm = vm()
        vm.pick(NEW); vm.confirmRestore(); runCurrent()

        assertTrue(vm.state.value.error)
        assertNull(vm.state.value.result)
        assertNull(fake.restoredFrom) // nothing staged
        assertFalse(fake.tempFile().exists()) // the partial file is gone
        assertFalse(vm.state.value.loading)
    }

    @Test fun `tablet stays linked after a restore`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = AppDatabase.configure(Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)).allowMainThreadQueries().build()
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("s.preferences_pb") }))
        val manager = BackupManager(context, db, settings, FakeClock())
        val cloud = CloudStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { cloudDataStoreFile(context) }),
            FakeTokenCipher(),
        )
        try {
            cloud.saveLink("tok-123", "shop-1", "0700111222")
            val zip = File(tmp.root, "b.zip").also { f -> f.outputStream().use { manager.writeZip(it) } }
            assertEquals(RestoreResult.OK, manager.restoreFromFile(zip))

            db.close()
            PendingRestore.applyIfPending(context)

            // The token file is not in the backup zip, so the restore leaves the link alone.
            assertEquals("tok-123", cloud.token())
        } finally {
            db.close()
            context.deleteDatabase(AppDatabase.DATABASE_NAME)
        }
    }
}
