package com.pdfreader.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val uri: String,
    val pageCount: Int,
    val lastOpenedAt: Long,
    val isFavorite: Boolean
)
