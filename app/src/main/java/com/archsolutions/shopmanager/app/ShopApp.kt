package com.archsolutions.shopmanager.app

import android.app.Application
import com.archsolutions.shopmanager.data.backup.PendingRestore
import com.archsolutions.shopmanager.data.cloud.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class ShopApp : Application() {
    @Inject lateinit var syncScheduler: SyncScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // Before super.onCreate(): nothing has opened the database or the settings file yet.
        PendingRestore.applyIfPending(this)
        super.onCreate()
        syncScheduler.start(appScope)
    }
}
