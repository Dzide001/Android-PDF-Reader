package com.pdfreader.core.storage

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pdf_reader.db"
            ).build().also { db ->
                instance = db
            }
        }
    }
}
