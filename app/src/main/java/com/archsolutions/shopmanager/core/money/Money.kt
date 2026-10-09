package com.archsolutions.shopmanager.core.money

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US) // Latin digits + comma grouping in every language
    private val fmt = DecimalFormat("#,##0", symbols)

    fun format(amount: Long, withCode: Boolean = true): String =
        (if (withCode) "UGX " else "") + fmt.format(amount)

    fun signed(amount: Long): String =
        (if (amount >= 0) "+ " else "− ") + fmt.format(kotlin.math.abs(amount))

    fun short(amount: Long): String = when {
        amount >= 1_000_000 -> "%.2fM".format(Locale.US, amount / 1e6)
        amount >= 1_000 -> "${amount / 1000}k"
        else -> "$amount"
    } // charts only

    fun parse(text: String): Long? =
        text.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.toLongOrNull()
}
