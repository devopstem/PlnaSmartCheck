package com.plna.smartcheck.data.repository

import com.plna.smartcheck.data.health.ProductHealthAnalyzer
import com.plna.smartcheck.data.local.ExpenseDao
import com.plna.smartcheck.data.local.ExpenseEntity
import com.plna.smartcheck.data.local.PendingCloudDeleteDao
import com.plna.smartcheck.data.local.PendingCloudDeleteEntity
import com.plna.smartcheck.data.local.ProductJsonCodec
import com.plna.smartcheck.data.local.ReceiptMetaJsonCodec
import com.plna.smartcheck.data.model.ExpenseProduct
import com.plna.smartcheck.data.model.ParsedProduct
import com.plna.smartcheck.data.model.ParsedReceipt
import com.plna.smartcheck.data.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * CRUD чеков: Room сразу, облако в фоне / при синке.
 * Удаление: сначала телефон, потом Firestore (или очередь pending_cloud_deletes).
 */
class LocalExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val pendingCloudDeleteDao: PendingCloudDeleteDao,
    private val networkMonitor: NetworkMonitor,
    private val remoteRepository: ExpenseRepository,
) {

    fun observeExpenses(): Flow<List<ExpenseEntity>> = expenseDao.observeAll()

    fun observePendingCount(): Flow<Int> = expenseDao.observePendingCount()

    fun observeExpense(localId: String): Flow<ExpenseEntity?> = expenseDao.observeById(localId)

    suspend fun getExpense(localId: String): ExpenseEntity? = expenseDao.getById(localId)

    suspend fun getAllExpenses(): List<ExpenseEntity> = expenseDao.getAll()

    suspend fun saveReceipt(
        receipt: ParsedReceipt,
        storeName: String,
        rawText: String,
        source: String,
        date: Date = Date(),
    ): SaveResult {
        val products = receipt.items.map { it.toExpenseProductWithHealth() }
        val entity = ExpenseEntity(
            dateMillis = date.time,
            storeName = storeName.ifBlank { receipt.storeName.orEmpty() },
            totalAmount = receipt.totalAmount,
            productsJson = ProductJsonCodec.encode(products),
            metaJson = ReceiptMetaJsonCodec.encode(receipt.metaMap()),
            rawText = rawText,
            source = source,
            synced = false,
        )
        expenseDao.upsert(entity)
        return SaveResult(
            localId = entity.localId,
            synced = false,
            message = "Чек сохранён на телефоне. В облако отправим в фоне.",
        )
    }

    suspend fun updateExpense(
        localId: String,
        storeName: String,
        products: List<ExpenseProduct>,
    ) {
        val current = expenseDao.getById(localId) ?: return
        val withHealth = products.map { product ->
            if (product.healthTag.isBlank() || product.healthTag == "neutral") {
                product.copy(
                    healthTag = ProductHealthAnalyzer.classify(product.name, product.barcode).code,
                )
            } else {
                product
            }
        }
        val total = withHealth.sumOf { (it.lineTotal - it.discount).coerceAtLeast(0.0) }
        expenseDao.upsert(
            current.copy(
                storeName = storeName,
                productsJson = ProductJsonCodec.encode(withHealth),
                totalAmount = total,
                synced = false,
            ),
        )
    }

    suspend fun updateProductHealth(localId: String, productIndex: Int, healthTag: String) {
        val current = expenseDao.getById(localId) ?: return
        val products = ProductJsonCodec.decode(current.productsJson).toMutableList()
        if (productIndex !in products.indices) return
        products[productIndex] = products[productIndex].copy(healthTag = healthTag)
        expenseDao.upsert(
            current.copy(
                productsJson = ProductJsonCodec.encode(products),
                synced = false,
            ),
        )
    }

    /**
     * Удаляет чек с телефона и из облака (или ставит в очередь удаления).
     */
    suspend fun deleteExpense(localId: String): DeleteResult {
        val entity = expenseDao.getById(localId)
            ?: return DeleteResult(ok = false, message = "Чек уже удалён")

        expenseDao.delete(localId)

        val cloudId = entity.firestoreId
        if (cloudId.isNullOrBlank()) {
            return DeleteResult(ok = true, message = "Удалено с телефона")
        }

        if (networkMonitor.isOnline()) {
            return try {
                remoteRepository.deleteExpense(cloudId)
                pendingCloudDeleteDao.delete(cloudId)
                DeleteResult(ok = true, message = "Удалено с телефона и из облака")
            } catch (e: Exception) {
                pendingCloudDeleteDao.upsert(PendingCloudDeleteEntity(firestoreId = cloudId))
                DeleteResult(
                    ok = true,
                    message = "Удалено с телефона. Облако очистим при следующей синхронизации.",
                )
            }
        }

        pendingCloudDeleteDao.upsert(PendingCloudDeleteEntity(firestoreId = cloudId))
        return DeleteResult(
            ok = true,
            message = "Удалено с телефона. Из облака уйдёт, когда появится интернет.",
        )
    }

    suspend fun flushPendingCloudDeletes(): Int {
        if (!networkMonitor.isOnline()) return 0
        val pending = pendingCloudDeleteDao.getAll()
        var done = 0
        for (item in pending) {
            try {
                remoteRepository.deleteExpense(item.firestoreId)
                pendingCloudDeleteDao.delete(item.firestoreId)
                done++
            } catch (_: Exception) {
                // оставим в очереди
            }
        }
        return done
    }

    private fun ParsedProduct.toExpenseProductWithHealth(): ExpenseProduct {
        val tag = healthTag.takeIf { it.isNotBlank() && it != "neutral" }
            ?: ProductHealthAnalyzer.classify(name, barcode).code
        return ExpenseProduct(
            barcode = barcode,
            name = name,
            unitPrice = unitPrice,
            quantity = quantity,
            lineTotal = lineTotal,
            discount = discount,
            healthTag = tag,
        )
    }

    data class SaveResult(
        val localId: String,
        val synced: Boolean,
        val firestoreId: String? = null,
        val message: String,
    )

    data class DeleteResult(
        val ok: Boolean,
        val message: String,
    )
}
