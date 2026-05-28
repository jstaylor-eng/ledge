package com.example.ledge.data.service

import android.content.Context
import android.util.Log
import com.example.ledge.data.db.AppDatabase
import com.example.ledge.data.model.DictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class DictionaryService(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.dictionaryDao()

    /**
     * Initializes the dictionary from the assets file if the DB is empty.
     */
    suspend fun initializeIfNeeded(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (dao.getCount() > 0) return@withContext

        try {
            val inputStream = context.assets.open("cedict.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            val total = lines.size
            val entries = mutableListOf<DictionaryEntry>()

            lines.forEachIndexed { index, line ->
                if (line.startsWith("#")) return@forEachIndexed // Skip comments

                // Format: Traditional Simplified [pinyin] /def1/def2/
                val parts = line.split(" ")
                if (parts.size < 3) return@forEachIndexed

                val traditional = parts[0]
                val simplified = parts[1]
                
                val pinyinStart = line.indexOf("[")
                val pinyinEnd = line.indexOf("]")
                if (pinyinStart == -1 || pinyinEnd == -1) return@forEachIndexed
                val pinyin = line.substring(pinyinStart + 1, pinyinEnd)

                val definitions = line.substring(pinyinEnd + 2).trim()

                entries.add(DictionaryEntry(
                    traditional = traditional,
                    simplified = simplified,
                    pinyin = pinyin,
                    definitions = definitions
                ))

                // Batch insert every 1000 entries
                if (entries.size >= 1000) {
                    dao.insertAll(entries)
                    entries.clear()
                    onProgress(index.toFloat() / total.toFloat())
                }
            }
            if (entries.isNotEmpty()) {
                dao.insertAll(entries)
            }
            onProgress(1.0f)
        } catch (e: Exception) {
            Log.e("DictionaryService", "Error initializing dictionary: ${e.message}")
        }
    }

    suspend fun lookup(word: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        dao.lookup(word)
    }
}
