package com.archsolutions.shopmanager.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodePickTest {
    @Test fun `none gives minus one`() = assertEquals(-1, pickCentralBarcode(emptyList(), 720, 1280))

    @Test fun `the barcode nearest the image centre wins`() {
        val centres = listOf(Centre(50f, 60f), Centre(380f, 700f), Centre(600f, 1200f))
        assertEquals(1, pickCentralBarcode(centres, 720, 1280))
    }

    @Test fun `a tie goes to the first`() {
        val centres = listOf(Centre(300f, 640f), Centre(420f, 640f))
        assertEquals(0, pickCentralBarcode(centres, 720, 1280))
    }

    @Test fun `barcodes without a box are skipped when another has one`() {
        assertEquals(2, pickCentralBarcode(listOf(null, null, Centre(10f, 10f)), 720, 1280))
    }

    @Test fun `when none has a box the first is used`() {
        assertEquals(0, pickCentralBarcode(listOf(null, null), 720, 1280))
    }

    @Test fun `the upright size decides, not the sensor size`() {
        // The two sizes give different winners for these points, so a wrong rotation swap fails this test.
        val centres = listOf(Centre(360f, 1000f), Centre(700f, 400f))
        // Upright centre (360, 640): d0 = 360, d1 = hypot(340, 240) = 416 -> index 0.
        assertEquals(0, pickCentralBarcode(centres, 720, 1280))
        // Swapped centre (640, 360): d0 = hypot(280, 640) = 698, d1 = hypot(60, 40) = 72 -> index 1.
        assertEquals(1, pickCentralBarcode(centres, 1280, 720))
    }
}
