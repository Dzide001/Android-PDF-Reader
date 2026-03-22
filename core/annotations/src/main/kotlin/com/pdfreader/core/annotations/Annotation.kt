package com.pdfreader.core.annotations

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class Annotation(
    @PrimaryKey val id: String,
    val docId: String,
    val page: Int,
    val type: String,
    val geometryJson: String,
    val color: Int,
    val strokeWidth: Float,
    val createdAt: Long
)
