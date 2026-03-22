package com.pdfreader.core.pdfengine

import java.nio.ByteBuffer

/**
 * MuPDF implementation of PdfEngine
 * 
 * This class provides JNI bindings to MuPDF for PDF rendering
 */
class MuPdfEngine : PdfEngine {
    
    companion object {
        init {
            // Load native library
            try {
                System.loadLibrary("mupdf_engine")
            } catch (e: UnsatisfiedLinkError) {
                throw RuntimeException("Failed to load mupdf_engine native library", e)
            }
        }
        
        private external fun nativeInitialize(): Boolean
    }
    
    // Native methods - declared as external
    private external fun nativeOpenDocument(path: String): Long
    private external fun nativeCloseDocument(docHandle: Long): Int
    private external fun nativeGetPageCount(docHandle: Long): Int
    private external fun nativeGetPageDimensions(docHandle: Long, pageIndex: Int, output: FloatArray): Int
    private external fun nativeRenderPage(docHandle: Long, pageIndex: Int, width: Int, height: Int, buffer: ByteBuffer): Int
    private external fun nativeExtractText(docHandle: Long, pageIndex: Int): String?
    private external fun nativeGetTextBoundingBoxes(docHandle: Long, pageIndex: Int): Array<TextBox>?
    private external fun nativeGetAnnotations(docHandle: Long, pageIndex: Int): Array<AnnotationData>?
    
    override fun openDocument(path: String): Long {
        return nativeOpenDocument(path)
    }
    
    override fun closeDocument(docHandle: Long): Int {
        return nativeCloseDocument(docHandle)
    }
    
    override fun getPageCount(docHandle: Long): Int {
        return nativeGetPageCount(docHandle)
    }
    
    override fun getPageDimensions(docHandle: Long, pageIndex: Int): PageDimensions? {
        val dimensions = FloatArray(2)
        val result = nativeGetPageDimensions(docHandle, pageIndex, dimensions)
        return if (result == 0) {
            PageDimensions(dimensions[0], dimensions[1])
        } else {
            null
        }
    }
    
    override fun renderPage(
        docHandle: Long,
        pageIndex: Int,
        width: Int,
        height: Int,
        buffer: ByteBuffer
    ): Int {
        return nativeRenderPage(docHandle, pageIndex, width, height, buffer)
    }
    
    override fun extractText(docHandle: Long, pageIndex: Int): String {
        return nativeExtractText(docHandle, pageIndex) ?: ""
    }
    
    override fun getTextBoundingBoxes(docHandle: Long, pageIndex: Int): List<TextBox> {
        return nativeGetTextBoundingBoxes(docHandle, pageIndex)?.toList() ?: emptyList()
    }
    
    override fun getAnnotations(docHandle: Long, pageIndex: Int): List<AnnotationData> {
        return nativeGetAnnotations(docHandle, pageIndex)?.toList() ?: emptyList()
    }
}
