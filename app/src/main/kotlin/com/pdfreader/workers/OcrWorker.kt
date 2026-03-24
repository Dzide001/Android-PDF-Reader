package com.pdfreader.workers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pdfreader.core.ocr.TesseractOcrEngine
import com.pdfreader.core.ocr.HocrParser
import com.pdfreader.core.ocr.OcrLayoutSerializer
import com.pdfreader.core.storage.DatabaseProvider
import com.pdfreader.core.storage.OcrResultEntity
import com.pdfreader.core.storage.OcrLayoutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

class OcrWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database by lazy { DatabaseProvider.getDatabase(applicationContext) }
    private val ocrEngine by lazy { TesseractOcrEngine(applicationContext) }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val documentId = inputData.getString(KEY_DOCUMENT_ID)
                    ?: return@withContext Result.failure(workDataOf("error" to "Missing documentId"))

                val pageNumber = inputData.getInt(KEY_PAGE_NUMBER, -1)
                    .takeIf { it >= 0 }
                    ?: return@withContext Result.failure(workDataOf("error" to "Invalid pageNumber"))

                val pdfPath = inputData.getString(KEY_PDF_PATH)
                    ?: return@withContext Result.failure(workDataOf("error" to "Missing pdfPath"))

                val language = inputData.getString(KEY_LANGUAGE) ?: "eng"

                // Initialize OCR engine with language data
                ocrEngine.initialize(language)

                // Render PDF page to bitmap
                val bitmap = renderPdfPageToBitmap(pdfPath, pageNumber)
                    ?: return@withContext Result.retry()

                try {
                    // Run OCR on bitmap
                    val ocrResult = ocrEngine.recognize(bitmap, language)

                    // Store basic OCR result (text + confidence)
                    val resultId = "${documentId}_${pageNumber}"
                    val resultEntity = OcrResultEntity(
                        id = resultId,
                        documentId = documentId,
                        pageNumber = pageNumber,
                        extractedText = ocrResult.text,
                        confidence = ocrResult.confidence,
                        language = ocrResult.language,
                        processedAt = System.currentTimeMillis(),
                        isSearchable = ocrResult.text.isNotBlank()
                    )

                    database.ocrResultDao().insertOcrResult(resultEntity)

                    // Index OCR text for full-text search (FTS5)
                    if (ocrResult.text.isNotBlank()) {
                        try {
                            database.ocrSearchDao().deletePageIndex(documentId, pageNumber)
                            database.ocrSearchDao().insertPageIndex(
                                documentId = documentId,
                                pageNumber = pageNumber,
                                extractedText = ocrResult.text,
                                language = ocrResult.language,
                                source = "ocr",
                                updatedAt = resultEntity.processedAt
                            )

                            // Optimize FTS segments periodically for large documents
                            if ((pageNumber + 1) % FTS_OPTIMIZE_INTERVAL == 0) {
                                database.ocrSearchDao().optimizeFtsIndex()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Continue even if search indexing fails
                        }
                    }

                    // Parse hOCR for layout information if available
                    if (!ocrResult.hocrXml.isNullOrEmpty()) {
                        try {
                            val hocrParser = HocrParser()
                            val pageLayout = hocrParser.parse(
                                ocrResult.hocrXml!!,
                                pageWidth = bitmap.width.toFloat(),
                                pageHeight = bitmap.height.toFloat(),
                                pageNumber = pageNumber,
                                documentId = documentId,
                                language = language
                            )

                            if (pageLayout != null) {
                                val layoutJson = OcrLayoutSerializer.toJson(pageLayout)
                                val layoutEntity = OcrLayoutEntity(
                                    id = resultId,
                                    documentId = documentId,
                                    pageNumber = pageNumber,
                                    pageWidth = bitmap.width.toFloat(),
                                    pageHeight = bitmap.height.toFloat(),
                                    layoutJson = layoutJson,
                                    language = language,
                                    engineVersion = ocrResult.hocrXml?.let {
                                        if (it.contains("ocr-version")) "hocr-1.2" else null
                                    },
                                    processingTime = System.currentTimeMillis() - resultEntity.processedAt
                                )
                                database.ocrLayoutDao().insertLayout(layoutEntity)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Continue even if layout parsing fails
                        }
                    }

                    // Report progress
                    setProgress(workDataOf(
                        "page" to pageNumber,
                        "status" to "completed",
                        "text_length" to ocrResult.text.length
                    ))

                    Result.success(workDataOf(
                        "page" to pageNumber,
                        "status" to "completed",
                        "confidence" to ocrResult.confidence
                    ))
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
                }
            }
        }
    }

    private suspend fun renderPdfPageToBitmap(
        pdfPath: String,
        pageNumber: Int
    ): Bitmap? {
        return try {
            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) return null

            val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)

            if (pageNumber >= renderer.pageCount) {
                renderer.close()
                fd.close()
                return null
            }

            val page = renderer.openPage(pageNumber)

            // Adaptive render scale to control memory for large pages while keeping OCR quality.
            val baseScale = DEFAULT_RENDER_SCALE
            val rawWidth = page.width * baseScale
            val rawHeight = page.height * baseScale
            val rawPixels = rawWidth * rawHeight
            val scaleFactor = if (rawPixels > MAX_RENDER_PIXELS) {
                sqrt(MAX_RENDER_PIXELS / rawPixels)
            } else {
                1.0f
            }

            val renderScale = (baseScale * scaleFactor).coerceIn(MIN_RENDER_SCALE, baseScale)
            val width = (page.width * renderScale).toInt().coerceAtLeast(1)
            val height = (page.height * renderScale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            fd.close()

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_PAGE_NUMBER = "page_number"
        const val KEY_PDF_PATH = "pdf_path"
        const val KEY_LANGUAGE = "language"
        const val UNIQUE_WORK_NAME = "ocr_worker"
        const val MAX_RETRIES = 3
        const val DEFAULT_RENDER_SCALE = 2.0f
        const val MIN_RENDER_SCALE = 1.25f
        const val MAX_RENDER_PIXELS = 3_500_000f
        const val FTS_OPTIMIZE_INTERVAL = 25

        fun createInputData(
            documentId: String,
            pageNumber: Int,
            pdfPath: String,
            language: String = "eng"
        ) = workDataOf(
            KEY_DOCUMENT_ID to documentId,
            KEY_PAGE_NUMBER to pageNumber,
            KEY_PDF_PATH to pdfPath,
            KEY_LANGUAGE to language
        )
    }
}
