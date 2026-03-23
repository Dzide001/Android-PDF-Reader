package com.pdfreader.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OcrResultEntity(
    @PrimaryKey val id: String, // Format: "{documentId}_{pageNumber}"
    val documentId: String,
    val pageNumber: Int,
    val extractedText: String,
    val confidence: Int,
    val language: String,
    val processedAt: Long,
    val isSearchable: Boolean = false
)
