package com.plna.smartcheck.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plna.smartcheck.PlnaApplication
import com.plna.smartcheck.R
import com.plna.smartcheck.data.model.ParsedProduct
import com.plna.smartcheck.ui.AppViewModelFactory
import com.plna.smartcheck.ui.components.PlnaLoadingOverlay
import com.plna.smartcheck.ui.components.PlnaScreenTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val app = LocalContext.current.applicationContext as PlnaApplication
    val viewModel: QrScanViewModel = viewModel(factory = AppViewModelFactory(app))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onGalleryImagePicked(uri)
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }
    LaunchedEffect(uiState.saveMessage) {
        val message = uiState.saveMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSaveMessage()
    }

    val showLoader = uiState.isOcrProcessing || uiState.isSaving || uiState.isLoadingDetails
    val loaderTitle = when {
        uiState.isSaving -> stringResource(R.string.loading_saving)
        uiState.isLoadingDetails -> stringResource(R.string.please_wait)
        uiState.ocrStatus?.contains("Скачиваем", ignoreCase = true) == true ->
            stringResource(R.string.loading_ocr_models)
        uiState.isOcrProcessing -> stringResource(R.string.loading_ocr_recognize)
        else -> stringResource(R.string.please_wait)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            when (uiState.mode) {
                CaptureMode.CHOOSER -> PlnaScreenTopBar(title = "Добавить чек")
                CaptureMode.QR -> PlnaScreenTopBar(
                    title = "Сканер QR",
                    onBack = viewModel::backToChooser,
                )
                CaptureMode.OCR -> PlnaScreenTopBar(
                    title = "Сканер текста",
                    onBack = viewModel::backToChooser,
                )
                CaptureMode.REVIEW -> PlnaScreenTopBar(
                    title = "Проверка чека",
                    onBack = viewModel::backToChooser,
                )
                CaptureMode.EDIT -> PlnaScreenTopBar(
                    title = "Изменить",
                    onBack = { viewModel.applyEditsAndReturnToReview() },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (uiState.mode) {
            CaptureMode.CHOOSER -> ModeChooser(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                onQr = { viewModel.selectMode(CaptureMode.QR) },
                onOcr = { viewModel.selectMode(CaptureMode.OCR) },
                onGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            CaptureMode.QR -> CameraQrContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                enabled = uiState.isScanning,
                onRawQr = viewModel::onRawQrScanned,
            )
            CaptureMode.OCR -> CameraOcrContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                enabled = uiState.isScanning,
                isProcessing = uiState.isOcrProcessing,
                ocrStatus = uiState.ocrStatus,
                captureNonce = uiState.ocrCaptureNonce,
                onCapture = viewModel::requestOcrCapture,
                onBitmap = viewModel::onOcrBitmapCaptured,
                onCaptureError = viewModel::onOcrCaptureFailed,
                onPickGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            CaptureMode.REVIEW -> ReviewContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                storeName = uiState.storeName,
                address = uiState.address,
                unp = uiState.unp,
                rnSkko = uiState.rnSkko,
                documentNumber = uiState.documentNumber,
                dateTimeText = uiState.dateTimeText,
                paymentInfo = uiState.paymentInfo,
                receiptUid = uiState.receiptUid,
                checkSearchCode = uiState.checkSearchCode,
                totalAmount = uiState.totalAmount,
                totalDiscount = uiState.totalDiscount,
                items = uiState.items,
                needsApi = uiState.qrMeta?.needsApiFetch == true && uiState.items.isEmpty(),
                isLoadingDetails = uiState.isLoadingDetails,
                isSaving = uiState.isSaving,
                onFetchApi = viewModel::fetchQrDetails,
                onConfirm = viewModel::confirmAndUpload,
                onEdit = viewModel::openEdit,
                onRescan = viewModel::rescan,
            )
            CaptureMode.EDIT -> EditContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                storeName = uiState.storeName,
                address = uiState.address,
                unp = uiState.unp,
                rnSkko = uiState.rnSkko,
                documentNumber = uiState.documentNumber,
                dateTimeText = uiState.dateTimeText,
                paymentInfo = uiState.paymentInfo,
                receiptUid = uiState.receiptUid,
                checkSearchCode = uiState.checkSearchCode,
                totalAmount = uiState.totalAmount,
                totalDiscount = uiState.totalDiscount,
                rawPayload = uiState.rawPayload,
                items = uiState.items,
                onStoreNameChange = viewModel::onStoreNameChange,
                onRawChange = viewModel::onRawPayloadChange,
                onItemChange = viewModel::updateItem,
                onDone = viewModel::applyEditsAndReturnToReview,
            )
        }
    }

    PlnaLoadingOverlay(
        visible = showLoader,
        title = loaderTitle,
        subtitle = uiState.ocrStatus ?: stringResource(R.string.loading_hint),
    )
    }
}

@Composable
private fun ModeChooser(
    modifier: Modifier = Modifier,
    onQr: () -> Unit,
    onOcr: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.add_receipt_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.add_receipt_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ModeCard(
            title = stringResource(R.string.mode_qr_title),
            subtitle = stringResource(R.string.mode_qr_subtitle),
            icon = Icons.Filled.QrCodeScanner,
            onClick = onQr,
        )
        ModeCard(
            title = stringResource(R.string.mode_ocr_title),
            subtitle = stringResource(R.string.mode_ocr_subtitle),
            icon = Icons.Filled.DocumentScanner,
            onClick = onOcr,
        )
        ModeCard(
            title = stringResource(R.string.mode_gallery_title),
            subtitle = stringResource(R.string.mode_gallery_subtitle),
            icon = Icons.Filled.PhotoLibrary,
            onClick = onGallery,
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun CameraQrContent(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onRawQr: (String) -> Unit,
) {
    CameraPermissionGate {
        Box(modifier = modifier) {
            QrCameraPreview(
                modifier = Modifier.fillMaxSize(),
                enabled = enabled,
                onRawQr = onRawQr,
            )
            ScanOverlay(hint = if (enabled) "Наведите на QR-код чека" else "QR распознан")
        }
    }
}


@Composable
private fun CameraOcrContent(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isProcessing: Boolean,
    ocrStatus: String?,
    captureNonce: Int,
    onCapture: () -> Unit,
    onBitmap: (android.graphics.Bitmap) -> Unit,
    onCaptureError: (String) -> Unit,
    onPickGallery: () -> Unit,
) {
    CameraPermissionGate {
        Box(modifier = modifier) {
            OcrCameraPreview(
                modifier = Modifier.fillMaxSize(),
                enabled = enabled && !isProcessing,
                captureNonce = captureNonce,
                onBitmapCaptured = onBitmap,
                onCaptureError = onCaptureError,
            )
            ScanOverlay(hint = "Наведите на русский текст чека")
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!ocrStatus.isNullOrBlank()) {
                    Text(
                        text = ocrStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = onCapture,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(28.dp),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Распознаём…")
                    } else {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Сфотографировать")
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onPickGallery,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Из галереи")
                }
            }
        }
    }
}


@Composable
private fun ReviewContent(
    modifier: Modifier = Modifier,
    storeName: String,
    address: String,
    unp: String,
    rnSkko: String,
    documentNumber: String,
    dateTimeText: String,
    paymentInfo: String,
    receiptUid: String,
    checkSearchCode: String,
    totalAmount: Double?,
    totalDiscount: Double?,
    items: List<ParsedProduct>,
    needsApi: Boolean,
    isLoadingDetails: Boolean,
    isSaving: Boolean,
    onFetchApi: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Проверьте чек",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Как на кассовой ленте — листать можно, если товаров много",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReceiptTicket(
                storeName = storeName,
                address = address.ifBlank { null },
                unp = unp.ifBlank { null },
                rnSkko = rnSkko.ifBlank { null },
                documentNumber = documentNumber.ifBlank { null },
                dateTimeText = dateTimeText.ifBlank { null },
                items = items,
                totalAmount = totalAmount,
                totalDiscount = totalDiscount,
                paymentInfo = paymentInfo.ifBlank { null },
                receiptUid = receiptUid.ifBlank { null },
                checkSearchCode = checkSearchCode.ifBlank { null },
            )
            if (needsApi) {
                FilledTonalButton(
                    onClick = onFetchApi,
                    enabled = !isLoadingDetails,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLoadingDetails) "Загрузка…" else "Подтянуть товары из API")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Подтвердить")
            }
            FilledTonalButton(
                onClick = onEdit,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Изменить")
            }
            TextButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                Text("Сканировать снова")
            }
        }
    }
}


@Composable
private fun EditContent(
    modifier: Modifier = Modifier,
    storeName: String,
    address: String,
    unp: String,
    rnSkko: String,
    documentNumber: String,
    dateTimeText: String,
    paymentInfo: String,
    receiptUid: String,
    checkSearchCode: String,
    totalAmount: Double?,
    totalDiscount: Double?,
    rawPayload: String,
    items: List<ParsedProduct>,
    onStoreNameChange: (String) -> Unit,
    onRawChange: (String) -> Unit,
    onItemChange: (Int, ParsedProduct) -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Изменение чека",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Тот же вид кассовой ленты — листайте, если позиций много",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReceiptTicket(
                storeName = storeName,
                address = address.ifBlank { null },
                unp = unp.ifBlank { null },
                rnSkko = rnSkko.ifBlank { null },
                documentNumber = documentNumber.ifBlank { null },
                dateTimeText = dateTimeText.ifBlank { null },
                items = items,
                totalAmount = totalAmount,
                totalDiscount = totalDiscount,
                paymentInfo = paymentInfo.ifBlank { null },
                receiptUid = receiptUid.ifBlank { null },
                checkSearchCode = checkSearchCode.ifBlank { null },
            )
            OutlinedTextField(
                value = storeName,
                onValueChange = onStoreNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Магазин") },
                singleLine = true,
            )
            OutlinedTextField(
                value = rawPayload,
                onValueChange = onRawChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                label = { Text("Текст чека (русский OCR)") },
            )
            items.forEachIndexed { index, product ->
                OutlinedTextField(
                    value = product.name,
                    onValueChange = { onItemChange(index, product.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Товар ${index + 1}") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = product.quantity.toString(),
                        onValueChange = { value ->
                            val q = value.replace(',', '.').toDoubleOrNull() ?: product.quantity
                            onItemChange(
                                index,
                                product.copy(
                                    quantity = q,
                                    lineTotal = product.unitPrice * q,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Кол-во") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = product.unitPrice.toString(),
                        onValueChange = { value ->
                            val p = value.replace(',', '.').toDoubleOrNull() ?: product.unitPrice
                            onItemChange(
                                index,
                                product.copy(
                                    unitPrice = p,
                                    lineTotal = p * product.quantity,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Цена") },
                        singleLine = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Готово")
        }
    }
}

@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Нужен доступ к камере", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Разрешить камеру")
            }
        }
    }
}

@Composable
private fun ScanOverlay(hint: String) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
    val frameColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameSize = size.minDimension * 0.72f
            val left = (size.width - frameSize) / 2f
            val top = (size.height - frameSize) / 2.6f
            val rect = Rect(left, top, left + frameSize, top + frameSize)
            val path = Path().apply {
                addRoundRect(RoundRect(rect, CornerRadius(40f, 40f)))
            }
            clipPath(path, clipOp = ClipOp.Difference) { drawRect(scrim) }
            drawRoundRect(
                color = frameColor,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(40f, 40f),
                style = Stroke(width = 5f),
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(16.dp),
        )
    }
}
