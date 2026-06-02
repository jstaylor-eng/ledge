package com.example.ledge.data.service

import android.content.Context
import android.util.Log
import com.example.ledge.data.db.AppDatabase
import com.example.ledge.data.model.DictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream

class DictionaryService(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.dictionaryDao()

    suspend fun isInitialized(): Boolean = withContext(Dispatchers.IO) {
        dao.getCount() > 0
    }

    /**
     * Initializes from a stream (either Assets or External file).
     */
    suspend fun importFromStream(inputStream: InputStream, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            val total = lines.size
            val entries = mutableListOf<DictionaryEntry>()

            db.clearAllTables()

            lines.forEachIndexed { index, line ->
                if (line.startsWith("#")) return@forEachIndexed

                try {
                    val firstSpace = line.indexOf(" ")
                    val secondSpace = line.indexOf(" ", firstSpace + 1)
                    val pinyinStart = line.indexOf("[")
                    val pinyinEnd = line.indexOf("]")
                    
                    if (firstSpace == -1 || secondSpace == -1 || pinyinStart == -1 || pinyinEnd == -1) return@forEachIndexed

                    val traditional = line.substring(0, firstSpace)
                    val simplified = line.substring(firstSpace + 1, secondSpace)
                    val pinyin = line.substring(pinyinStart + 1, pinyinEnd)
                    val rawDefinitions = line.substring(pinyinEnd + 1).trim()
                    
                    // Simple HSK detection: look for 'HSK1', 'HSK 2' etc in definitions
                    val hskMatch = Regex("HSK\\s*([1-6])").find(rawDefinitions)
                    val hskLevel = hskMatch?.groupValues?.get(1)?.toInt() ?: 0

                    entries.add(DictionaryEntry(
                        traditional = traditional,
                        simplified = simplified,
                        pinyin = pinyin,
                        definitions = rawDefinitions,
                        hskLevel = hskLevel
                    ))

                    if (entries.size >= 2000) {
                        dao.insertAll(entries)
                        entries.clear()
                        onProgress(index.toFloat() / total.toFloat())
                    }
                } catch (e: Exception) {}
            }
            if (entries.isNotEmpty()) dao.insertAll(entries)
            onProgress(1.0f)
        } catch (e: Exception) {
            Log.e("DictionaryService", "Import failed: ${e.message}")
        }
    }

    suspend fun lookup(word: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        dao.lookup(word)
    }
}
