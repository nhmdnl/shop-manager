package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.core.money.Money

/** Translated format strings for the close-day summary; the UI fills them from strings.xml. */
data class SummaryTemplates(
    val title: String, // "%1$s — %2$s"
    val sales: String, // "Sales: %1$s (Cash %2$s · MoMo %3$s · Credit %4$s)"
    val costs: String, // "Expenses: %1$s · Purchases: %2$s"
    val channel: String, // "%1$s: expected %2$s, counted %3$s (%4$s)"
    val short: String, // "short %1$s"
    val over: String, // "over %1$s"
    val balanced: String, // "balanced"
    val withReason: String, // "%1$s — %2$s"
    val profit: String, // "Profit (est.): %1$s"
    val cashLabel: String,
    val mobileLabel: String,
)

/** The WhatsApp / share text after closing the day (spec 6.6). */
object DaySummary {
    fun build(
        templates: SummaryTemplates,
        shopName: String,
        dateText: String,
        balance: DayBalance,
        countedCash: Long,
        countedMobile: Long,
        allowedDifference: Long,
        reason: String?,
    ): String {
        fun channelLine(label: String, expected: Long, counted: Long): String {
            val difference = counted - expected
            val status = when (BalanceCalculator.status(difference, allowedDifference)) {
                BalanceStatus.BALANCED -> templates.balanced
                BalanceStatus.SHORT -> templates.short.format(Money.format(-difference, withCode = false))
                BalanceStatus.OVER -> templates.over.format(Money.format(difference, withCode = false))
            }
            val withReason = if (status != templates.balanced && !reason.isNullOrBlank()) {
                templates.withReason.format(status, reason)
            } else {
                status
            }
            return templates.channel.format(
                label,
                Money.format(expected, withCode = false),
                Money.format(counted, withCode = false),
                withReason,
            )
        }

        return listOf(
            templates.title.format(shopName, dateText),
            templates.sales.format(
                Money.format(balance.totalSales),
                Money.format(balance.cash.sales, withCode = false),
                Money.format(balance.mobile.sales, withCode = false),
                Money.format(balance.creditSales, withCode = false),
            ),
            templates.costs.format(Money.format(balance.expensesTotal), Money.format(balance.purchasesTotal)),
            channelLine(templates.cashLabel, balance.cash.expected, countedCash),
            channelLine(templates.mobileLabel, balance.mobile.expected, countedMobile),
            templates.profit.format(Money.format(balance.profit)),
        ).joinToString("\n")
    }
}
