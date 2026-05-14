package com.example.ledge.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote

class AnkiService(private val context: Context) {

    // Updated authority for 2026 AnkiDroid
    private val AUTHORITY = "com.ichi2.anki.flashcards"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY")
    private val DECKS_URI = Uri.withAppendedPath(CONTENT_URI, "decks")

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
            // Updated columns for 2026 API: deck_id, deck_name
            val projection = arrayOf("deck_id", "deck_name")
            
            val cursor: Cursor? = context.contentResolver.query(
                DECKS_URI, projection, null, null, null
            )

            if (cursor == null) {
                return Result.failure(Exception("AnkiDroid Provider ($AUTHORITY) not found or returned null. Check AnkiDroid API settings."))
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
            
            if (decks.isEmpty()) {
                // Try fallback for older API if needed? No, let's stick to flashcards first.
                Result.failure(Exception("Handshake OK, but no decks found in $AUTHORITY."))
            } else {
                Result.success(decks)
            }
        } catch (e: SecurityException) {
            Result.failure(Exception("Security Error: Permission denied for $AUTHORITY."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNotesInDeck(deckId: Long): List<AnkiNote> {
        val notes = mutableListOf<AnkiNote>()
        // Updated path for 2026: decks/<id>/notes
        val deckNotesUri = Uri.withAppendedPath(CONTENT_URI, "decks/$deckId/notes")
        
        // Note: columns might be different here too, usually 'id' and 'flds'
        val projection = arrayOf("id", "flds")
        
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                deckNotesUri, projection, null, null, null
            )

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
}
