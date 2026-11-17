package com.plna.smartcheck.data.parser

import com.plna.smartcheck.data.model.ParsedProduct
import com.plna.smartcheck.data.model.QrParseResult
import com.plna.smartcheck.data.model.QrReceiptData
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Парсер сырой строки из QR-кода кассового чека.
 *
 * Поддерживает:
 * 1. ФНС query-string: `t=...&s=...&fn=...&i=...&fp=...&n=...` (+ опционально `inn=`)
 * 2. URL с теми же параметрами в query
 * 3. «Голый» URL без фискальных полей → [QrReceiptData.needsApiFetch] = true
 * 4. JSON-тело чека (если QR/ответ уже содержит товары)
 *
 * Когда пришлёшь реальный пример строки — донастроим под точный формат
 * (РБ / РФ / конкретная сеть).
 */
class QrReceiptParser {

    fun parse(raw: String): QrParseResult {
        val payload = raw.trim()
        if (payload.isEmpty()) {
            return QrParseResult.Error("Пустая строка QR", payload)
        }

        return try {
            when {
                payload.startsWith("{") -> QrParseResult.Success(parseJsonReceipt(payload, payload))
                looksLikeUrl(payload) -> QrParseResult.Success(parseUrl(payload))
                looksLikeFiscalQuery(payload) -> QrParseResult.Success(parseFiscalQuery(payload, checkUrl = null))
                else -> QrParseResult.Error(
                    "Неизвестный формат QR. Пришлите пример строки — расширим парсер.",
                    payload,
                )
            }
        } catch (e: Exception) {
            QrParseResult.Error(e.message ?: "Ошибка разбора QR", payload)
        }
    }

    private fun parseUrl(url: String): QrReceiptData {
        val uri = URI(url)
        val query = uri.rawQuery.orEmpty()

        if (looksLikeFiscalQuery(query) || looksLikeFiscalQuery(url.substringAfter('?', missingDelimiterValue = ""))) {
            val q = query.ifBlank { url.substringAfter('?', missingDelimiterValue = "") }
            return parseFiscalQuery(q, checkUrl = url)
        }

        // Только ссылка — дата/сумма/товары придут из API
        return QrReceiptData(
            rawPayload = url,
            checkUrl = url,
            needsApiFetch = true,
        )
    }

    private fun parseFiscalQuery(query: String, checkUrl: String?): QrReceiptData {
        val params = parseQueryParams(query)

        val dateTime = params["t"]?.let { parseFiscalDateTime(it) }
        val total = params["s"]?.replace(',', '.')?.toDoubleOrNull()
        val inn = params["inn"]?.takeIf { it.isNotBlank() }
            ?: params["userInn"]?.takeIf { it.isNotBlank() }

        // В классическом ФНС QR списка товаров нет
        return QrReceiptData(
            rawPayload = query,
            dateTime = dateTime,
            totalAmount = total,
            inn = inn,
            fiscalDriveNumber = params["fn"],
            fiscalDocumentNumber = params["i"],
            fiscalSign = params["fp"],
            operationType = params["n"]?.toIntOrNull(),
            checkUrl = checkUrl,
            items = emptyList(),
            needsApiFetch = true,
        )
    }

    /**
     * Универсальный разбор JSON чека.
     * Ожидаемые варианты ключей (ФНС / агрегаторы / РБ):
     * - userInn / inn / sellerInn
     * - dateTime / date / ticketDate
     * - totalSum / sum / total (сумма в рублях ИЛИ в копейках)
     * - items / products / ticketItems
     */
    fun parseJsonReceipt(jsonText: String, rawPayload: String = jsonText): QrReceiptData {
        val root = JSONObject(jsonText)
        val data = unwrapDataNode(root)

        val inn = firstString(data, "userInn", "inn", "sellerInn", "unp")
        val total = parseMoney(data, "totalSum", "sum", "total", "totalAmount")
        val dateTime = firstString(data, "dateTime", "date", "ticketDate", "saleDateTime")
            ?.let { parseFlexibleDateTime(it) }

        val itemsArray = firstArray(data, "items", "products", "ticketItems", "positions")
        val items = buildList {
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.optJSONObject(i) ?: continue
                    add(parseProduct(obj))
                }
            }
        }

        return QrReceiptData(
            rawPayload = rawPayload,
            dateTime = dateTime,
            totalAmount = total,
            inn = inn,
            fiscalDriveNumber = firstString(data, "fn", "fiscalDriveNumber"),
            fiscalDocumentNumber = firstString(data, "fd", "fiscalDocumentNumber", "i")
                ?: data.opt("fiscalDocumentNumber")?.toString(),
            fiscalSign = firstString(data, "fp", "fiscalSign"),
            items = items,
            needsApiFetch = false,
        )
    }

    private fun parseProduct(obj: JSONObject): ParsedProduct {
        val name = firstString(obj, "name", "title", "productName").orEmpty()
        val barcode = firstString(obj, "barcode", "ean", "gtin", "productCode", "code").orEmpty()
        val quantity = obj.optDouble("quantity", obj.optDouble("qty", 1.0))
        val unitPrice = parseMoney(obj, "price", "unitPrice") ?: 0.0
        val lineTotal = parseMoney(obj, "sum", "total", "lineTotal")
            ?: (unitPrice * quantity)

        return ParsedProduct(
            barcode = barcode,
            name = name,
            unitPrice = if (unitPrice > 0) unitPrice else if (quantity != 0.0) lineTotal / quantity else lineTotal,
            quantity = quantity,
            lineTotal = lineTotal,
        )
    }

    private fun unwrapDataNode(root: JSONObject): JSONObject {
        // proverkacheka / некоторые прокси: { "code":0, "data": { "json": { ... } } }
        val data = root.opt("data")
        if (data is JSONObject) {
            val inner = data.opt("json")
            if (inner is JSONObject) return inner
            return data
        }
        val ticket = root.optJSONObject("ticket")
        if (ticket != null) return ticket
        return root
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val normalized = query.removePrefix("?").trim()
        if (normalized.isEmpty()) return emptyMap()

        return normalized.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = pair.substring(0, idx).trim().lowercase()
                val value = pair.substring(idx + 1).trim()
                key to value
            }
            .toMap()
    }

    private fun looksLikeUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun looksLikeFiscalQuery(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("t=") && (lower.contains("s=") || lower.contains("fn="))
    }

    private fun parseFiscalDateTime(raw: String): LocalDateTime? {
        val candidates = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )
        for (fmt in candidates) {
            try {
                return LocalDateTime.parse(raw, fmt)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun parseFlexibleDateTime(raw: String): LocalDateTime? {
        parseFiscalDateTime(raw)?.let { return it }
        val iso = raw.replace(' ', 'T')
        return try {
            LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Если значение похоже на копейки (целое без точки и > 1000 при наличии items) —
     * делим на 100. Для явных рублей (`1234.56`) оставляем как есть.
     */
    private fun parseMoney(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val raw = obj.get(key)
            when (raw) {
                is Number -> {
                    val d = raw.toDouble()
                    // ФНС часто отдаёт сумму в копейках целым числом
                    return if (raw is Int || (raw is Long && !raw.toString().contains('.'))) {
                        d / 100.0
                    } else {
                        d
                    }
                }
                is String -> {
                    val normalized = raw.replace(',', '.')
                    val d = normalized.toDoubleOrNull() ?: continue
                    return if (!normalized.contains('.') && d >= 100) d / 100.0 else d
                }
            }
        }
        return null
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty() && value != "null") return value
        }
        return null
    }

    private fun firstArray(obj: JSONObject, vararg keys: String): JSONArray? {
        for (key in keys) {
            val arr = obj.optJSONArray(key)
            if (arr != null) return arr
        }
        return null
    }
}
