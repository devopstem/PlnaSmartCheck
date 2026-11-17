package com.plna.smartcheck.data.parser

import com.plna.smartcheck.data.model.ParsedProduct
import com.plna.smartcheck.data.model.ParsedReceipt
import java.util.Locale

/**
 * Парсер OCR/текста чека белорусского формата (Гиппо / БелВиллесден и т.п.).
 *
 * Пример позиции:
 * ```
 * 4810285013352 Напиток б/алк Пепси-кола сильногаз 2л пэт
 * 4.89 *1.000                   4.89
 * Скидка                       1.50
 * ```
 * Итоги: `ИТОГО К ОПЛАТЕ: 22.70`, `ИТОГО СКИДКА: 2.52`
 */
class ReceiptParser {

    fun parse(rawText: String): ParsedReceipt {
        val lines = normalizeOcr(rawText)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val items = mutableListOf<ParsedProduct>()
        var index = 0

        while (index < lines.size) {
            val barcodeMatch = BARCODE_LINE.find(lines[index])
            if (barcodeMatch == null) {
                index++
                continue
            }

            val barcode = barcodeMatch.groupValues[1]
            val name = barcodeMatch.groupValues[2].trim()
            index++

            val nameBuilder = StringBuilder(name)
            while (index < lines.size &&
                PRICE_LINE.find(lines[index]) == null &&
                DISCOUNT_LINE.find(lines[index]) == null &&
                BARCODE_LINE.find(lines[index]) == null &&
                !isFooterOrTotal(lines[index])
            ) {
                if (!SERVICE_LINE.containsMatchIn(lines[index])) {
                    nameBuilder.append(' ').append(lines[index])
                }
                index++
            }

            if (index >= lines.size) break

            val priceMatch = PRICE_LINE.find(lines[index])
            if (priceMatch == null) {
                continue
            }
            index++

            val unitPrice = priceMatch.groupValues[1].toMoney()
            val quantity = priceMatch.groupValues[2].toMoney()
            val lineTotal = priceMatch.groupValues[3].toMoney()

            var discount = 0.0
            if (index < lines.size) {
                val discountMatch = DISCOUNT_LINE.find(lines[index])
                if (discountMatch != null) {
                    discount = discountMatch.groupValues[1].toMoney().let { kotlin.math.abs(it) }
                    index++
                }
            }

            items += ParsedProduct(
                barcode = barcode,
                name = nameBuilder.toString().trim(),
                unitPrice = unitPrice,
                quantity = quantity,
                lineTotal = lineTotal,
                discount = discount,
            )
        }

        val joined = lines.joinToString("\n")

        val rawTotal = lines.firstNotNullOfOrNull { TOTAL_PAY_LINE.find(it)?.groupValues?.get(1)?.toMoney() }
            ?: lines.asReversed().firstNotNullOfOrNull { TOTAL_LINE.find(it)?.groupValues?.get(1)?.toMoney() }

        val totalDiscount = lines.firstNotNullOfOrNull {
            TOTAL_DISCOUNT_LINE.find(it)?.groupValues?.get(1)?.toMoney()
        }

        val storeName = lines.firstOrNull { HYPERMARKET_LINE.containsMatchIn(it) }
            ?: lines.firstOrNull { STORE_HINT.containsMatchIn(it) && BARCODE_LINE.find(it) == null }
            ?: lines.getOrNull(1)

        val address = lines.firstOrNull { ADDRESS_HINT.containsMatchIn(it) }
        val unp = UNP_LINE.find(joined)?.groupValues?.get(1)
        val rnSkko = RN_SKKO_LINE.find(joined)?.groupValues?.get(1)
        val documentNumber = DOC_LINE.find(joined)?.groupValues?.get(1)
        val dateTimeText = DATE_TIME_LINE.find(joined)?.groupValues?.get(0)
        val paymentInfo = lines.firstOrNull { PAYMENT_LINE.containsMatchIn(it) }
        val receiptUid = UID_LINE.find(joined)?.groupValues?.get(1)
        val cashRegister = CASH_REGISTER_LINE.find(joined)?.groupValues?.get(1)
        val shift = SHIFT_LINE.find(joined)?.groupValues?.get(1)
        val saleCheckNumber = SALE_CHECK_LINE.find(joined)?.groupValues?.get(1)
        val checkSearchCode = SEARCH_CODE_LINE.find(joined)?.groupValues?.get(1)

        return ParsedReceipt(
            items = items,
            storeName = storeName,
            address = address,
            unp = unp,
            rnSkko = rnSkko,
            documentNumber = documentNumber,
            rawTotal = rawTotal,
            totalDiscount = totalDiscount,
            paymentInfo = paymentInfo,
            dateTimeText = dateTimeText,
            receiptUid = receiptUid,
            cashRegister = cashRegister,
            shift = shift,
            saleCheckNumber = saleCheckNumber,
            checkSearchCode = checkSearchCode,
        )
    }

    /**
     * Нормализация типичных OCR-ошибок для русских чеков.
     */
    private fun normalizeOcr(raw: String): String {
        return raw
            .replace('\u00A0', ' ')
            .replace('٫', '.')
            .replace('，', ',')
            .replace(Regex("""(?<=\d),(?=\d)"""), ".") // 4,89 → 4.89
            .trim()
    }

    private fun isFooterOrTotal(line: String): Boolean =
        TOTAL_PAY_LINE.containsMatchIn(line) ||
            TOTAL_DISCOUNT_LINE.containsMatchIn(line) ||
            TOTAL_LINE.containsMatchIn(line) ||
            PAYMENT_LINE.containsMatchIn(line) ||
            line.startsWith("***") ||
            line.contains("Кассир", ignoreCase = true) ||
            UID_LINE.containsMatchIn(line)

    private fun String.toMoney(): Double {
        val normalized = trim()
            .replace(',', '.')
            .replace(" ", "")
            .replace("₽", "")
            .replace("BYN", "", ignoreCase = true)
        normalized.toDoubleOrNull()?.let { return it }
        return runCatching {
            java.text.NumberFormat.getInstance(Locale("ru", "RU"))
                .parse(trim())
                ?.toDouble()
        }.getOrNull() ?: 0.0
    }

    companion object {
        private val BARCODE_LINE = Regex("""^(\d{8,14})\s+(.+)$""")

        private val PRICE_LINE = Regex(
            """^([\d]+[.,][\d]+)\s*\*\s*([\d]+[.,][\d]+)\s+([\d]+[.,][\d]+)\.?$""",
        )

        private val DISCOUNT_LINE = Regex(
            """^скидка\s*:?\s*([\d]+[.,][\d]+)\.?$""",
            RegexOption.IGNORE_CASE,
        )

        private val TOTAL_PAY_LINE = Regex(
            """итого\s*к\s*оплате\s*:?\s*([\d]+[.,][\d]+)""",
            RegexOption.IGNORE_CASE,
        )

        private val TOTAL_DISCOUNT_LINE = Regex(
            """итого\s*скидка\s*:?\s*([\d]+[.,][\d]+)""",
            RegexOption.IGNORE_CASE,
        )

        private val TOTAL_LINE = Regex(
            """^(?:итого|всего|сумма)\s*:?\s*([\d]+[.,][\d]+)\.?$""",
            RegexOption.IGNORE_CASE,
        )

        private val UNP_LINE = Regex(
            """УНП\s*[№N#:]?\s*(\d{9})""",
            RegexOption.IGNORE_CASE,
        )

        private val RN_SKKO_LINE = Regex(
            """РН\s*СККО\s*:?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        /** `N док. 153811` / `№ док. 153811` */
        private val DOC_LINE = Regex(
            """(?:N|№)\s*док\.?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        private val DATE_TIME_LINE = Regex(
            """\d{2}\.\d{2}\.\d{4}\s+\d{2}:\d{2}:\d{2}""",
        )

        private val UID_LINE = Regex(
            """УИ\s+([A-F0-9]{16,})""",
            RegexOption.IGNORE_CASE,
        )

        private val CASH_REGISTER_LINE = Regex(
            """Касса\s*:?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        private val SHIFT_LINE = Regex(
            """Смена\s*:?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        private val SALE_CHECK_LINE = Regex(
            """Чек\s*продажи\s*:?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        /** `047.0743.171126.0241` */
        private val SEARCH_CODE_LINE = Regex(
            """(\d{3}\.\d{4}\.\d{6}\.\d{4})""",
        )

        private val HYPERMARKET_LINE = Regex(
            """гипермаркет|гиппо|белвиллесден""",
            RegexOption.IGNORE_CASE,
        )

        private val STORE_HINT = Regex(
            """магазин|супермаркет|гипермаркет|ооо|зао|ип|унитарн|предприят""",
            RegexOption.IGNORE_CASE,
        )

        private val ADDRESS_HINT = Regex(
            """г\.\s*минск|пр\.|проспект|ул\.|улица|тц\s""",
            RegexOption.IGNORE_CASE,
        )

        private val PAYMENT_LINE = Regex(
            """банк\.?\s*пл\.?\s*карт|наличн|оплат""",
            RegexOption.IGNORE_CASE,
        )

        private val SERVICE_LINE = Regex(
            """унп|рн\s*скко|кассир|смена|чек\s*#|qr\s|платежный\s*документ""",
            RegexOption.IGNORE_CASE,
        )
    }
}
