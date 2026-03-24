package com.pdfreader.core.storage

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrSearchDao {
    @Query("DELETE FROM ocr_search_fts WHERE documentId = :documentId AND pageNumber = :pageNumber AND source = :source")
    suspend fun deletePageIndex(documentId: String, pageNumber: Int, source: String = "ocr")

    @Query(
        """
        INSERT INTO ocr_search_fts (
            documentId,
            pageNumber,
            extractedText,
            language,
            source,
            updatedAt
        ) VALUES (
            :documentId,
            :pageNumber,
            :extractedText,
            :language,
            :source,
            :updatedAt
        )
        """
    )
    suspend fun insertPageIndex(
        documentId: String,
        pageNumber: Int,
        extractedText: String,
        language: String,
        source: String = "ocr",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM ocr_search_fts WHERE documentId = :documentId")
    suspend fun deleteDocumentIndex(documentId: String)

    @Query("SELECT COUNT(*) FROM ocr_search_fts WHERE documentId = :documentId")
    fun countIndexedPages(documentId: String): Flow<Int>

    @Query(
        """
        SELECT
            pageNumber AS pageNumber,
            snippet(ocr_search_fts, 2, '[', ']', '…', 12) AS snippet,
            bm25(ocr_search_fts) AS score
        FROM ocr_search_fts
        WHERE ocr_search_fts MATCH :query
          AND documentId = :documentId
        ORDER BY score
        LIMIT :limit
        """
    )
    suspend fun searchDocument(
        documentId: String,
        query: String,
        limit: Int = 50
    ): List<OcrSearchMatch>

    @Query("INSERT INTO ocr_search_fts(ocr_search_fts) VALUES('optimize')")
    suspend fun optimizeFtsIndex()
}
