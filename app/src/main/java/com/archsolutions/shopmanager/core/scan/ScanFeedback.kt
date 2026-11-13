package com.archsolutions.shopmanager.core.scan

/** Beep/vibration for a scan result. */
interface ScanFeedback {
    fun success()
    fun failure()
}
