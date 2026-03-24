package com.pdfreader.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResult(result: OcrResultEntity)

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId AND pageNumber = :pageNumber")
    suspend fun getOcrResult(documentId: String, pageNumber: Int): OcrResultEntity?

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId ORDER BY pageNumber")
    fun getOcrResultsByDocument(documentId: String): Flow<List<OcrResultEntity>>

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId ORDER BY pageNumber")
    suspend fun getOcrResultsByDocumentOnce(documentId: String): List<OcrResultEntity>

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId AND pageNumber = :pageNumber")
    fun observeOcrResult(documentId: String, pageNumber: Int): Flow<OcrResultEntity?>

    @Update
    suspend fun updateOcrResult(result: OcrResultEntity)

    @Query("DELETE FROM ocr_results WHERE documentId = :documentId AND pageNumber = :pageNumber")
    suspend fun deleteOcrResult(documentId: String, pageNumber: Int)

    @Query("DELETE FROM ocr_results WHERE documentId = :documentId")
    suspend fun deleteOcrResultsByDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM ocr_results WHERE documentId = :documentId")
    fun countOcrResultsForDocument(documentId: String): Flow<Int>
}
