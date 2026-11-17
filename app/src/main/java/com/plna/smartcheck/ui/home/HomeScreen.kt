package com.plna.smartcheck.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plna.smartcheck.PlnaApplication
import com.plna.smartcheck.R
import com.plna.smartcheck.data.health.ProductHealth
import com.plna.smartcheck.data.health.ProductHealthAnalyzer
import com.plna.smartcheck.data.local.ExpenseEntity
import com.plna.smartcheck.data.local.ProductJsonCodec
import com.plna.smartcheck.ui.AppViewModelFactory
import com.plna.smartcheck.ui.components.HealthChip
import com.plna.smartcheck.ui.components.PlnaHomeTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenProfile: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    if (selectedId != null) {
        ExpenseDetailScreen(
            localId = selectedId!!,
            onBack = { selectedId = null },
            contentPadding = contentPadding,
        )
        return
    }

    val app = LocalContext.current.applicationContext as PlnaApplication
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory(app))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            PlnaHomeTopBar(onOpenProfile = onOpenProfile)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (uiState.isOnline) {
                                stringResource(R.string.online)
                            } else {
                                stringResource(R.string.offline)
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                if (uiState.pendingCount > 0) {
                    AssistChip(
                        onClick = viewModel::syncNow,
                        label = { Text("Ждут отправки: ${uiState.pendingCount}") },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::syncNow) {
                    Text("Синк")
                }
            }

            uiState.syncMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.expenses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.home_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(uiState.expenses, key = { it.localId }) { expense ->
                        ExpenseCard(
                            expense = expense,
                            onClick = { selectedId = expense.localId },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: ExpenseEntity,
    onClick: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val products = ProductJsonCodec.decode(expense.productsJson)
    val harmfulCount = products.count {
        ProductHealth.fromCode(it.healthTag).let { tag ->
            if (tag == ProductHealth.NEUTRAL) {
                ProductHealthAnalyzer.classify(it.name, it.barcode) == ProductHealth.HARMFUL
            } else {
                tag == ProductHealth.HARMFUL
            }
        }
    }
    val healthyCount = products.count {
        ProductHealth.fromCode(it.healthTag).let { tag ->
            if (tag == ProductHealth.NEUTRAL) {
                ProductHealthAnalyzer.classify(it.name, it.barcode) == ProductHealth.HEALTHY
            } else {
                tag == ProductHealth.HEALTHY
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = expense.storeName.ifBlank { stringResource(R.string.purchase_fallback) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%.2f".format(expense.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = buildString {
                append(dateFormat.format(Date(expense.dateMillis)))
                append(" · ")
                append(if (expense.source == "QR") "QR" else "Текст")
                append(" · ")
                append("${products.size} поз.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (healthyCount > 0) {
                HealthChip(ProductHealth.HEALTHY)
            }
            if (harmfulCount > 0) {
                HealthChip(ProductHealth.HARMFUL)
            }
        }
    }
}
