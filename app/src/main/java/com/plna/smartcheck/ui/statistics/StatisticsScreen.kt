package com.plna.smartcheck.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plna.smartcheck.PlnaApplication
import com.plna.smartcheck.R
import com.plna.smartcheck.data.health.ProductHealth
import com.plna.smartcheck.data.health.ProductHealthAnalyzer
import com.plna.smartcheck.data.health.ProductHealthColors
import com.plna.smartcheck.data.statistics.StatsPeriod
import com.plna.smartcheck.data.statistics.TopProduct
import com.plna.smartcheck.ui.AppViewModelFactory
import com.plna.smartcheck.ui.components.HealthChip
import com.plna.smartcheck.ui.components.PlnaScreenTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val app = LocalContext.current.applicationContext as PlnaApplication
    val viewModel: StatisticsViewModel = viewModel(factory = AppViewModelFactory(app))
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            PlnaScreenTopBar(title = stringResource(R.string.stats_title))
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ui.period == StatsPeriod.WEEK,
                        onClick = { viewModel.setPeriod(StatsPeriod.WEEK) },
                        label = { Text(stringResource(R.string.stats_week)) },
                    )
                    FilterChip(
                        selected = ui.period == StatsPeriod.MONTH,
                        onClick = { viewModel.setPeriod(StatsPeriod.MONTH) },
                        label = { Text(stringResource(R.string.stats_month)) },
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.stats_spent),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "%.2f".format(ui.grandTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.stats_health_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                HealthBar(ui.healthSpend)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniHealthStat(
                        label = stringResource(R.string.health_healthy),
                        amount = ui.healthSpend.healthy,
                        color = ProductHealthColors.Healthy,
                    )
                    MiniHealthStat(
                        label = stringResource(R.string.health_harmful),
                        amount = ui.healthSpend.harmful,
                        color = ProductHealthColors.Harmful,
                    )
                    MiniHealthStat(
                        label = stringResource(R.string.health_neutral),
                        amount = ui.healthSpend.neutral,
                        color = ProductHealthColors.Neutral,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.stats_top_products),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (ui.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(ui.overallTopProducts) { product ->
                    TopProductCard(product)
                }
            }
        }
    }
}

@Composable
private fun HealthBar(spend: HealthSpend) {
    val total = (spend.healthy + spend.harmful + spend.neutral).coerceAtLeast(0.01)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Box(
            Modifier
                .weight((spend.healthy / total).toFloat().coerceAtLeast(0.001f))
                .fillMaxSize()
                .background(ProductHealthColors.Healthy),
        )
        Box(
            Modifier
                .weight((spend.harmful / total).toFloat().coerceAtLeast(0.001f))
                .fillMaxSize()
                .background(ProductHealthColors.Harmful),
        )
        Box(
            Modifier
                .weight((spend.neutral / total).toFloat().coerceAtLeast(0.001f))
                .fillMaxSize()
                .background(ProductHealthColors.Neutral),
        )
    }
}

@Composable
private fun MiniHealthStat(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(modifier = Modifier.width(100.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            text = "%.2f".format(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TopProductCard(product: TopProduct) {
    val health = ProductHealthAnalyzer.classify(product.name, product.barcode)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(
                        R.string.stats_product_meta,
                        product.purchaseCount,
                        "%.2f".format(product.totalSpent),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HealthChip(health = health)
        }
    }
}
