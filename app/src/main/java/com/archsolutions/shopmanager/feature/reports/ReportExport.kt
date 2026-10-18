package com.archsolutions.shopmanager.feature.reports

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.domain.PaidBy
import java.io.File
import java.time.ZoneId
import java.util.Locale

/** "Share PDF / CSV" (section 10): files go to cache/exports and leave through the FileProvider. */
internal object ReportExport {
    enum class Kind(val mimeType: String) { PDF("application/pdf"), CSV("text/csv") }

    fun write(context: Context, kind: Kind, state: ReportsUiState, zoneId: ZoneId, nowMillis: Long): List<File> {
        val report = state.report ?: return emptyList()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val range = "${report.period.from}_${report.period.to}"
        return when (kind) {
            Kind.CSV -> listOf(
                File(dir, "sales_$range.csv").apply { writeText(ReportCsv.sales(state.sales, state.items, zoneId)) },
                File(dir, "days_$range.csv").apply { writeText(ReportCsv.days(state.closedDays)) },
            )
            Kind.PDF -> listOf(File(dir, "report_$range.pdf").also { ReportPdf(context).write(it, state, zoneId, nowMillis) })
        }
    }

    fun share(context: Context, files: List<File>, kind: Kind) {
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = kind.mimeType
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant for every file to whichever app is picked.
        send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        context.startActivity(Intent.createChooser(send, null))
    }
}

/**
 * The A4 report (section 10): title, period, stats, paid-by split, top products, daily totals and closed
 * days, with 40 pt margins and new pages as needed. DM Sans / Bricolage are downloadable fonts in this
 * app (not in res/font), so the PDF uses the system sans-serif.
 */
private class ReportPdf(private val context: Context) {
    private val document = PdfDocument()
    private lateinit var page: PdfDocument.Page
    private var pageNumber = 0
    private var y = 0f

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        this.color = color
    }

    private val body = paint(10f)
    private val bodyBold = paint(10f, bold = true)
    private val label = paint(8.5f, bold = true, color = MUTED)
    private val line = Paint().apply { color = LINE; strokeWidth = 0.7f }

    fun write(file: File, state: ReportsUiState, zoneId: ZoneId, nowMillis: Long) {
        val report = state.report ?: return
        val locale: Locale = context.resources.configuration.locales[0]
        newPage()

        text(context.getString(R.string.pdf_title, state.shopName), paint(20f, bold = true), lineHeight = 26f)
        text(DayFormat.range(report.period.from, report.period.to, locale), paint(11f, color = MUTED), lineHeight = 26f)

        val stats = listOf(
            R.string.report_sales_ugx to Money.format(report.stats.sales, withCode = false),
            R.string.report_profit_ugx to Money.format(report.stats.profit, withCode = false),
            R.string.report_number_of_sales to report.stats.count.toString(),
            R.string.report_average_ugx to Money.format(report.stats.average, withCode = false),
        )
        ensure(44f)
        val column = CONTENT_WIDTH / stats.size
        stats.forEachIndexed { index, (labelRes, value) ->
            val x = MARGIN + index * column
            page.canvas.drawText(context.getString(labelRes), x, y + 10f, paint(9f, color = MUTED))
            page.canvas.drawText(value, x, y + 30f, paint(16f, bold = true, color = if (labelRes == R.string.report_profit_ugx) GREEN else INK))
        }
        y += 44f

        heading(R.string.paid_by)
        paidBy(report.paidBy)

        heading(R.string.top_products)
        table(
            header = listOf("#", context.getString(R.string.col_product), context.getString(R.string.pdf_col_sold), context.getString(R.string.pdf_col_amount)),
            widths = listOf(24f, 311f, 80f, 100f),
            endAligned = setOf(2, 3),
            rows = report.top.mapIndexed { index, product ->
                listOf("${index + 1}", product.name, Qty.format(product.qty), Money.format(product.amount, withCode = false))
            },
        )

        heading(R.string.pdf_daily_totals)
        table(
            header = listOf(context.getString(R.string.pdf_col_date), context.getString(R.string.label_sales)),
            widths = listOf(315f, 200f),
            endAligned = setOf(1),
            rows = report.daily.map { listOf(DayFormat.date(it.date.toString(), "EEE d MMM yyyy", locale), Money.format(it.total, withCode = false)) },
        )

        heading(R.string.pdf_closed_days)
        table(
            header = listOf(
                context.getString(R.string.pdf_col_date),
                context.getString(R.string.label_sales),
                context.getString(R.string.pdf_col_cash_diff),
                context.getString(R.string.pdf_col_momo_diff),
                context.getString(R.string.label_reason),
            ),
            widths = listOf(95f, 85f, 95f, 95f, 145f),
            endAligned = setOf(1, 2, 3),
            rows = state.closedDays.map { (day, sales) ->
                listOf(
                    DayFormat.date(day.date, "EEE d MMM", locale),
                    Money.format(sales, withCode = false),
                    difference(day.diffCash),
                    difference(day.diffMobile),
                    day.diffReason.orEmpty(),
                )
            },
        )

        y += 16f
        val made = DayFormat.dateOf(nowMillis, zoneId, "d MMM yyyy", locale) + " " + DayFormat.time(nowMillis, zoneId)
        text(context.getString(R.string.pdf_made_on, made), paint(8.5f, color = MUTED), lineHeight = 14f)

        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun difference(amount: Long?): String = when (amount) {
        null -> ""
        0L -> "0"
        else -> Money.signed(amount)
    }

    private fun newPage() {
        if (pageNumber > 0) document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        y = MARGIN
    }

    private fun ensure(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN) newPage()
    }

    private fun text(value: String, textPaint: TextPaint, lineHeight: Float) {
        ensure(lineHeight)
        val fitted = TextUtils.ellipsize(value, textPaint, CONTENT_WIDTH, TextUtils.TruncateAt.END).toString()
        page.canvas.drawText(fitted, MARGIN, y + textPaint.textSize, textPaint)
        y += lineHeight
    }

    private fun heading(titleRes: Int) {
        ensure(44f)
        y += 14f
        page.canvas.drawText(context.getString(titleRes).uppercase(), MARGIN, y + 9f, label)
        y += 16f
    }

    private fun paidBy(paidBy: PaidBy) {
        ensure(40f)
        val parts = listOf(
            Triple(R.string.label_cash, paidBy.cash, GREEN),
            Triple(R.string.label_mobile_money, paidBy.mobile, ORANGE),
            Triple(R.string.label_credit, paidBy.credit, RUST),
        )
        var x = MARGIN
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        if (paidBy.total > 0L) {
            parts.forEach { (_, amount, color) ->
                val width = CONTENT_WIDTH * amount / paidBy.total
                fill.color = color
                page.canvas.drawRect(x, y, x + width, y + 10f, fill)
                x += width
            }
        } else {
            fill.color = LINE
            page.canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 10f, fill)
        }
        y += 14f
        val percentages = paidBy.percentages()
        val legend = parts.mapIndexed { index, (labelRes, amount, _) ->
            "${context.getString(labelRes)} ${percentages[index]}% (${Money.format(amount, withCode = false)})"
        }.joinToString("   ·   ")
        text(legend, body, lineHeight = 18f)
    }

    private fun table(header: List<String>, widths: List<Float>, endAligned: Set<Int>, rows: List<List<String>>) {
        row(header, widths, endAligned, label)
        if (rows.isEmpty()) {
            text(context.getString(R.string.empty_reports), paint(10f, color = MUTED), lineHeight = 18f)
        }
        rows.forEach { row(it, widths, endAligned, body) }
    }

    private fun row(cells: List<String>, widths: List<Float>, endAligned: Set<Int>, textPaint: TextPaint) {
        ensure(ROW_HEIGHT)
        var x = MARGIN
        cells.forEachIndexed { index, cell ->
            val width = widths[index]
            // A gap after right-aligned columns, or "MoMo difference" runs into "Reason".
            val gap = if (index in endAligned && index < cells.lastIndex) COLUMN_GAP else 0f
            val fitted = TextUtils.ellipsize(cell, textPaint, width - gap - 6f, TextUtils.TruncateAt.END).toString()
            val start = if (index in endAligned) x + width - gap - textPaint.measureText(fitted) else x
            page.canvas.drawText(fitted, start, y + 12f, textPaint)
            x += width
        }
        y += ROW_HEIGHT
        page.canvas.drawLine(MARGIN, y - 2f, MARGIN + CONTENT_WIDTH, y - 2f, line)
    }

    private companion object {
        // A4 portrait in PostScript points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val ROW_HEIGHT = 18f
        const val COLUMN_GAP = 12f
        // Light palette from the design system.
        const val INK = 0xFF1B1A17.toInt()
        const val MUTED = 0xFF5E5A52.toInt()
        const val LINE = 0xFFE4E0D6.toInt()
        const val GREEN = 0xFF1E6B47.toInt()
        const val ORANGE = 0xFFD98B3A.toInt()
        const val RUST = 0xFF9A3412.toInt()
    }
}
