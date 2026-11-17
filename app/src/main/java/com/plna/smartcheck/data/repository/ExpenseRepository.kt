package com.plna.smartcheck.data.repository

import com.plna.smartcheck.data.model.Expense
import com.plna.smartcheck.data.model.ExpenseProduct
import com.plna.smartcheck.data.model.ModerationItem
import com.plna.smartcheck.data.model.ParsedProduct
import com.plna.smartcheck.data.model.ParsedReceipt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class ExpenseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Пользователь не авторизован")

    private fun expensesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("expenses")

    /**
     * Сохраняет покупку в users/{uid}/expenses/{expenseId}
     * и ставит новые товары в moderation_queue.
     *
     * @return id созданного expense-документа
     */
    suspend fun saveExpenseFromReceipt(
        receipt: ParsedReceipt,
        storeName: String,
        date: Date = Date(),
    ): String {
        val products = receipt.items.map { it.toExpenseProduct() }
        val expense = Expense(
            date = date,
            storeName = storeName.ifBlank { receipt.storeName.orEmpty() },
            totalAmount = receipt.totalAmount,
            products = products,
        )
        val id = uploadExpense(expense)
        enqueueNewProductsForModeration(receipt.items)
        return id
    }

    /** Загрузка уже собранного expense (например из Room sync). */
    suspend fun uploadExpense(expense: Expense): String {
        val uid = requireUid()
        val docRef = if (expense.id.isBlank()) {
            expensesCollection(uid).document()
        } else {
            expensesCollection(uid).document(expense.id)
        }
        docRef.set(expense.copy(id = docRef.id).toMap()).await()
        return docRef.id
    }

    /** Удаление чека из Firestore `users/{uid}/expenses/{id}`. */
    suspend fun deleteExpense(firestoreId: String) {
        if (firestoreId.isBlank()) return
        val uid = requireUid()
        expensesCollection(uid).document(firestoreId).delete().await()
    }

    /**
     * Добавляет товар в moderation_queue, если такого barcode ещё нет
     * (или нет записи со статусом pending/approved).
     */
    suspend fun enqueueNewProductsForModeration(products: List<ParsedProduct>) {
        val queue = firestore.collection("moderation_queue")

        for (product in products) {
            if (product.barcode.isBlank()) continue

            val existing = queue
                .whereEqualTo("barcode", product.barcode)
                .limit(1)
                .get()
                .await()

            if (!existing.isEmpty) continue

            val item = ModerationItem(
                barcode = product.barcode,
                recognizedName = product.name,
                price = product.unitPrice,
            )
            queue.add(item.toMap()).await()
        }
    }

    suspend fun getExpenses(from: Date? = null, to: Date? = null): List<Expense> {
        val uid = requireUid()
        var query: Query = expensesCollection(uid)

        if (from != null) {
            query = query.whereGreaterThanOrEqualTo("date", com.google.firebase.Timestamp(from))
        }
        if (to != null) {
            query = query.whereLessThanOrEqualTo("date", com.google.firebase.Timestamp(to))
        }

        val snapshot = query
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            Expense.fromDocument(doc.id, doc.data.orEmpty())
        }
    }

    private fun ParsedProduct.toExpenseProduct() = ExpenseProduct(
        barcode = barcode,
        name = name,
        unitPrice = unitPrice,
        quantity = quantity,
        lineTotal = lineTotal,
        discount = discount,
    )
}
