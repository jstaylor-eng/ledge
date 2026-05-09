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

    fun isAnkiDroidInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.ichi2.anki", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getDecks(): List<AnkiDeck> {
        val decks = mutableListOf<AnkiDeck>()
        val projection = arrayOf("id", "name")
        
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                DECKS_URI, projection, null, null, null
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("id")
                val nameIndex = it.getColumnIndex("name")
                if (idIndex != -1 && nameIndex != -1) {
                    while (it.moveToNext()) {
                        decks.add(AnkiDeck(it.getLong(idIndex), it.getString(nameIndex)))
                    }
                }
            }
            decks
        } catch (e: Exception) {
            emptyList()
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
