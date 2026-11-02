package com.archsolutions.shopmanager.app

import android.app.Application
import android.util.Log
import com.archsolutions.shopmanager.data.backup.PendingRestore
import com.archsolutions.shopmanager.data.cloud.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShopApp : Application() {
    // Lazy: the sync graph (OkHttp, stores) is built off the main thread, and a failure there can't stop the app opening.
    @Inject lateinit var syncScheduler: dagger.Lazy<SyncScheduler>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // Before super.onCreate(): nothing has opened the database or the settings file yet.
        PendingRestore.applyIfPending(this)
        super.onCreate()
        appScope.launch {
            try {
                syncScheduler.get().start(appScope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShopApp", "Could not start sync", e)
            }
        }
    }
}
