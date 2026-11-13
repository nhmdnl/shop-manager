package com.archsolutions.shopmanager.feature.backup

import com.archsolutions.shopmanager.feature.backup.BackupReminderViewModel.Companion.backupReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupReminderTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test fun `no reminder within 7 days of a backup`() {
        assertNull(backupReminder(lastBackupAt = now - 6 * day, lastCloudBackupAt = null, firstOpenedAt = 0L, now = now))
    }

    @Test fun `a file backup 10 days ago reminds`() {
        assertEquals(BackupReminder(days = 10, never = false), backupReminder(now - 10 * day, null, 0L, now))
    }

    @Test fun `a recent cloud backup counts, so an old file backup doesn't remind`() {
        assertNull(backupReminder(lastBackupAt = now - 10 * day, lastCloudBackupAt = now - day, firstOpenedAt = 0L, now = now))
        assertNull(backupReminder(lastBackupAt = null, lastCloudBackupAt = now - day, firstOpenedAt = 0L, now = now))
    }

    @Test fun `never backed up counts from the first day`() {
        assertEquals(BackupReminder(days = 8, never = true), backupReminder(null, null, now - 8 * day, now))
        assertNull(backupReminder(null, null, null, now))
    }
}
