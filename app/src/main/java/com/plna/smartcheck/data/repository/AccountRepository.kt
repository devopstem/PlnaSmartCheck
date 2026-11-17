package com.plna.smartcheck.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.plna.smartcheck.data.local.ExpenseDao
import com.plna.smartcheck.data.local.ExpenseEntity
import com.plna.smartcheck.data.local.ProductJsonCodec
import com.plna.smartcheck.data.local.ReceiptMetaJsonCodec
import com.plna.smartcheck.data.local.UserSessionDao
import com.plna.smartcheck.data.local.UserSessionEntity
import com.plna.smartcheck.data.model.Expense
import com.plna.smartcheck.data.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date

/**
 * Регистрация / вход по 16-значному ID (без email и телефона).
 * Локально — Room, в облаке — Firebase Auth (служебный) + Firestore `accounts/{id}`.
 */
class AccountRepository(
    private val sessionDao: UserSessionDao,
    private val expenseDao: ExpenseDao,
    private val networkMonitor: NetworkMonitor,
    private val expenseRepository: ExpenseRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val sessionFlow: Flow<UserSessionEntity?> = sessionDao.observeLoggedIn()

    suspend fun getSession(): UserSessionEntity? = sessionDao.getLoggedIn()

    suspend fun register(displayNameInput: String?): UserSessionEntity {
        val accountId = generateAccountId()
        val displayName = displayNameInput?.trim().takeUnless { it.isNullOrBlank() }
            ?: randomUserName(accountId)

        val session = UserSessionEntity(
            accountId = accountId,
            displayName = displayName,
            languageCode = "ru",
            loggedIn = true,
            synced = false,
        )
        sessionDao.clearLoggedIn()
        sessionDao.upsert(session)
        // Облако — в фоне через syncPendingAccount(), UI не ждёт
        return session
    }

    /**
     * Быстрый вход: Auth + профиль. Чеки качайте отдельно [pullExpensesFromCloud].
     */
    suspend fun login(accountIdRaw: String): UserSessionEntity {
        val accountId = accountIdRaw.filter { it.isDigit() }
        require(accountId.length == 16) { "ID должен содержать 16 цифр" }

        if (!networkMonitor.isOnline()) {
            throw IllegalStateException("Для входа на другом устройстве нужен интернет")
        }

        val email = syntheticEmail(accountId)
        val password = accountId

        val authResult = try {
            auth.signInWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
            throw IllegalStateException("Аккаунт с таким ID не найден")
        }

        val uid = authResult.user?.uid
            ?: throw IllegalStateException("Не удалось войти")

        val remote = firestore.collection("accounts").document(accountId).get().await()
        val displayName = remote.getString("displayName")
            ?: randomUserName(accountId)
        val language = remote.getString("languageCode") ?: "ru"

        val session = UserSessionEntity(
            accountId = accountId,
            displayName = displayName,
            languageCode = language,
            firebaseUid = uid,
            synced = true,
            loggedIn = true,
        )
        sessionDao.clearLoggedIn()
        sessionDao.upsert(session)
        return session
    }

    suspend fun updateDisplayName(name: String): UserSessionEntity {
        val current = sessionDao.getLoggedIn()
            ?: throw IllegalStateException("Нет активной сессии")
        val updated = current.copy(
            displayName = name.trim().ifBlank { current.displayName },
            synced = false,
            updatedAtMillis = System.currentTimeMillis(),
        )
        sessionDao.upsert(updated)
        return updated
    }

    suspend fun updateLanguage(languageCode: String): UserSessionEntity? {
        val current = sessionDao.getLoggedIn() ?: return null
        val updated = current.copy(
            languageCode = languageCode,
            synced = false,
            updatedAtMillis = System.currentTimeMillis(),
        )
        sessionDao.upsert(updated)
        return updated
    }

    suspend fun logout() {
        sessionDao.clearLoggedIn()
        runCatching { auth.signOut() }
    }

    suspend fun syncPendingAccount(): UserSessionEntity? {
        val session = sessionDao.getLoggedIn() ?: return null
        if (session.synced || !networkMonitor.isOnline()) return session
        return runCatching { syncAccountToCloud(session, isNew = session.firebaseUid == null) }
            .onSuccess { sessionDao.upsert(it) }
            .getOrDefault(session)
    }

    /**
     * Скачивает чеки с облака на это устройство (вход с другого телефона).
     */
    suspend fun pullExpensesFromCloud(): Int {
        if (!networkMonitor.isOnline()) return 0
        val remote = expenseRepository.getExpenses()
        var imported = 0
        for (expense in remote) {
            val existing = expenseDao.findByFirestoreId(expense.id)
            if (existing != null) continue
            expenseDao.upsert(
                ExpenseEntity(
                    firestoreId = expense.id,
                    dateMillis = expense.date.time,
                    storeName = expense.storeName,
                    totalAmount = expense.totalAmount,
                    productsJson = ProductJsonCodec.encode(expense.products),
                    metaJson = ReceiptMetaJsonCodec.encode(expense.receiptMeta),
                    rawText = "",
                    source = "CLOUD",
                    synced = true,
                ),
            )
            imported++
        }
        return imported
    }

    private suspend fun syncAccountToCloud(
        session: UserSessionEntity,
        isNew: Boolean,
    ): UserSessionEntity {
        val email = syntheticEmail(session.accountId)
        val password = session.accountId

        val uid = if (isNew || auth.currentUser == null) {
            val result = try {
                if (isNew) {
                    auth.createUserWithEmailAndPassword(email, password).await()
                } else {
                    auth.signInWithEmailAndPassword(email, password).await()
                }
            } catch (_: Exception) {
                // Уже создан локально, пробуем войти
                auth.signInWithEmailAndPassword(email, password).await()
            }
            result.user?.uid ?: throw IllegalStateException("Firebase Auth недоступен")
        } else {
            auth.currentUser!!.uid
        }

        val payload = mapOf(
            "accountId" to session.accountId,
            "displayName" to session.displayName,
            "languageCode" to session.languageCode,
            "firebaseUid" to uid,
            "updatedAt" to Date(),
            "createdAt" to Date(),
        )
        firestore.collection("accounts").document(session.accountId)
            .set(payload, SetOptions.merge())
            .await()

        return session.copy(firebaseUid = uid, synced = true)
    }

    companion object {
        fun syntheticEmail(accountId: String): String =
            "$accountId@plna-smartcheck.app"

        fun generateAccountId(): String {
            val random = SecureRandom()
            return buildString(16) {
                // первая цифра 1–9, чтобы ID не выглядел «обрезанным»
                append(random.nextInt(9) + 1)
                repeat(15) { append(random.nextInt(10)) }
            }
        }

        fun randomUserName(accountId: String): String =
            "User_${accountId.takeLast(4)}"

        fun formatAccountId(accountId: String): String =
            accountId.chunked(4).joinToString(" ")
    }
}
