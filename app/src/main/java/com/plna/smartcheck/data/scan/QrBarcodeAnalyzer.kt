package com.plna.smartcheck.data.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [ImageAnalysis.Analyzer] на базе ML Kit Barcode Scanning.
 * Возвращает сырую строку из QR (или другого поддерживаемого штрихкода).
 */
class QrBarcodeAnalyzer(
    private val onRawValue: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
            )
            .build()
    )

    private val isBusy = AtomicBoolean(false)
    private val hasEmitted = AtomicBoolean(false)

    fun reset() {
        hasEmitted.set(false)
    }

    fun close() {
        scanner.close()
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasEmitted.get() || isBusy.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isBusy.set(true)
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes
                    .asSequence()
                    .mapNotNull { it.rawValue }
                    .firstOrNull { it.isNotBlank() }

                if (raw != null && hasEmitted.compareAndSet(false, true)) {
                    onRawValue(raw)
                }
            }
            .addOnCompleteListener {
                isBusy.set(false)
                imageProxy.close()
            }
    }
}
