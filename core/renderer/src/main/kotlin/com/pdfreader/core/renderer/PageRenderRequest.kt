package com.pdfreader.core.renderer

data class PageRenderRequest(
    val documentId: String,
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val zoom: Float = 1f
)

data class PageRenderResult(
    val success: Boolean,
    val errorMessage: String? = null
)
