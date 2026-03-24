package com.pdfreader.core.storage

data class OcrSearchMatch(
    val pageNumber: Int,
    val snippet: String,
    val score: Double
)
