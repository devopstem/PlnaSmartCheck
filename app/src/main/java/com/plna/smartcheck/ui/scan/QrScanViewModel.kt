package com.plna.smartcheck.ui.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plna.smartcheck.data.api.ReceiptApiClient
import com.plna.smartcheck.data.local.DraftTextStore
import com.plna.smartcheck.data.model.ParsedProduct
import com.plna.smartcheck.data.model.ParsedReceipt
import com.plna.smartcheck.data.model.QrParseResult
import com.plna.smartcheck.data.model.QrReceiptData
import com.plna.smartcheck.data.parser.QrReceiptParser
import com.plna.smartcheck.data.parser.ReceiptParser
import com.plna.smartcheck.data.repository.LocalExpenseRepository
import com.plna.smartcheck.data.scan.RussianOcrEngine
import com.plna.smartcheck.data.sync.ExpenseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CaptureMode {
    CHOOSER,
    QR,
    OCR,
    REVIEW,
    EDIT,
}

data class QrScanUiState(
    val mode: CaptureMode = CaptureMode.CHOOSER,
    val source: String = "",
    val isScanning: Boolean = true,
    val isOcrProcessing: Boolean = false,
    val ocrCaptureNonce: Int = 0,
    val isLoadingDetails: Boolean = false,
    val isSaving: Boolean = false,
    val rawPayload: String = "",
    val storeName: String = "",
    val address: String = "",
    val unp: String = "",
    val rnSkko: String = "",
    val documentNumber: String = "",
    val dateTimeText: String = "",
    val paymentInfo: String = "",
    val receiptUid: String = "",
    val cashRegister: String = "",
    val shift: String = "",
    val saleCheckNumber: String = "",
    val checkSearchCode: String = "",
    val items: List<ParsedProduct> = emptyList(),
    val totalAmount: Double? = null,
    val totalDiscount: Double? = null,
    val qrMeta: QrReceiptData? = null,
    val ocrStatus: String? = null,
    val saveMessage: String? = null,
    val errorMessage: String? = null,
)

class QrScanViewModel(
    application: Application,
    private val localExpenseRepository: LocalExpenseRepository,
    private val draftTextStore: DraftTextStore,
    private val syncManager: ExpenseSyncManager,
    private val qrParser: QrReceiptParser = QrReceiptParser(),
    private val textParser: ReceiptParser = ReceiptParser(),
    private val apiClient: ReceiptApiClient = ReceiptApiClient(),
    private val ocrEngine: RussianOcrEngine = RussianOcrEngine(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(QrScanUiState())
    val uiState: StateFlow<QrScanUiState> = _uiState.asStateFlow()

    fun selectMode(mode: CaptureMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                source = when (mode) {
                    CaptureMode.QR -> "QR"
                    CaptureMode.OCR -> "TEXT"
                    else -> it.source
                },
                isScanning = mode == CaptureMode.QR || mode == CaptureMode.OCR,
                isOcrProcessing = false,
                errorMessage = null,
                saveMessage = null,
                rawPayload = "",
                items = emptyList(),
                totalAmount = null,
                totalDiscount = null,
                address = "",
                unp = "",
                rnSkko = "",
                documentNumber = "",
                dateTimeText = "",
                paymentInfo = "",
                receiptUid = "",
                cashRegister = "",
                shift = "",
                saleCheckNumber = "",
                checkSearchCode = "",
                qrMeta = null,
            )
        }
    }

    fun backToChooser() {
        _uiState.value = QrScanUiState(storeName = _uiState.value.storeName)
    }

    fun onGalleryImagePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = CaptureMode.OCR,
                    source = "GALLERY",
                    isScanning = false,
                    isOcrProcessing = true,
                    errorMessage = null,
                    ocrStatus = "Читаем фото из галереи…",
                )
            }
            val decoded = withContext(Dispatchers.IO) {
                runCatching { decodeBitmap(uri) }
            }
            val bitmap = decoded.getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isOcrProcessing = false,
                        ocrStatus = null,
                        mode = CaptureMode.CHOOSER,
                        errorMessage = e.message ?: "Не удалось открыть фото",
                    )
                }
                return@launch
            }
            onOcrBitmapCaptured(bitmap)
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        val resolver = getApplication<Application>().contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    fun onStoreNameChange(value: String) {
        _uiState.update { it.copy(storeName = value) }
    }

    fun onRawPayloadChange(value: String) {
        _uiState.update { it.copy(rawPayload = value) }
        viewModelScope.launch { draftTextStore.save(value) }
    }

    fun requestOcrCapture() {
        _uiState.update {
            it.copy(
                isOcrProcessing = true,
                errorMessage = null,
                ocrStatus = "Делаем снимок…",
                ocrCaptureNonce = it.ocrCaptureNonce + 1,
            )
        }
    }

    fun onOcrBitmapCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    ocrStatus = if (ocrEngine.isLanguagePackReady()) {
                        "Русский OCR уже на телефоне…"
                    } else {
                        "Первый запуск: скачиваем русский язык (потом сохранится)…"
                    },
                )
            }
            val scaled = scaleForOcr(bitmap)
            if (scaled !== bitmap) bitmap.recycle()

            ocrEngine.ensureReady { state ->
                when (state) {
                    RussianOcrEngine.PrepareState.AlreadyCached -> {
                        _uiState.update {
                            it.copy(ocrStatus = "Язык уже сохранён — скачивать не нужно")
                        }
                    }
                    is RussianOcrEngine.PrepareState.Downloading -> {
                        _uiState.update {
                            it.copy(ocrStatus = "Скачиваем языковую модель ${state.fileName}…")
                        }
                    }
                    RussianOcrEngine.PrepareState.Ready -> {
                        _uiState.update { it.copy(ocrStatus = "Распознаём русский текст…") }
                    }
                }
            }

            val result = ocrEngine.recognize(scaled)
            scaled.recycle()

            result
                .onSuccess { text -> onOcrTextRecognized(text) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isOcrProcessing = false,
                            ocrStatus = null,
                            errorMessage = e.message ?: "Ошибка русского OCR",
                        )
                    }
                }
        }
    }

    fun onOcrCaptureFailed(message: String) {
        _uiState.update {
            it.copy(
                isOcrProcessing = false,
                ocrStatus = null,
                errorMessage = message,
            )
        }
    }

    private fun onOcrTextRecognized(text: String) {
        val parsed = textParser.parse(text)
        viewModelScope.launch { draftTextStore.save(text) }
        _uiState.update {
            it.applyParsed(parsed).copy(
                mode = CaptureMode.REVIEW,
                isScanning = false,
                isOcrProcessing = false,
                ocrStatus = null,
                rawPayload = text,
                source = if (it.source == "GALLERY") "GALLERY" else "TEXT",
                errorMessage = if (parsed.items.isEmpty()) {
                    "Текст распознан, но позиции не найдены — нажмите «Изменить»"
                } else {
                    null
                },
            )
        }
    }

    private fun scaleForOcr(source: Bitmap): Bitmap {
        val maxSide = 2000
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) return source
        val scale = maxSide.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    fun onRawQrScanned(raw: String) {
        if (!_uiState.value.isScanning || _uiState.value.mode != CaptureMode.QR) return

        when (val result = qrParser.parse(raw)) {
            is QrParseResult.Error -> {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        rawPayload = raw,
                        errorMessage = result.message,
                        mode = CaptureMode.REVIEW,
                        source = "QR",
                    )
                }
            }
            is QrParseResult.Success -> {
                val data = result.data
                _uiState.update {
                    it.copy(
                        mode = CaptureMode.REVIEW,
                        isScanning = false,
                        rawPayload = raw,
                        qrMeta = data,
                        items = data.items,
                        totalAmount = data.totalAmount,
                        storeName = it.storeName.ifBlank {
                            data.inn?.let { inn -> "УНП/ИНН $inn" }.orEmpty()
                        },
                        dateTimeText = data.dateTime?.toString().orEmpty(),
                        source = "QR",
                        errorMessage = if (data.items.isEmpty() && data.needsApiFetch) {
                            "QR прочитан. Список товаров — через API или «Изменить»."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    fun openEdit() {
        _uiState.update { it.copy(mode = CaptureMode.EDIT, errorMessage = null) }
    }

    fun applyEditsAndReturnToReview() {
        val text = _uiState.value.rawPayload
        val parsed = textParser.parse(text)
        _uiState.update {
            val withParsed = it.applyParsed(parsed)
            withParsed.copy(
                mode = CaptureMode.REVIEW,
                items = parsed.items.ifEmpty { it.items },
                totalAmount = parsed.rawTotal
                    ?: parsed.totalAmount.takeIf { t -> t > 0 }
                    ?: it.totalAmount,
                totalDiscount = parsed.totalDiscount ?: parsed.computedDiscount.takeIf { d -> d > 0 }
                    ?: it.totalDiscount,
                errorMessage = if (parsed.items.isEmpty() && it.items.isEmpty()) {
                    "Позиции не найдены — поправьте текст"
                } else {
                    null
                },
            )
        }
    }

    fun updateItem(index: Int, item: ParsedProduct) {
        _uiState.update { state ->
            val list = state.items.toMutableList()
            if (index in list.indices) {
                list[index] = item
            }
            state.copy(
                items = list,
                totalAmount = list.sumOf { it.netTotal },
                totalDiscount = list.sumOf { it.discount },
            )
        }
    }

    fun confirmAndUpload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveMessage = null) }
            try {
                val state = _uiState.value
                val items = state.items
                if (items.isEmpty() && state.totalAmount == null) {
                    throw IllegalStateException("Нет данных для сохранения — нажмите «Изменить»")
                }
                val parsed = ParsedReceipt(
                    items = items,
                    storeName = state.storeName.ifBlank { null },
                    address = state.address.ifBlank { null },
                    unp = state.unp.ifBlank { null },
                    rnSkko = state.rnSkko.ifBlank { null },
                    documentNumber = state.documentNumber.ifBlank { null },
                    rawTotal = state.totalAmount,
                    totalDiscount = state.totalDiscount,
                    paymentInfo = state.paymentInfo.ifBlank { null },
                    dateTimeText = state.dateTimeText.ifBlank { null },
                    receiptUid = state.receiptUid.ifBlank { null },
                    cashRegister = state.cashRegister.ifBlank { null },
                    shift = state.shift.ifBlank { null },
                    saleCheckNumber = state.saleCheckNumber.ifBlank { null },
                    checkSearchCode = state.checkSearchCode.ifBlank { null },
                )
                val result = localExpenseRepository.saveReceipt(
                    receipt = parsed,
                    storeName = state.storeName,
                    rawText = state.rawPayload,
                    source = state.source.ifBlank { "TEXT" },
                )
                draftTextStore.clear()
                // UI сразу свободен; облако — в фоне
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveMessage = result.message,
                        mode = CaptureMode.CHOOSER,
                        isScanning = false,
                        rawPayload = "",
                        items = emptyList(),
                        totalAmount = null,
                        totalDiscount = null,
                        qrMeta = null,
                    )
                }
                viewModelScope.launch {
                    runCatching { syncManager.syncNow() }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка сохранения")
                }
            }
        }
    }

    fun fetchQrDetails() {
        val receipt = _uiState.value.qrMeta ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true, errorMessage = null) }
            when (val result = apiClient.fetchReceipt(receipt)) {
                is ReceiptApiClient.Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDetails = false,
                            qrMeta = result.data,
                            items = result.data.items,
                            totalAmount = result.data.totalAmount ?: it.totalAmount,
                            storeName = it.storeName.ifBlank {
                                result.data.inn?.let { inn -> "УНП/ИНН $inn" }.orEmpty()
                            },
                        )
                    }
                }
                is ReceiptApiClient.Result.HttpError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDetails = false,
                            errorMessage = "API ${result.code}: ${result.body.take(200)}",
                        )
                    }
                }
                is ReceiptApiClient.Result.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoadingDetails = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun rescan() {
        val sourceMode = when (_uiState.value.source) {
            "QR" -> CaptureMode.QR
            else -> CaptureMode.OCR
        }
        selectMode(sourceMode)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
    }

    private fun QrScanUiState.applyParsed(parsed: ParsedReceipt): QrScanUiState = copy(
        storeName = storeName.ifBlank { parsed.storeName.orEmpty() },
        address = address.ifBlank { parsed.address.orEmpty() },
        unp = unp.ifBlank { parsed.unp.orEmpty() },
        rnSkko = rnSkko.ifBlank { parsed.rnSkko.orEmpty() },
        documentNumber = documentNumber.ifBlank { parsed.documentNumber.orEmpty() },
        dateTimeText = dateTimeText.ifBlank { parsed.dateTimeText.orEmpty() },
        paymentInfo = paymentInfo.ifBlank { parsed.paymentInfo.orEmpty() },
        receiptUid = receiptUid.ifBlank { parsed.receiptUid.orEmpty() },
        cashRegister = cashRegister.ifBlank { parsed.cashRegister.orEmpty() },
        shift = shift.ifBlank { parsed.shift.orEmpty() },
        saleCheckNumber = saleCheckNumber.ifBlank { parsed.saleCheckNumber.orEmpty() },
        checkSearchCode = checkSearchCode.ifBlank { parsed.checkSearchCode.orEmpty() },
        items = parsed.items,
        totalAmount = parsed.rawTotal ?: parsed.totalAmount.takeIf { it > 0 },
        totalDiscount = parsed.totalDiscount ?: parsed.computedDiscount.takeIf { it > 0 },
    )
}
