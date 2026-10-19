package com.archsolutions.shopmanager.feature.reports

import com.archsolutions.shopmanager.data.db.dao.ReportItemRow
import com.archsolutions.shopmanager.data.db.dao.ReportSaleRow
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.domain.ClosedDayRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReportCsvTest {

    private val kampala = ZoneId.of("Africa/Kampala")

    @Test
    fun `sales csv lists each sale with its items, total and profit`() {
        val at = ZonedDateTime.of(2026, 9, 30, 14, 5, 0, 0, kampala).toInstant().toEpochMilli()
        val sales = listOf(
            ReportSaleRow(7, "2026-09-30", at, PayMethod.CREDIT, total = 12_000, costTotal = 9_500, customerName = "Joseph \"Joe\" K."),
        )
        val items = listOf(
            ReportItemRow(7, 1, "Sugar 1kg", qty = 2.0, unitPrice = 5_000),
            ReportItemRow(7, 2, "Beans, red", qty = 0.5, unitPrice = 4_000),
        )

        val lines = ReportCsv.sales(sales, items, kampala).removePrefix("﻿").split("\r\n")

        assertEquals("date,time,sale id,method,customer,items,total,profit", lines[0])
        // Commas and quotes are quoted the CSV way.
        assertEquals("2026-09-30,14:05,7,CREDIT,\"Joseph \"\"Joe\"\" K.\",\"Sugar 1kg × 2; Beans, red × 0.5\",12000,2500", lines[1])
        assertEquals("", lines[2]) // file ends with a line break
    }

    @Test
    fun `days csv puts the oldest day first and leaves unknown counts empty`() {
        fun day(id: Long, date: String, diffCash: Long?, reason: String?) = BusinessDayEntity(
            id = id, date = date, status = DayStatus.CLOSED, openedAt = 0L, openingCash = 150_000, openingMobile = 40_000,
            closedAt = 1L, expectedCash = 195_900, expectedMobile = 94_700, countedCash = 195_000, countedMobile = 94_700,
            diffCash = diffCash, diffMobile = 0, diffReason = reason,
        )
        val rows = listOf(
            ClosedDayRow(day(2, "2026-09-30", diffCash = -900, reason = "Change error"), sales = 198_500),
            ClosedDayRow(day(1, "2026-09-29", diffCash = null, reason = null), sales = 186_000),
        )

        val lines = ReportCsv.days(rows).removePrefix("﻿").split("\r\n")

        assertEquals(
            "date,sales,opening cash,opening momo,expected cash,expected momo,counted cash,counted momo,cash difference,momo difference,reason",
            lines[0],
        )
        assertEquals("2026-09-29,186000,150000,40000,195900,94700,195000,94700,,0,", lines[1])
        assertEquals("2026-09-30,198500,150000,40000,195900,94700,195000,94700,-900,0,Change error", lines[2])
    }
}
