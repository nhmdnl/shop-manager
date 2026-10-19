package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductCsvTest {

    @Test
    fun `reads the 7_8 columns with a header, quotes and kilos`() {
        val text = """
            name,category,unit,sell_price,cost_price,stock,low_stock_level,barcode
            Sugar 1kg,Food,pcs,5000,4200,34,15,6001234
            "Beans, red",Food,kg,"4,000",3000,"12,5",5,
            Soap bar,,,2500,,40,12,
        """.trimIndent()

        val parsed = ProductCsv.parse(text)

        assertEquals(emptyList<CsvRowError>(), parsed.errors)
        assertEquals(
            listOf(
                CsvProduct("Sugar 1kg", "Food", ProductUnit.PCS, 5_000, 4_200, 34.0, 15.0, "6001234"),
                CsvProduct("Beans, red", "Food", ProductUnit.KG, 4_000, 3_000, 12.5, 5.0, null),
                CsvProduct("Soap bar", null, ProductUnit.PCS, 2_500, 0, 40.0, 12.0, null),
            ),
            parsed.products,
        )
    }

    @Test
    fun `bad rows are reported with their line and skipped`() {
        val text = "﻿Rice,Food,pcs,4500,3800,50,10,\n,Food,pcs,100,50,1,1,\nMilk,Dairy,pcs,free,1100,3,10,\nEggs,Food,pcs,13000,11000,-2,8,\n"

        val parsed = ProductCsv.parse(text)

        assertEquals(listOf("Rice"), parsed.products.map { it.name })
        assertEquals(
            listOf(CsvRowError(2, CsvProblem.NO_NAME), CsvRowError(3, CsvProblem.BAD_PRICE), CsvRowError(4, CsvProblem.BAD_NUMBER)),
            parsed.errors,
        )
    }
}
