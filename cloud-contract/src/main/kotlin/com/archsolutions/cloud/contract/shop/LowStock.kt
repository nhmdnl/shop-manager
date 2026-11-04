package com.archsolutions.cloud.contract.shop

object LowStock {
    fun isLow(stockQty: Double, lowStockLevel: Double): Boolean = stockQty <= lowStockLevel
}
