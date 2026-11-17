package com.plna.smartcheck.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plna.smartcheck.data.health.ProductHealth
import com.plna.smartcheck.data.health.ProductHealthAnalyzer
import com.plna.smartcheck.data.local.toDomain
import com.plna.smartcheck.data.repository.LocalExpenseRepository
import com.plna.smartcheck.data.statistics.PeriodBucket
import com.plna.smartcheck.data.statistics.StatisticsAggregator
import com.plna.smartcheck.data.statistics.StatsPeriod
import com.plna.smartcheck.data.statistics.TopProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

data class HealthSpend(
    val healthy: Double = 0.0,
    val harmful: Double = 0.0,
    val neutral: Double = 0.0,
)

data class StatisticsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val grandTotal: Double = 0.0,
    val buckets: List<PeriodBucket> = emptyList(),
    val overallTopProducts: List<TopProduct> = emptyList(),
    val healthSpend: HealthSpend = HealthSpend(),
    val isEmpty: Boolean = true,
)

class StatisticsViewModel(
    localExpenseRepository: LocalExpenseRepository,
) : ViewModel() {

    private val periodFlow = MutableStateFlow(StatsPeriod.WEEK)

    val uiState: StateFlow<StatisticsUiState> = combine(
        localExpenseRepository.observeExpenses(),
        periodFlow,
    ) { entities, period ->
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val fromDate = when (period) {
            StatsPeriod.WEEK -> now.minusDays(6)
            StatsPeriod.MONTH -> now.withDayOfMonth(1)
        }
        val fromMillis = fromDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val filtered = entities.filter { it.dateMillis >= fromMillis }.map { it.toDomain() }
        val summary = StatisticsAggregator.aggregate(filtered, period, topN = 5)
        val top = StatisticsAggregator.topProducts(filtered, topN = 5)
        val health = computeHealthSpend(filtered)

        StatisticsUiState(
            period = period,
            grandTotal = summary.grandTotal,
            buckets = summary.buckets,
            overallTopProducts = top,
            healthSpend = health,
            isEmpty = filtered.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState(),
    )

    fun setPeriod(period: StatsPeriod) {
        periodFlow.update { period }
    }

    private fun computeHealthSpend(expenses: List<com.plna.smartcheck.data.model.Expense>): HealthSpend {
        var healthy = 0.0
        var harmful = 0.0
        var neutral = 0.0
        for (expense in expenses) {
            for (product in expense.products) {
                val spent = (product.lineTotal - product.discount).coerceAtLeast(0.0)
                val tag = ProductHealth.fromCode(product.healthTag).let { tag ->
                    if (tag == ProductHealth.NEUTRAL) {
                        ProductHealthAnalyzer.classify(product.name, product.barcode)
                    } else {
                        tag
                    }
                }
                when (tag) {
                    ProductHealth.HEALTHY -> healthy += spent
                    ProductHealth.HARMFUL -> harmful += spent
                    ProductHealth.NEUTRAL -> neutral += spent
                }
            }
        }
        return HealthSpend(healthy = healthy, harmful = harmful, neutral = neutral)
    }
}
