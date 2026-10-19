package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit

/** One product line from an import file (7.8). Amounts in whole UGX; stock may be fractional (kg). */
data class CsvProduct(
    val name: String,
    val category: String?,
    val unit: ProductUnit,
    val sellPrice: Long,
    val costPrice: Long,
    val stock: Double,
    val lowStockLevel: Double,
    val barcode: String?,
)

data class CsvRowError(val line: Int, val reason: CsvProblem)

enum class CsvProblem { NO_NAME, BAD_PRICE, BAD_NUMBER }

data class ParsedProducts(val products: List<CsvProduct>, val errors: List<CsvRowError>)

/**
 * Reads `name,category,unit,sell_price,cost_price,stock,low_stock_level,barcode` (7.8). A header row is
 * optional; quoted fields may hold commas ("5,000"); a bad row is reported and skipped, never guessed.
 */
object ProductCsv {
    fun parse(text: String): ParsedProducts {
        val products = mutableListOf<CsvProduct>()
        val errors = mutableListOf<CsvRowError>()
        text.removePrefix("﻿").lineSequence().forEachIndexed { index, raw ->
            val line = index + 1
            if (raw.isBlank()) return@forEachIndexed
            val cells = split(raw).map { it.trim() }
            if (line == 1 && cells.firstOrNull()?.equals("name", ignoreCase = true) == true) return@forEachIndexed
            fun cell(i: Int) = cells.getOrNull(i).orEmpty()
            val name = cell(0)
            if (name.isEmpty()) {
                errors += CsvRowError(line, CsvProblem.NO_NAME)
                return@forEachIndexed
            }
            val sell = money(cell(3))
            val cost = if (cell(4).isEmpty()) 0L else money(cell(4))
            if (sell == null || cost == null) {
                errors += CsvRowError(line, CsvProblem.BAD_PRICE)
                return@forEachIndexed
            }
            val stock = number(cell(5))
            val low = number(cell(6))
            if (stock == null || low == null) {
                errors += CsvRowError(line, CsvProblem.BAD_NUMBER)
                return@forEachIndexed
            }
            products += CsvProduct(
                name = name,
                category = cell(1).ifEmpty { null },
                unit = unit(cell(2)),
                sellPrice = sell,
                costPrice = cost,
                stock = stock,
                lowStockLevel = low,
                barcode = cell(7).ifEmpty { null },
            )
        }
        return ParsedProducts(products, errors)
    }

    private fun money(text: String): Long? = text.replace(",", "").replace(" ", "").toLongOrNull()?.takeIf { it >= 0 }

    /** Empty means 0; "2.5" and "2,5" both mean two and a half. */
    private fun number(text: String): Double? =
        if (text.isEmpty()) 0.0 else text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

    private fun unit(text: String): ProductUnit = when (text.lowercase()) {
        "kg", "kgs", "kilo", "kilogram" -> ProductUnit.KG
        "l", "litre", "liter", "litres", "liters" -> ProductUnit.LITRE
        "pack", "packs", "pkt" -> ProductUnit.PACK
        else -> ProductUnit.PCS
    }

    /** Splits one CSV line: commas separate, double quotes group, "" inside quotes is a quote. */
    private fun split(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }
}
