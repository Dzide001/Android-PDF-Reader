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

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pdf_reader.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build().also { db ->
                    instance = db
                }
        }
    }
}

