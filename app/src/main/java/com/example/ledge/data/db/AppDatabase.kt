package com.example.ledge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ledge.data.model.DictionaryEntry
import com.example.ledge.data.model.ChatMessage

@Database(entities = [DictionaryEntry::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledge_database"
                )
                .fallbackToDestructiveMigration() // Simple for dev, resets DB on version bump
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
