package com.archsolutions.shopmanager.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Quantities: whole numbers for pieces, up to 3 decimals for kg and litre ("0.5"), Latin digits. */
object Qty {
    private val fmt = DecimalFormat("0.###", DecimalFormatSymbols(Locale.US))

    fun format(qty: Double): String = fmt.format(qty)

    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
}
