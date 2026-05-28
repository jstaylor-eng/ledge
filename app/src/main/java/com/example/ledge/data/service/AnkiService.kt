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
            
            if (decks.isEmpty()) Result.failure(Exception("No decks found."))
            else Result.success(decks)
        } catch (e: SecurityException) {
            Result.failure(Exception("Security Error: Permission denied for $AUTHORITY."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNotesInDeck(deckId: Long): List<AnkiNote> {
        val notes = mutableListOf<AnkiNote>()
        val deckNotesUri = Uri.withAppendedPath(CONTENT_URI, "decks/$deckId/notes")
        val projection = arrayOf("id", "flds")
        
        return try {
            val cursor: Cursor? = context.contentResolver.query(deckNotesUri, projection, null, null, null)
            cursor?.use {
                val idIndex = it.getColumnIndex("id")
                val fldsIndex = it.getColumnIndex("flds")
                if (idIndex != -1 && fldsIndex != -1) {
                    while (it.moveToNext()) {
                        val flds = it.getString(fldsIndex).split("\u001f")
                        notes.add(AnkiNote(it.getLong(idIndex), flds))
                    }
                }
            }
            notes
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Answers a card for a given note.
     * ease: 1=Again, 2=Hard, 3=Good, 4=Easy
     */
    fun answerNote(noteId: Long, ease: Int): Boolean {
        return try {
            val values = ContentValues().apply {
                put("note_id", noteId)
                put("card_ord", 0) // Assume first card of the note
                put("ease", ease)
            }
            // In some API versions, we use update() on the schedule URI to "answer"
            val rows = context.contentResolver.update(SCHEDULE_URI, values, null, null)
            rows > 0
        } catch (e: Exception) {
            false
        }
    }
}
