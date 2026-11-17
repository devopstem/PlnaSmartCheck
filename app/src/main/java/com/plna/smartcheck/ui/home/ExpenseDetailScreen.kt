package com.plna.smartcheck.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plna.smartcheck.PlnaApplication
import com.plna.smartcheck.R
import com.plna.smartcheck.data.health.ProductHealth
import com.plna.smartcheck.data.model.ExpenseProduct
import com.plna.smartcheck.ui.components.HealthChip
import com.plna.smartcheck.ui.components.PlnaScreenTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    localId: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val app = LocalContext.current.applicationContext as PlnaApplication
    val viewModel: ExpenseDetailViewModel = viewModel(
        key = localId,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExpenseDetailViewModel(
                    localId = localId,
                    localExpenseRepository = app.container.localExpenseRepository,
                    syncManager = app.container.syncManager,
                ) as T
            }
        },
    )
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var editProducts by remember { mutableStateOf<List<ExpenseProduct>>(emptyList()) }

    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    LaunchedEffect(ui.isEditing, ui.products) {
        if (ui.isEditing) editProducts = ui.products
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            PlnaScreenTopBar(
                title = stringResource(R.string.receipt_detail_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        val expense = ui.expense
        if (expense == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(24.dp),
            ) {
                Text(stringResource(R.string.receipt_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (ui.isEditing) {
                OutlinedTextField(
                    value = ui.storeNameDraft,
                    onValueChange = viewModel::onStoreNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_store)) },
                    singleLine = true,
                )
            } else {
                Text(
                    text = expense.storeName.ifBlank { stringResource(R.string.purchase_fallback) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
            Text(
                text = dateFormat.format(Date(expense.dateMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.receipt_total, "%.2f".format(expense.totalAmount)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(R.string.health_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            val products = if (ui.isEditing) editProducts else ui.products
            products.forEachIndexed { index, product ->
                ProductRow(
                    product = product,
                    editing = ui.isEditing,
                    onNameChange = { name ->
                        editProducts = editProducts.toMutableList().also {
                            it[index] = it[index].copy(name = name)
                        }
                    },
                    onHealthClick = { viewModel.cycleHealth(index) },
                )
                HorizontalDivider()
            }

            Spacer(Modifier.height(8.dp))

            if (ui.isEditing) {
                Button(
                    onClick = { viewModel.saveEdits(editProducts) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(
                    onClick = viewModel::cancelEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            } else {
                FilledTonalButton(
                    onClick = viewModel::startEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_edit_receipt))
                }
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.action_delete_receipt))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteExpense()
                    },
                ) {
                    Text(stringResource(R.string.action_delete_receipt))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun ProductRow(
    product: ExpenseProduct,
    editing: Boolean,
    onNameChange: (String) -> Unit,
    onHealthClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (editing) {
            OutlinedTextField(
                value = product.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_product)) },
                singleLine = true,
            )
        } else {
            Text(
                text = product.name.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%.2f × %.3g = %.2f".format(
                    product.unitPrice,
                    product.quantity,
                    (product.lineTotal - product.discount).coerceAtLeast(0.0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HealthChip(
                health = ProductHealth.fromCode(product.healthTag),
                onClick = onHealthClick,
            )
        }
    }
}
