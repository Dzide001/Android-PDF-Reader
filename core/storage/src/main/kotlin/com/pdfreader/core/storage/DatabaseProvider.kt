package com.pdfreader.core.storage

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    // Migration from v1 to v2 - add ocr_results table
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ocr_results` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `documentId` TEXT NOT NULL,
                    `pageNumber` INTEGER NOT NULL,
                    `extractedText` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `language` TEXT NOT NULL,
                    `processedAt` INTEGER NOT NULL,
                    `isSearchable` INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ocr_results_documentId` ON `ocr_results` (`documentId`)"
            )
        }
    }

    // Migration from v2 to v3 - add ocr_layouts table
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ocr_layouts` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `documentId` TEXT NOT NULL,
                    `pageNumber` INTEGER NOT NULL,
                    `pageWidth` REAL NOT NULL,
                    `pageHeight` REAL NOT NULL,
                    `layoutJson` TEXT NOT NULL,
                    `language` TEXT NOT NULL,
                    `ocrEngine` TEXT NOT NULL DEFAULT 'tesseract',
                    `engineVersion` TEXT,
                    `processingTime` INTEGER,
                    `storedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ocr_layouts_documentId` ON `ocr_layouts` (`documentId`)"
            )
        }
    }

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pdf_reader.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { db ->
                    instance = db
                }
        }
    }
}
