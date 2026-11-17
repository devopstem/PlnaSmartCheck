package com.plna.smartcheck.data.sync

import com.plna.smartcheck.data.local.ExpenseDao
import com.plna.smartcheck.data.local.ExpenseEntity
import com.plna.smartcheck.data.local.ProductJsonCodec
import com.plna.smartcheck.data.local.ReceiptMetaJsonCodec
import com.plna.smartcheck.data.model.Expense
import com.plna.smartcheck.data.network.NetworkMonitor
import com.plna.smartcheck.data.repository.AccountRepository
import com.plna.smartcheck.data.repository.ExpenseRepository
import com.plna.smartcheck.data.repository.LocalExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Каждую минуту: аккаунт + несинхронизированные чеки → Firestore.
 */
class ExpenseSyncManager(
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val networkMonitor: NetworkMonitor,
    private val accountRepository: AccountRepository,
    private val localExpenseRepository: LocalExpenseRepository,
) {
    private var job: Job? = null

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val online = networkMonitor.isOnline()
                _isOnline.value = online
                if (online) {
                    runCatching { syncAll() }
                        .onFailure { _lastSyncMessage.value = it.message }
                }
                delay(60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun syncNow(): Int {
        if (!networkMonitor.isOnline()) {
            _isOnline.value = false
            return 0
        }
        _isOnline.value = true
        return syncAll()
    }

    private suspend fun syncAll(): Int {
        _isSyncing.value = true
        return try {
            accountRepository.syncPendingAccount()
            localExpenseRepository.flushPendingCloudDeletes()
            syncPendingExpenses()
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun syncPendingExpenses(): Int {
        val pending = expenseDao.getPendingSync()
        var syncedCount = 0
        for (entity in pending) {
            val firestoreId = expenseRepository.uploadExpense(entity.toExpense())
            expenseDao.markSynced(localId = entity.localId, firestoreId = firestoreId)
            expenseRepository.enqueueNewProductsForModeration(
                ProductJsonCodec.decode(entity.productsJson).map {
                    com.plna.smartcheck.data.model.ParsedProduct(
                        barcode = it.barcode,
                        name = it.name,
                        unitPrice = it.unitPrice,
                        quantity = it.quantity,
                        lineTotal = it.lineTotal,
                        discount = it.discount,
                        healthTag = it.healthTag,
                    )
                },
            )
            syncedCount++
        }
        if (syncedCount > 0) {
            _lastSyncMessage.value = "Синхронизировано: $syncedCount"
        }
        return syncedCount
    }

    private fun ExpenseEntity.toExpense() = Expense(
        id = firestoreId.orEmpty(),
        date = Date(dateMillis),
        storeName = storeName,
        totalAmount = totalAmount,
        products = ProductJsonCodec.decode(productsJson),
        receiptMeta = ReceiptMetaJsonCodec.decode(metaJson),
    )
}
