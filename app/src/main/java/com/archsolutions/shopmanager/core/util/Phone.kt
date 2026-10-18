package com.archsolutions.shopmanager.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the dialer with the number filled in; needs no call permission. */
object Phone {
    fun dial(context: Context, number: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } catch (e: ActivityNotFoundException) {
            // Wi-Fi-only tablets may have no dialer; the number stays on screen.
        }
    }
}
