package com.pdfreader.core.ocr

data class OcrJob(
    val documentId: String,
    val pageIndex: Int,
    val language: String = "eng"
)
