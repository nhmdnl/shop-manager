package com.archsolutions.shopmanager.domain.staff

import androidx.annotation.StringRes
import com.archsolutions.shopmanager.R

/** The permission each PIN prompt guards. An unmapped purpose is a bug, so it throws. */
fun permissionFor(@StringRes purpose: Int): Permission = when (purpose) {
    R.string.pin_for_void -> Permission.VOID_SALE
    R.string.pin_for_stock -> Permission.CORRECT_STOCK
    R.string.pin_for_close, R.string.pin_for_reopen -> Permission.CLOSE_OR_REOPEN_DAY
    R.string.pin_for_settings, R.string.pin_for_restore, R.string.pin_for_cloud -> Permission.ADMIN_SETTINGS
    else -> error("No permission mapped for PIN purpose $purpose")
}
