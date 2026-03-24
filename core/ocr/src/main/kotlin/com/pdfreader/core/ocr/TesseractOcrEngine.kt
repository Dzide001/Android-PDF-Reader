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
    @Volatile
    private var preparedLanguage: String? = null

    suspend fun initialize(language: String = "eng") = withContext(Dispatchers.IO) {
        if (preparedLanguage == language) return@withContext

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

        preparedLanguage = language
    }

    suspend fun recognize(
        bitmap: Bitmap,
        language: String = "eng"
    ): OcrPageResult = withContext(Dispatchers.IO) {
        val baseApi = TessBaseAPI()
        try {
            val initialized = baseApi.init(workDir.absolutePath, language)
            if (!initialized) {
                throw IllegalStateException("Failed to initialize Tesseract for language: $language")
            }

            baseApi.setImage(bitmap)
            val text = baseApi.utF8Text.orEmpty()
            val confidence = baseApi.meanConfidence().coerceIn(0, 100)
            
            // Get hOCR output for layout information
            val hocrOutput = baseApi.getHOCRText(0)

            OcrPageResult(
                text = text.trim(),
                confidence = confidence,
                language = language,
                hocrXml = hocrOutput
            )
        } finally {
            baseApi.end()
        }
    }
}

data class OcrPageResult(
    val text: String,
    val confidence: Int,
    val language: String,
    val hocrXml: String? = null
)
