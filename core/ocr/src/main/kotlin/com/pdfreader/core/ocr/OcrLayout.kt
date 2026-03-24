package com.pdfreader.core.ocr

import android.graphics.RectF

/**
 * Represents a bounding box in document coordinates
 * Values are in 0-1 fractional coordinates (0.0 = left/top, 1.0 = right/bottom)
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun toRectF(): RectF = RectF(left, top, right, bottom)
    
    companion object {
        fun fromPixels(left: Float, top: Float, right: Float, bottom: Float, 
                      pageWidth: Float, pageHeight: Float): BoundingBox {
            return BoundingBox(
                left = left / pageWidth,
                top = top / pageHeight,
                right = right / pageWidth,
                bottom = bottom / pageHeight
            )
        }
    }
}

/**
 * Represents a single recognized character/glyph with position and confidence
 */
data class OcrGlyph(
    val char: String,
    val confidence: Int,
    val bbox: BoundingBox
)

/**
 * Represents a word/token - group of characters
 */
data class OcrWord(
    val text: String,
    val confidence: Int,
    val bbox: BoundingBox,
    val glyphs: List<OcrGlyph> = emptyList(),
    val fontInfo: String? = null,
    val fontSize: Float? = null
)

/**
 * Represents a text line - group of words
 */
data class OcrLine(
    val text: String,
    val confidence: Int,
    val bbox: BoundingBox,
    val words: List<OcrWord>,
    val writingDirection: String = "horizontal",  // horizontal, vertical, mixed
    val baseline: Float? = null  // fractional Y coordinate
)

/**
 * Represents a paragraph/block - group of lines
 */
data class OcrBlock(
    val blockId: String,  // e.g., "block_1_0", "block_1_1"
    val blockType: String,  // text, image, table, list, etc.
    val text: String,
    val confidence: Int,
    val bbox: BoundingBox,
    val lines: List<OcrLine>,
    val lang: String? = null,
    val langConfidence: Float? = null
)

/**
 * Complete OCR layout/hierarchy for a document page
 */
data class OcrPageLayout(
    val pageNumber: Int,
    val documentId: String,
    val pageWidth: Float,
    val pageHeight: Float,
    val blocks: List<OcrBlock>,
    val language: String,
    val ocrEngine: String = "tesseract",
    val engineVersion: String? = null,
    val processingTime: Long? = null  // milliseconds
) {
    fun extractFullText(): String {
        return blocks.joinToString("\n\n") { block ->
            block.lines.joinToString("\n") { line ->
                line.words.joinToString(" ") { it.text }
            }
        }
    }
    
    fun extractText(blockType: String? = null): String {
        val filtered = if (blockType != null) {
            blocks.filter { it.blockType == blockType }
        } else {
            blocks
        }
        return filtered.joinToString("\n\n") { block ->
            block.lines.joinToString("\n") { line ->
                line.words.joinToString(" ") { it.text }
            }
        }
    }
}
