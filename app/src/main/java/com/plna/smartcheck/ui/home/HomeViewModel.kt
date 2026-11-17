package com.plna.smartcheck.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plna.smartcheck.data.local.ExpenseEntity
import com.plna.smartcheck.data.repository.LocalExpenseRepository
import com.plna.smartcheck.data.sync.ExpenseSyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val pendingCount: Int = 0,
    val isOnline: Boolean = false,
    val syncMessage: String? = null,
)

class HomeViewModel(
    localExpenseRepository: LocalExpenseRepository,
    private val syncManager: ExpenseSyncManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        localExpenseRepository.observeExpenses(),
        localExpenseRepository.observePendingCount(),
        syncManager.isOnline,
        syncManager.lastSyncMessage,
    ) { expenses, pending, online, message ->
        HomeUiState(
            expenses = expenses,
            pendingCount = pending,
            isOnline = online,
            syncMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun syncNow() {
        viewModelScope.launch {
            syncManager.syncNow()
        }
    }
}
