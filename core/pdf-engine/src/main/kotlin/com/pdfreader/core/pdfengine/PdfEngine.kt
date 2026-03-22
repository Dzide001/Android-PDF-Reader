package com.pdfreader.core.pdfengine

/**
 * Interface for MuPDF PDF rendering engine
 * 
 * All methods are thread-safe and can be called from any thread.
 * However, rendering operations should be batched to avoid excessive
 * JNI overhead.
 */
interface PdfEngine {
    
    /**
     * Opens a PDF document
     * 
     * @param path Absolute file path to PDF document
     * @return Document handle (opaque long value), or -1 on error
     */
    fun openDocument(path: String): Long
    
    /**
     * Closes a PDF document and releases resources
     * 
     * @param docHandle Document handle from openDocument()
     * @return 0 on success, non-zero on error
     */
    fun closeDocument(docHandle: Long): Int
    
    /**
     * Gets total page count for a document
     * 
     * @param docHandle Document handle
     * @return Number of pages (>0), or -1 on error
     */
    fun getPageCount(docHandle: Long): Int
    
    /**
     * Gets dimensions of a specific page
     * 
     * @param docHandle Document handle
     * @param pageIndex Zero-based page number
     * @return PageDimensions with width/height in points, or null on error
     */
    fun getPageDimensions(docHandle: Long, pageIndex: Int): PageDimensions?
    
    /**
     * Renders a PDF page to an RGBA buffer
     * 
     * Renders page at specified dimensions and returns data to provided buffer.
     * The buffer must be at least (width * height * 4) bytes (RGBA, 8-bit per channel).
     * 
     * @param docHandle Document handle
     * @param pageIndex Zero-based page number
     * @param width Output width in pixels
     * @param height Output height in pixels
     * @param buffer Pre-allocated ByteBuffer for RGBA data
     * @return 0 on success, non-zero on error
     */
    fun renderPage(
        docHandle: Long,
        pageIndex: Int,
        width: Int,
        height: Int,
        buffer: java.nio.ByteBuffer
    ): Int
    
    /**
     * Extracts text from a page
     * 
     * @param docHandle Document handle
     * @param pageIndex Zero-based page number
     * @return Extracted text, or empty string if no text found
     */
    fun extractText(docHandle: Long, pageIndex: Int): String
    
    /**
     * Gets bounding boxes for all text blocks on a page
     * 
     * Useful for text selection and highlighting.
     * 
     * @param docHandle Document handle
     * @param pageIndex Zero-based page number
     * @return List of TextBox objects with position and text
     */
    fun getTextBoundingBoxes(docHandle: Long, pageIndex: Int): List<TextBox>
    
    /**
     * Gets annotations for a page
     * 
     * @param docHandle Document handle
     * @param pageIndex Zero-based page number
     * @return List of AnnotationData objects
     */
    fun getAnnotations(docHandle: Long, pageIndex: Int): List<AnnotationData>
}

/**
 * Represents dimensions of a PDF page
 */
data class PageDimensions(
    val width: Float,  // Points (1/72 inch)
    val height: Float
)

/**
 * Represents a text box with position and content
 */
data class TextBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String
)

/**
 * Represents a PDF annotation
 */
data class AnnotationData(
    val type: String,  // "highlight", "underline", etc.
    val rects: List<TextBox>,
    val contents: String?,
    val author: String?,
    val color: Int
)
