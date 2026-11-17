package com.plna.smartcheck.ui.smartcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plna.smartcheck.data.model.ParsedReceipt
import com.plna.smartcheck.data.parser.ReceiptParser
import com.plna.smartcheck.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class SmartCheckUiState(
    val isLoading: Boolean = false,
    val parsedReceipt: ParsedReceipt? = null,
    val savedExpenseId: String? = null,
    val errorMessage: String? = null,
)

/**
 * Берёт сырой текст чека → парсит → сохраняет expense в Firestore
 * и отправляет новые товары в moderation_queue.
 */
class SmartCheckViewModel(
    private val receiptParser: ReceiptParser = ReceiptParser(),
    private val expenseRepository: ExpenseRepository = ExpenseRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartCheckUiState())
    val uiState: StateFlow<SmartCheckUiState> = _uiState.asStateFlow()

    /** Только парсинг — для превью перед сохранением. */
    fun parseReceipt(rawText: String) {
        runCatching { receiptParser.parse(rawText) }
            .onSuccess { parsed ->
                _uiState.update {
                    it.copy(parsedReceipt = parsed, errorMessage = null, savedExpenseId = null)
                }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Ошибка парсинга", parsedReceipt = null)
                }
            }
    }

    /**
     * Парсит чек (если ещё не распарсен) и сохраняет покупку:
     * - users/{uid}/expenses/{expenseId}
     * - moderation_queue для новых barcode
     */
    fun savePurchase(
        rawText: String,
        storeName: String,
        date: Date = Date(),
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, savedExpenseId = null) }
            try {
                val parsed = if (rawText.isNotBlank()) {
                    receiptParser.parse(rawText)
                } else {
                    _uiState.value.parsedReceipt
                        ?: throw IllegalArgumentException("Нет текста чека для сохранения")
                }

                if (parsed.items.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            parsedReceipt = parsed,
                            errorMessage = "В тексте чека не найдено ни одной позиции",
                        )
                    }
                    return@launch
                }

                val expenseId = expenseRepository.saveExpenseFromReceipt(
                    receipt = parsed,
                    storeName = storeName,
                    date = date,
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        parsedReceipt = parsed,
                        savedExpenseId = expenseId,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Не удалось сохранить покупку",
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
