package com.plna.smartcheck.data.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ML Kit Text Recognition для кассовых чеков (кириллица / русский / белорусский текст).
 *
 * On-device модель Latin умеет читать печатную кириллицу на чеках РБ/РФ.
 * Захват только по [requestCapture], чтобы не слать шум с каждого кадра.
 */
class TextOcrAnalyzer(
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    // DEFAULT_OPTIONS — печатный текст, включая кириллицу на термоленте
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isBusy = AtomicBoolean(false)
    private val captureRequested = AtomicBoolean(false)
    private val hasEmitted = AtomicBoolean(false)

    fun requestCapture() {
        if (!hasEmitted.get()) {
            captureRequested.set(true)
        }
    }

    fun reset() {
        hasEmitted.set(false)
        captureRequested.set(false)
    }

    fun close() {
        recognizer.close()
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!captureRequested.get() || hasEmitted.get() || isBusy.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isBusy.set(true)
        captureRequested.set(false)
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isNotBlank() && hasEmitted.compareAndSet(false, true)) {
                    onText(text)
                } else if (text.isBlank()) {
                    // разрешаем ещё одну попытку
                    hasEmitted.set(false)
                }
            }
            .addOnFailureListener {
                hasEmitted.set(false)
            }
            .addOnCompleteListener {
                isBusy.set(false)
                imageProxy.close()
            }
    }
}
