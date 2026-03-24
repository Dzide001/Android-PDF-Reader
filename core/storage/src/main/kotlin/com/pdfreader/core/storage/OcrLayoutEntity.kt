package com.pdfreader.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_layouts",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OcrLayoutEntity(
    @PrimaryKey val id: String, // Format: "{documentId}_{pageNumber}"
    val documentId: String,
    val pageNumber: Int,
    val pageWidth: Float,
    val pageHeight: Float,
    // JSON serialization of OcrPageLayout hierarchy
    val layoutJson: String,
    val language: String,
    val ocrEngine: String = "tesseract",
    val engineVersion: String? = null,
    val processingTime: Long? = null,
    val storedAt: Long = System.currentTimeMillis()
)
