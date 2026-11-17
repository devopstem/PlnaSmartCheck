package com.plna.smartcheck.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plna.smartcheck.data.model.ParsedProduct
import java.util.Locale

private val ReceiptPaper = Color(0xFFFFFDF7)
private val ReceiptInk = Color(0xFF1A1A1A)
private val ReceiptMuted = Color(0xFF5C5C5C)
private val ReceiptDash = Color(0xFFBDBDBD)

/**
 * Визуальный «бумажный» чек в стиле кассовой ленты (Гиппо / РБ).
 */
@Composable
fun ReceiptTicket(
    modifier: Modifier = Modifier,
    storeName: String,
    address: String?,
    unp: String?,
    rnSkko: String? = null,
    documentNumber: String?,
    dateTimeText: String?,
    items: List<ParsedProduct>,
    totalAmount: Double?,
    totalDiscount: Double?,
    paymentInfo: String?,
    receiptUid: String? = null,
    checkSearchCode: String? = null,
) {
    val moneyLocale = Locale("ru", "RU")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .background(ReceiptPaper, RoundedCornerShape(4.dp))
            .border(1.dp, ReceiptDash, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = storeName.ifBlank { "Чек покупки" },
                color = ReceiptInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )

            if (!address.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = address,
                    color = ReceiptMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                )
            }

            if (!unp.isNullOrBlank() || !rnSkko.isNullOrBlank() || !documentNumber.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                if (!unp.isNullOrBlank()) {
                    MonoLine("УНП $unp")
                }
                if (!rnSkko.isNullOrBlank()) {
                    MonoLine("РН СККО $rnSkko")
                }
                if (!documentNumber.isNullOrBlank()) {
                    MonoLine("N док. $documentNumber")
                }
            }

            DashedDivider()

            if (items.isEmpty()) {
                Text(
                    text = "Товары не распознаны.\nНажмите «Изменить».",
                    color = ReceiptMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                items.forEachIndexed { index, product ->
                    ReceiptItemBlock(index + 1, product, moneyLocale)
                    if (index != items.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            DashedDivider()

            MoneyRow(
                label = "ИТОГО СКИДКА",
                value = (totalDiscount ?: items.sumOf { it.discount }).formatMoney(moneyLocale),
                emphasize = false,
            )
            Spacer(Modifier.height(4.dp))
            MoneyRow(
                label = "ИТОГО К ОПЛАТЕ",
                value = (totalAmount ?: items.sumOf { it.netTotal }).formatMoney(moneyLocale),
                emphasize = true,
            )

            if (!paymentInfo.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                MonoLine(paymentInfo, muted = true)
            }

            DashedDivider()

            if (!dateTimeText.isNullOrBlank()) {
                MonoLine(dateTimeText)
            }
            if (!receiptUid.isNullOrBlank()) {
                MonoLine("УИ $receiptUid", muted = true)
            }
            if (!checkSearchCode.isNullOrBlank()) {
                MonoLine(checkSearchCode, muted = true)
            }
            Text(
                text = "PLNA Smart Check",
                color = ReceiptMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ReceiptItemBlock(
    number: Int,
    product: ParsedProduct,
    locale: Locale,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$number. ${product.name.ifBlank { "Товар" }}",
            color = ReceiptInk,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (product.barcode.isNotBlank()) {
            Text(
                text = product.barcode,
                color = ReceiptMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        MoneyRow(
            label = "${product.unitPrice.formatMoney(locale)} × ${product.quantity.formatQty(locale)}",
            value = product.lineTotal.formatMoney(locale),
            emphasize = false,
        )
        if (product.discount > 0) {
            MoneyRow(
                label = "Скидка",
                value = product.discount.formatMoney(locale),
                emphasize = false,
                muted = true,
            )
        }
    }
}

@Composable
private fun MoneyRow(
    label: String,
    value: String,
    emphasize: Boolean,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (muted) ReceiptMuted else ReceiptInk,
            fontFamily = FontFamily.Monospace,
            fontSize = if (emphasize) 13.sp else 11.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = if (muted) ReceiptMuted else ReceiptInk,
            fontFamily = FontFamily.Monospace,
            fontSize = if (emphasize) 14.sp else 12.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun MonoLine(text: String, muted: Boolean = false) {
    Text(
        text = text,
        color = if (muted) ReceiptMuted else ReceiptInk,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DashedDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = ReceiptDash,
        thickness = 1.dp,
    )
}

private fun Double.formatMoney(locale: Locale): String =
    String.format(locale, "%.2f", this)

private fun Double.formatQty(locale: Locale): String =
    if (this == this.toLong().toDouble()) {
        String.format(locale, "%.0f", this)
    } else {
        String.format(locale, "%.3f", this)
    }
