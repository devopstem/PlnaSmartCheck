package com.plna.smartcheck.data.model

import java.time.LocalDateTime

/**
 * Результат разбора сырой строки из QR-кода чека.
 *
 * Типичный российский ФНС-payload (без списка товаров):
 * `t=20240315T143022&s=1234.56&fn=9287440300821824&i=16891&fp=2911959981&n=1`
 *
 * Список товаров обычно приходит только из JSON-ответа API
 * (или если в QR уже лежит JSON / расширенный формат).
 */
data class QrReceiptData(
    val rawPayload: String,
    val dateTime: LocalDateTime? = null,
    val totalAmount: Double? = null,
    /** ИНН магазина / продавца (если есть в QR или JSON). */
    val inn: String? = null,
    val fiscalDriveNumber: String? = null,
    val fiscalDocumentNumber: String? = null,
    val fiscalSign: String? = null,
    val operationType: Int? = null,
    /** Если QR — только ссылка. */
    val checkUrl: String? = null,
    val items: List<ParsedProduct> = emptyList(),
    /**
     * true → в QR нет товаров/полного состава, нужен GET к API
     * (см. [com.plna.smartcheck.data.api.ReceiptApiClient]).
     */
    val needsApiFetch: Boolean = false,
)

sealed class QrParseResult {
    data class Success(val data: QrReceiptData) : QrParseResult()
    data class Error(val message: String, val rawPayload: String) : QrParseResult()
}
