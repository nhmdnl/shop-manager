package com.archsolutions.shopmanager.domain.staff

enum class Role(val key: String) {
    OWNER("OWNER"),
    CASHIER("CASHIER"),
    ;

    companion object {
        fun fromKey(key: String): Role? = entries.firstOrNull { it.key == key }
    }
}

enum class Permission { SELL, VOID_SALE, CLOSE_OR_REOPEN_DAY, CORRECT_STOCK, ADMIN_SETTINGS, MANAGE_STAFF }

/** A null or unknown role can do nothing; the Owner can do everything; the Cashier can only sell. */
fun Role?.can(p: Permission): Boolean = when (this) {
    Role.OWNER -> true
    Role.CASHIER -> p == Permission.SELL
    null -> false
}
