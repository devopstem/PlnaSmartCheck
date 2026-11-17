package com.plna.smartcheck.data.model

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Одна позиция, извлечённая из сырого текста чека.
 */
data class ParsedProduct(
    val barcode: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Double,
    val lineTotal: Double,
    val discount: Double = 0.0,
    /** healthy | harmful | neutral */
    val healthTag: String = "neutral",
) {
    val netTotal: Double get() = (lineTotal - discount).coerceAtLeast(0.0)
}

/**
 * Результат парсинга всего чека (формат РБ / Гиппо и аналоги).
 *
 * Обязательные для приложения сейчас:
 * магазин, адрес, УНП, № документа, товары, итого/скидка, оплата, дата.
 *
 * Полезно сохранить «на потом»:
 * РН СККО, УИ, касса/смена/чек продажи, код быстрого поиска QR.
 */
data class ParsedReceipt(
    val items: List<ParsedProduct>,
    val storeName: String? = null,
    val address: String? = null,
    val unp: String? = null,
    val rnSkko: String? = null,
    val documentNumber: String? = null,
    val rawTotal: Double? = null,
    val totalDiscount: Double? = null,
    val paymentInfo: String? = null,
    val dateTimeText: String? = null,
    val receiptUid: String? = null,
    val cashRegister: String? = null,
    val shift: String? = null,
    val saleCheckNumber: String? = null,
    val checkSearchCode: String? = null,
) {
    val totalAmount: Double
        get() = rawTotal ?: items.sumOf { it.netTotal }

    val computedDiscount: Double
        get() = totalDiscount ?: items.sumOf { it.discount }

    fun metaMap(): Map<String, String> = buildMap {
        unp?.takeIf { it.isNotBlank() }?.let { put("unp", it) }
        rnSkko?.takeIf { it.isNotBlank() }?.let { put("rnSkko", it) }
        documentNumber?.takeIf { it.isNotBlank() }?.let { put("documentNumber", it) }
        address?.takeIf { it.isNotBlank() }?.let { put("address", it) }
        dateTimeText?.takeIf { it.isNotBlank() }?.let { put("dateTimeText", it) }
        paymentInfo?.takeIf { it.isNotBlank() }?.let { put("paymentInfo", it) }
        receiptUid?.takeIf { it.isNotBlank() }?.let { put("receiptUid", it) }
        cashRegister?.takeIf { it.isNotBlank() }?.let { put("cashRegister", it) }
        shift?.takeIf { it.isNotBlank() }?.let { put("shift", it) }
        saleCheckNumber?.takeIf { it.isNotBlank() }?.let { put("saleCheckNumber", it) }
        checkSearchCode?.takeIf { it.isNotBlank() }?.let { put("checkSearchCode", it) }
        totalDiscount?.let { put("totalDiscount", it.toString()) }
    }
}

/**
 * Товар внутри сохранённого расхода (Firestore document field).
 */
data class ExpenseProduct(
    val barcode: String = "",
    val name: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Double = 0.0,
    val lineTotal: Double = 0.0,
    val discount: Double = 0.0,
    /** healthy | harmful | neutral */
    val healthTag: String = "neutral",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "barcode" to barcode,
        "name" to name,
        "unitPrice" to unitPrice,
        "quantity" to quantity,
        "lineTotal" to lineTotal,
        "discount" to discount,
        "healthTag" to healthTag,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): ExpenseProduct = ExpenseProduct(
            barcode = map["barcode"] as? String ?: "",
            name = map["name"] as? String ?: "",
            unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
            quantity = (map["quantity"] as? Number)?.toDouble() ?: 0.0,
            lineTotal = (map["lineTotal"] as? Number)?.toDouble() ?: 0.0,
            discount = (map["discount"] as? Number)?.toDouble() ?: 0.0,
            healthTag = map["healthTag"] as? String ?: "neutral",
        )
    }
}

/**
 * Документ users/{uid}/expenses/{expenseId}
 */
data class Expense(
    val id: String = "",
    val date: Date = Date(),
    val storeName: String = "",
    val totalAmount: Double = 0.0,
    val products: List<ExpenseProduct> = emptyList(),
    val receiptMeta: Map<String, String> = emptyMap(),
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("date", Timestamp(date))
        put("storeName", storeName)
        put("totalAmount", totalAmount)
        put("products", products.map { it.toMap() })
        if (receiptMeta.isNotEmpty()) put("receiptMeta", receiptMeta)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromDocument(id: String, data: Map<String, Any?>): Expense {
            val productsRaw = data["products"] as? List<*> ?: emptyList<Any>()
            val products = productsRaw.mapNotNull { entry ->
                (entry as? Map<String, Any?>)?.let { ExpenseProduct.fromMap(it) }
            }
            val timestamp = data["date"] as? Timestamp
            val metaRaw = data["receiptMeta"] as? Map<*, *>
            val meta = metaRaw
                ?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v?.toString() ?: return@mapNotNull null
                    key to value
                }
                ?.toMap()
                .orEmpty()
            return Expense(
                id = id,
                date = timestamp?.toDate() ?: Date(),
                storeName = data["storeName"] as? String ?: "",
                totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                products = products,
                receiptMeta = meta,
            )
        }
    }
}

/**
 * Документ moderation_queue/{id}
 */
data class ModerationItem(
    val barcode: String,
    val recognizedName: String,
    val price: Double,
    val createdAt: Date = Date(),
    val status: String = "pending",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "barcode" to barcode,
        "recognizedName" to recognizedName,
        "price" to price,
        "createdAt" to Timestamp(createdAt),
        "status" to status,
    )
}
