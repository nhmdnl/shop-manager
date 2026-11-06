package com.archsolutions.shopmanager.domain

/** Barcode spellings: a 12-digit UPC-A and its 13-digit EAN form (leading 0) are the same product. */
object Barcodes {
    /** Trimmed text; a 12-digit numeric code is padded to its 13-digit form. */
    fun key(code: String): String {
        val t = code.trim()
        return if (t.length == 12 && t.all { it in '0'..'9' }) "0$t" else t
    }

    /** Spellings to look up: the trimmed text, its 13-digit form, then the 12-digit form of a 0-led EAN. */
    fun candidates(code: String): List<String> {
        val t = code.trim()
        if (t.isEmpty()) return emptyList()
        val k = key(t)
        val upc = if (k.length == 13 && k.startsWith("0") && k.all { it in '0'..'9' }) k.drop(1) else null
        return listOfNotNull(t, k, upc).distinct()
    }
}
