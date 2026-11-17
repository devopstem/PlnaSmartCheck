package com.plna.smartcheck.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

/**
 * Превью + JPEG-снимок для русского OCR (Tesseract rus+eng).
 */
@Composable
fun OcrCameraPreview(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    captureNonce: Int,
    onBitmapCaptured: (Bitmap) -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnBitmap by rememberUpdatedState(onBitmapCaptured)
    val latestOnError by rememberUpdatedState(onCaptureError)

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(captureNonce) {
        if (captureNonce <= 0 || !enabled) return@LaunchedEffect
        val photoFile = File(context.cacheDir, "ocr_capture_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            output,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                            ?: error("Не удалось прочитать фото")
                        latestOnBitmap(bitmap)
                    } catch (e: Exception) {
                        latestOnError(e.message ?: "Ошибка обработки фото")
                    } finally {
                        photoFile.delete()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    latestOnError(exception.message ?: "Не удалось сделать фото")
                }
            },
        )
    }

    DisposableEffect(lifecycleOwner, enabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val bindListener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            if (enabled) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }
        }
        cameraProviderFuture.addListener(bindListener, mainExecutor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    DisposableEffect(Unit) {
        onDispose { captureExecutor.shutdown() }
    }

    AndroidView(modifier = modifier, factory = { previewView })
}
