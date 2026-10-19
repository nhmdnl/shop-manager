package com.archsolutions.shopmanager.app

import android.app.Application
import com.archsolutions.shopmanager.data.backup.PendingRestore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ShopApp : Application() {
    override fun onCreate() {
        // Before super.onCreate(): nothing has opened the database or the settings file yet.
        PendingRestore.applyIfPending(this)
        super.onCreate()
    }
}
