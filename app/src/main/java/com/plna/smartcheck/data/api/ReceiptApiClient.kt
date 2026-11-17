package com.plna.smartcheck.data.api

import com.plna.smartcheck.data.model.QrReceiptData
import com.plna.smartcheck.data.parser.QrReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Пример GET-запроса, когда QR содержит только ссылку (или ФНС-параметры без товаров).
 *
 * Важно:
 * - Официальный API ФНС «Проверка чека» требует авторизации / партнёрского доступа.
 * - Ниже — шаблон под ваш backend-прокси или публичный агрегатор.
 * - Не храните секреты в клиенте: лучше проксировать через свой Cloud Function.
 *
 * Пример ответа (упрощённо):
 * ```json
 * {
 *   "code": 0,
 *   "data": {
 *     "json": {
 *       "userInn": "7707083893",
 *       "dateTime": "2024-03-15T14:30:22",
 *       "totalSum": 123456,
 *       "items": [
 *         { "name": "Пепси 2л", "price": 489, "quantity": 1.0, "sum": 489, "barcode": "4810285013352" }
 *       ]
 *     }
 *   }
 * }
 * ```
 */
class ReceiptApiClient(
    private val client: OkHttpClient = defaultClient(),
    private val parser: QrReceiptParser = QrReceiptParser(),
    /**
     * Базовый URL вашего прокси, например:
     * `https://your-cloud-function.example/receipt`
     * Если null — используется [QrReceiptData.checkUrl] как есть.
     */
    private val proxyBaseUrl: String? = null,
) {

    sealed class Result {
        data class Success(val data: QrReceiptData) : Result()
        data class HttpError(val code: Int, val body: String) : Result()
        data class NetworkError(val message: String) : Result()
    }

    /**
     * GET по URL из QR или через прокси с фискальными параметрами.
     */
    suspend fun fetchReceipt(qr: QrReceiptData): Result = withContext(Dispatchers.IO) {
        val url = buildRequestUrl(qr)
            ?: return@withContext Result.NetworkError("Нет URL для запроса чека")

        val request = Request.Builder()
            .url(url)
            .get()
            // Пример заголовков для прокси/ФНС — подставьте свои:
            // .header("Authorization", "Bearer <token>")
            // .header("Device-Id", "<device>")
            // .header("User-Agent", "PlnaSmartCheck/1.0")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.HttpError(response.code, body)
                }
                val parsed = parser.parseJsonReceipt(body, rawPayload = qr.rawPayload)
                Result.Success(
                    parsed.copy(
                        checkUrl = qr.checkUrl ?: url,
                        fiscalDriveNumber = parsed.fiscalDriveNumber ?: qr.fiscalDriveNumber,
                        fiscalDocumentNumber = parsed.fiscalDocumentNumber ?: qr.fiscalDocumentNumber,
                        fiscalSign = parsed.fiscalSign ?: qr.fiscalSign,
                        inn = parsed.inn ?: qr.inn,
                        totalAmount = parsed.totalAmount ?: qr.totalAmount,
                        dateTime = parsed.dateTime ?: qr.dateTime,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.NetworkError(e.message ?: "Сетевая ошибка")
        }
    }

    /**
     * Пример сборки URL для прокси:
     * `GET https://proxy/receipt?t=...&s=...&fn=...&i=...&fp=...&n=...`
     */
    private fun buildRequestUrl(qr: QrReceiptData): String? {
        if (!proxyBaseUrl.isNullOrBlank()) {
            val params = buildList {
                qr.dateTime?.let {
                    val t = "%04d%02d%02dT%02d%02d%02d".format(
                        it.year, it.monthValue, it.dayOfMonth,
                        it.hour, it.minute, it.second,
                    )
                    add("t=$t")
                }
                qr.totalAmount?.let { add("s=${"%.2f".format(it)}") }
                qr.fiscalDriveNumber?.let { add("fn=$it") }
                qr.fiscalDocumentNumber?.let { add("i=$it") }
                qr.fiscalSign?.let { add("fp=$it") }
                qr.operationType?.let { add("n=$it") }
                qr.inn?.let { add("inn=$it") }
            }
            val base = proxyBaseUrl.trimEnd('?', '&')
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }

        return qr.checkUrl
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
