package com.example.ledge.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote

class AnkiService(private val context: Context) {

    private val AUTHORITY = "com.ichi2.anki.api"
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
        val projection = arrayOf("id", "name")
        
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                DECKS_URI, projection, null, null, null
            )

            if (cursor == null) {
                return Result.failure(Exception("AnkiDroid returned no data. Check: AnkiDroid > Settings > Advanced > AnkiDroid API."))
            }

            cursor.use {
                val idIndex = it.getColumnIndex("id")
                val nameIndex = it.getColumnIndex("name")
                if (idIndex != -1 && nameIndex != -1) {
                    while (it.moveToNext()) {
                        decks.add(AnkiDeck(it.getLong(idIndex), it.getString(nameIndex)))
                    }
                }
            }
            Result.success(decks)
        } catch (e: SecurityException) {
            Result.failure(Exception("Security Error: Permission Denied. Try granting it in Android Settings > Apps > Ledge > Permissions."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNotesInDeck(deckId: Long): List<AnkiNote> {
        val notes = mutableListOf<AnkiNote>()
        val deckNotesUri = Uri.withAppendedPath(CONTENT_URI, "decks/$deckId/notes")
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
