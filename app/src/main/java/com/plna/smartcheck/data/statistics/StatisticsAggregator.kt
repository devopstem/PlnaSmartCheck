package com.plna.smartcheck.data.statistics

import com.plna.smartcheck.data.model.Expense
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

enum class StatsPeriod {
    WEEK,
    MONTH,
}

data class TopProduct(
    val barcode: String,
    val name: String,
    val purchaseCount: Int,
    val totalQuantity: Double,
    val totalSpent: Double,
)

data class PeriodBucket(
    /** Ключ периода: `2026-W12` или `2026-03` */
    val periodKey: String,
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalAmount: Double,
    val expenseCount: Int,
    val topProducts: List<TopProduct>,
)

data class StatisticsSummary(
    val period: StatsPeriod,
    val grandTotal: Double,
    val buckets: List<PeriodBucket>,
)

/**
 * Чистая логика агрегации расходов.
 * Работает одинаково с данными из Firestore или Room — на входе просто [List]<[Expense]>.
 */
object StatisticsAggregator {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val weekFields: WeekFields = WeekFields.of(Locale.getDefault())

    fun aggregate(
        expenses: List<Expense>,
        period: StatsPeriod,
        topN: Int = 3,
    ): StatisticsSummary {
        val grouped = expenses.groupBy { expense ->
            expense.date.toLocalDate().toPeriodKey(period)
        }

        val buckets = grouped
            .map { (key, group) ->
                val range = key.toDateRange(period)
                PeriodBucket(
                    periodKey = key,
                    label = key.toLabel(period),
                    startDate = range.first,
                    endDate = range.second,
                    totalAmount = group.sumOf { it.totalAmount },
                    expenseCount = group.size,
                    topProducts = topProducts(group, topN),
                )
            }
            .sortedByDescending { it.startDate }

        return StatisticsSummary(
            period = period,
            grandTotal = expenses.sumOf { it.totalAmount },
            buckets = buckets,
        )
    }

    /**
     * Топ-N самых покупаемых товаров:
     * сортировка по числу покупок (сколько раз позиция встретилась в чеках),
     * при равенстве — по суммарному количеству.
     */
    fun topProducts(expenses: List<Expense>, topN: Int = 3): List<TopProduct> {
        data class Acc(
            var name: String,
            var purchaseCount: Int = 0,
            var totalQuantity: Double = 0.0,
            var totalSpent: Double = 0.0,
        )

        val byBarcode = linkedMapOf<String, Acc>()

        for (expense in expenses) {
            for (product in expense.products) {
                val key = product.barcode.ifBlank { product.name.lowercase() }
                val acc = byBarcode.getOrPut(key) { Acc(name = product.name) }
                if (product.name.isNotBlank()) acc.name = product.name
                acc.purchaseCount += 1
                acc.totalQuantity += product.quantity
                acc.totalSpent += (product.lineTotal - product.discount).coerceAtLeast(0.0)
            }
        }

        return byBarcode.entries
            .map { (barcode, acc) ->
                TopProduct(
                    barcode = barcode,
                    name = acc.name,
                    purchaseCount = acc.purchaseCount,
                    totalQuantity = acc.totalQuantity,
                    totalSpent = acc.totalSpent,
                )
            }
            .sortedWith(
                compareByDescending<TopProduct> { it.purchaseCount }
                    .thenByDescending { it.totalQuantity }
                    .thenByDescending { it.totalSpent }
            )
            .take(topN)
    }

    private fun Date.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    private fun LocalDate.toPeriodKey(period: StatsPeriod): String = when (period) {
        StatsPeriod.WEEK -> {
            val week = get(weekFields.weekOfWeekBasedYear())
            val year = get(weekFields.weekBasedYear())
            "%04d-W%02d".format(year, week)
        }
        StatsPeriod.MONTH -> "%04d-%02d".format(year, monthValue)
    }

    private fun String.toDateRange(period: StatsPeriod): Pair<LocalDate, LocalDate> = when (period) {
        StatsPeriod.WEEK -> {
            val parts = split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            val start = LocalDate.now()
                .with(weekFields.weekBasedYear(), year.toLong())
                .with(weekFields.weekOfWeekBasedYear(), week.toLong())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            start to start.plusDays(6)
        }
        StatsPeriod.MONTH -> {
            val parts = split("-")
            val start = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
            start to start.with(TemporalAdjusters.lastDayOfMonth())
        }
    }

    private fun String.toLabel(period: StatsPeriod): String = when (period) {
        StatsPeriod.WEEK -> "Неделя $this"
        StatsPeriod.MONTH -> {
            val parts = split("-")
            val monthNames = listOf(
                "", "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
            )
            "${monthNames[parts[1].toInt()]} ${parts[0]}"
        }
    }
}
