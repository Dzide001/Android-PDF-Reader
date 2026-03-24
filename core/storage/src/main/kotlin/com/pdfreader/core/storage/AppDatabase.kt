package com.pdfreader.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, OcrResultEntity::class, OcrLayoutEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun ocrResultDao(): OcrResultDao
    abstract fun ocrLayoutDao(): OcrLayoutDao
}
