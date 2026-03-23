package com.pdfreader.core.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TesseractOcrEngine(
    private val context: Context,
    private val workDir: File = File(context.filesDir, "ocr")
) {
    suspend fun initialize(language: String = "eng") = withContext(Dispatchers.IO) {
        val tessDataDir = File(workDir, "tessdata")
        if (!tessDataDir.exists()) tessDataDir.mkdirs()

        val trainedDataFile = File(tessDataDir, "$language.traineddata")
        if (!trainedDataFile.exists()) {
            val assetPath = "tessdata/$language.traineddata"
            try {
                context.assets.open(assetPath).use { input ->
                    trainedDataFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Missing OCR language data asset: $assetPath. Add it to app/src/main/assets/tessdata/",
                    e
                )
            }
        }
    }

    suspend fun recognize(
        bitmap: Bitmap,
        language: String = "eng"
    ): OcrPageResult = withContext(Dispatchers.IO) {
        initialize(language)

        val baseApi = TessBaseAPI()
        try {
            val initialized = baseApi.init(workDir.absolutePath, language)
            if (!initialized) {
                throw IllegalStateException("Failed to initialize Tesseract for language: $language")
            }

            baseApi.setImage(bitmap)
            val text = baseApi.utF8Text.orEmpty()
            val confidence = baseApi.meanConfidence().coerceIn(0, 100)

            OcrPageResult(
                text = text.trim(),
                confidence = confidence,
                language = language
            )
        } finally {
            baseApi.end()
        }
    }
}

data class OcrPageResult(
    val text: String,
    val confidence: Int,
    val language: String
)
