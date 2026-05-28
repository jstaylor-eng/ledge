package com.example.ledge.data.service

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote

class AnkiService(private val context: Context) {

    private val AUTHORITY = "com.ichi2.anki.flashcards"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY")
    private val DECKS_URI = Uri.withAppendedPath(CONTENT_URI, "decks")
    private val NOTES_URI = Uri.withAppendedPath(CONTENT_URI, "notes")
    private val MODELS_URI = Uri.withAppendedPath(CONTENT_URI, "models")
    private val SCHEDULE_URI = Uri.withAppendedPath(CONTENT_URI, "schedule")

    fun getAnkiPackageName(): String? {
        val packages = listOf("com.ichi2.anki", "com.ichi2.anki.parallel")
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    fun getDecks(): Result<List<AnkiDeck>> {
        val decks = mutableListOf<AnkiDeck>()
        return try {
            val projection = arrayOf("deck_id", "deck_name")
            val cursor: Cursor? = context.contentResolver.query(DECKS_URI, projection, null, null, null)

            if (cursor == null) {
                return Result.failure(Exception("AnkiDroid Provider ($AUTHORITY) not found."))
            }

            cursor.use {
                val idIndex = it.getColumnIndex("deck_id")
                val nameIndex = it.getColumnIndex("deck_name")
                if (idIndex != -1 && nameIndex != -1) {
                    while (it.moveToNext()) {
                        decks.add(AnkiDeck(it.getLong(idIndex), it.getString(nameIndex)))
                    }
                }
            }
            Result.success(decks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets available note types (models) from Anki.
     */
    fun getModels(): List<Pair<Long, String>> {
        val models = mutableListOf<Pair<Long, String>>()
        try {
            val cursor = context.contentResolver.query(MODELS_URI, arrayOf("model_id", "model_name"), null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    models.add(it.getLong(0) to it.getString(1))
                }
            }
        } catch (e: Exception) {}
        return models
    }

    fun getNotesInDeck(deckId: Long): List<AnkiNote> {
        val notes = mutableListOf<AnkiNote>()
        val deckNotesUri = Uri.withAppendedPath(CONTENT_URI, "decks/$deckId/notes")
        try {
            val cursor = context.contentResolver.query(deckNotesUri, arrayOf("id", "flds"), null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val flds = it.getString(1).split("\u001f")
                    notes.add(AnkiNote(it.getLong(0), flds))
                }
            }
        } catch (e: Exception) {}
        return notes
    }

    fun answerNote(noteId: Long, ease: Int): Boolean {
        return try {
            val values = ContentValues().apply {
                put("note_id", noteId)
                put("card_ord", 0)
                put("ease", ease)
            }
            context.contentResolver.update(SCHEDULE_URI, values, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Adds a new note to Anki.
     * fields: [Simplified, Pinyin, English, ...]
     */
    fun addNote(deckId: Long, modelId: Long, fields: List<String>): Boolean {
        return try {
            val values = ContentValues().apply {
                put("deck_id", deckId)
                put("model_id", modelId)
                put("flds", fields.joinToString("\u001f"))
                put("tags", "ledge-ai")
            }
            context.contentResolver.insert(NOTES_URI, values) != null
        } catch (e: Exception) {
            false
        }
    }
}
