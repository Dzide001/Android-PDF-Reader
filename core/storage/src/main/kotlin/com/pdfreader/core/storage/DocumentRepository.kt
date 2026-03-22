package com.pdfreader.core.storage

import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val dao: DocumentDao
) {
    fun observeRecentDocuments(limit: Int = 20): Flow<List<DocumentEntity>> =
        dao.observeRecentDocuments(limit)

    suspend fun saveOpenedDocument(
        uri: String,
        displayName: String,
        pageCount: Int
    ) {
        val timestamp = System.currentTimeMillis()
        val existing = DocumentEntity(
            id = uri,
            displayName = displayName,
            uri = uri,
            pageCount = pageCount,
            lastOpenedAt = timestamp,
            isFavorite = false
        )
        dao.upsert(existing)
    }

    suspend fun setFavorite(documentId: String, isFavorite: Boolean) {
        dao.setFavorite(documentId, isFavorite)
    }
}
