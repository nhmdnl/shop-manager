package com.archsolutions.shopmanager.core.util

import android.content.Context
import android.content.Intent

/** Starts the app afresh in a new process, e.g. after a restore was staged. */
object AppRestart {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }
}
