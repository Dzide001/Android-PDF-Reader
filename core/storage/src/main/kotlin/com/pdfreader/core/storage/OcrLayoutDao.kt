package com.pdfreader.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrLayoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: OcrLayoutEntity)

    @Query("SELECT * FROM ocr_layouts WHERE documentId = :documentId AND pageNumber = :pageNumber")
    suspend fun getLayout(documentId: String, pageNumber: Int): OcrLayoutEntity?

    @Query("SELECT * FROM ocr_layouts WHERE documentId = :documentId ORDER BY pageNumber")
    fun getLayoutsByDocument(documentId: String): Flow<List<OcrLayoutEntity>>

    @Query("SELECT * FROM ocr_layouts WHERE documentId = :documentId AND pageNumber = :pageNumber")
    fun observeLayout(documentId: String, pageNumber: Int): Flow<OcrLayoutEntity?>

    @Update
    suspend fun updateLayout(layout: OcrLayoutEntity)

    @Query("DELETE FROM ocr_layouts WHERE documentId = :documentId AND pageNumber = :pageNumber")
    suspend fun deleteLayout(documentId: String, pageNumber: Int)

    @Query("DELETE FROM ocr_layouts WHERE documentId = :documentId")
    suspend fun deleteLayoutsByDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM ocr_layouts WHERE documentId = :documentId")
    fun countLayoutsForDocument(documentId: String): Flow<Int>
}
