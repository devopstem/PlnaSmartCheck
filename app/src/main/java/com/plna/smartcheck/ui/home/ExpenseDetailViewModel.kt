package com.plna.smartcheck.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plna.smartcheck.data.health.ProductHealth
import com.plna.smartcheck.data.health.ProductHealthAnalyzer
import com.plna.smartcheck.data.local.ExpenseEntity
import com.plna.smartcheck.data.local.ProductJsonCodec
import com.plna.smartcheck.data.model.ExpenseProduct
import com.plna.smartcheck.data.repository.LocalExpenseRepository
import com.plna.smartcheck.data.sync.ExpenseSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpenseDetailUiState(
    val expense: ExpenseEntity? = null,
    val products: List<ExpenseProduct> = emptyList(),
    val storeNameDraft: String = "",
    val isEditing: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
)

class ExpenseDetailViewModel(
    private val localId: String,
    private val localExpenseRepository: LocalExpenseRepository,
    private val syncManager: ExpenseSyncManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExpenseDetailUiState())

    val uiState: StateFlow<ExpenseDetailUiState> = combine(
        localExpenseRepository.observeExpense(localId),
        _ui,
    ) { entity, ui ->
        val products = entity?.let { ProductJsonCodec.decode(it.productsJson) }.orEmpty()
        ui.copy(
            expense = entity,
            products = products,
            storeNameDraft = if (ui.isEditing) ui.storeNameDraft else entity?.storeName.orEmpty(),
            deleted = entity == null && ui.deleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpenseDetailUiState(),
    )

    init {
        viewModelScope.launch {
            // дозаполнить health для старых чеков
            val entity = localExpenseRepository.getExpense(localId) ?: return@launch
            val products = ProductJsonCodec.decode(entity.productsJson)
            var changed = false
            val updated = products.map { p ->
                if (p.healthTag.isBlank() || p.healthTag == "neutral") {
                    val tag = ProductHealthAnalyzer.classify(p.name, p.barcode)
                    if (tag != ProductHealth.NEUTRAL) {
                        changed = true
                        p.copy(healthTag = tag.code)
                    } else {
                        p
                    }
                } else {
                    p
                }
            }
            if (changed) {
                localExpenseRepository.updateExpense(localId, entity.storeName, updated)
            }
        }
    }

    fun startEdit() {
        _ui.update {
            it.copy(isEditing = true, storeNameDraft = it.expense?.storeName.orEmpty())
        }
    }

    fun onStoreNameChange(value: String) {
        _ui.update { it.copy(storeNameDraft = value) }
    }

    fun saveEdits(products: List<ExpenseProduct>) {
        viewModelScope.launch {
            localExpenseRepository.updateExpense(
                localId = localId,
                storeName = _ui.value.storeNameDraft,
                products = products,
            )
            _ui.update { it.copy(isEditing = false, message = "Изменения сохранены") }
            syncManager.syncNow()
        }
    }

    fun cancelEdit() {
        _ui.update { it.copy(isEditing = false) }
    }

    fun cycleHealth(index: Int) {
        viewModelScope.launch {
            val products = uiState.value.products
            if (index !in products.indices) return@launch
            val current = ProductHealth.fromCode(products[index].healthTag)
            val next = when (current) {
                ProductHealth.NEUTRAL -> ProductHealth.HEALTHY
                ProductHealth.HEALTHY -> ProductHealth.HARMFUL
                ProductHealth.HARMFUL -> ProductHealth.NEUTRAL
            }
            localExpenseRepository.updateProductHealth(localId, index, next.code)
            syncManager.syncNow()
        }
    }

    fun deleteExpense() {
        viewModelScope.launch {
            val result = localExpenseRepository.deleteExpense(localId)
            _ui.update {
                it.copy(deleted = true, message = result.message)
            }
            runCatching { syncManager.syncNow() }
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }
}
