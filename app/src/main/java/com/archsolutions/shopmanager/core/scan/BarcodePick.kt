package com.archsolutions.shopmanager.core.scan

import kotlin.math.hypot

/** Centre of a barcode's bounding box in the upright (rotated) image. */
data class Centre(val x: Float, val y: Float)

/**
 * Index of the barcode to use when several are in view: the one whose box centre is closest to the image centre
 * (first wins a tie). Barcodes without a box only count when none has one (then the first). -1 when empty.
 * [imageW] and [imageH] are the upright image size, i.e. already swapped for a 90/270 degree rotation.
 */
fun pickCentralBarcode(centres: List<Centre?>, imageW: Int, imageH: Int): Int {
    if (centres.isEmpty()) return -1
    val cx = imageW / 2f
    val cy = imageH / 2f
    var best = -1
    var bestDistance = Float.MAX_VALUE
    centres.forEachIndexed { i, c ->
        if (c != null) {
            val d = hypot(c.x - cx, c.y - cy)
            if (d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
    }
    return if (best >= 0) best else 0
}
