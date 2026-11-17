package com.plna.smartcheck.data.scan

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Русский OCR (Tesseract `rus+eng`) для чеков РБ / РФ.
 *
 * ## Где хранятся языки
 * Модели `rus.traineddata` и `eng.traineddata` скачиваются **один раз**
 * в постоянную папку приложения:
 * `context.filesDir/tesseract/tessdata/`.
 *
 * Это **не** Room и не кэш, который система чистит каждый день.
 * Файлы живут до удаления приложения / «Очистить данные».
 * При обычном обновлении APK модели **остаются**.
 *
 * ## Чем тогда Room?
 * Room хранит **чеки, товары, сессию пользователя**.
 * OCR-модели — отдельные бинарные файлы (~десятки МБ), их в SQLite не кладут.
 */
class RussianOcrEngine(
    context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val appContext = context.applicationContext
    private val tessDir = File(appContext.filesDir, "tesseract")
    private val dataDir = File(tessDir, "tessdata")

    sealed class PrepareState {
        /** Модели уже на телефоне — скачивать не нужно. */
        data object AlreadyCached : PrepareState()
        data class Downloading(val fileName: String) : PrepareState()
        data object Ready : PrepareState()
    }

    /** true, если rus+eng уже лежат на диске и пригодны к работе. */
    fun isLanguagePackReady(): Boolean =
        isModelReady("rus") && isModelReady("eng")

    fun languagePackPath(): String = dataDir.absolutePath

    suspend fun ensureReady(onProgress: (PrepareState) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!dataDir.exists()) dataDir.mkdirs()

                if (isLanguagePackReady()) {
                    onProgress(PrepareState.AlreadyCached)
                    onProgress(PrepareState.Ready)
                    return@runCatching
                }

                for (lang in listOf("rus", "eng")) {
                    if (isModelReady(lang)) continue
                    val file = File(dataDir, "$lang.traineddata")
                    onProgress(PrepareState.Downloading("$lang.traineddata"))
                    downloadTrainedData(lang, file)
                }
                onProgress(PrepareState.Ready)
            }
        }

    suspend fun recognize(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady().getOrThrow()
            val api = TessBaseAPI()
            check(
                api.init(tessDir.absolutePath, "rus+eng"),
            ) { "Не удалось инициализировать Tesseract (rus+eng)" }

            try {
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                api.setImage(bitmap)
                val text = api.utF8Text?.trim().orEmpty()
                check(text.isNotBlank()) {
                    "Текст на фото не распознан — сфотографируйте чек ближе и ровнее"
                }
                text
            } finally {
                api.recycle()
            }
        }
    }

    private fun isModelReady(lang: String): Boolean {
        val file = File(dataDir, "$lang.traineddata")
        // tessdata_fast/rus обычно > 1 МБ; порог отсекает битый/пустой файл
        return file.exists() && file.length() > 100_000L
    }

    private fun downloadTrainedData(lang: String, target: File) {
        val url =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$lang.traineddata"
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "Не удалось скачать OCR-модель $lang (${response.code})"
            }
            val body = response.body ?: error("Пустой ответ при скачивании $lang")
            // Пишем во временный файл, чтобы при обрыве сети не оставить битую модель
            val tmp = File(target.parentFile, "${target.name}.tmp")
            FileOutputStream(tmp).use { out ->
                body.byteStream().copyTo(out)
            }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "Не удалось сохранить модель $lang" }
        }
    }
}
