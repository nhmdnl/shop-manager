package com.archsolutions.shopmanager.core.scan

/** One decoded barcode: its text and the format name the decoder reported (may be empty). */
data class ScannedCode(val text: String, val format: String = "")
