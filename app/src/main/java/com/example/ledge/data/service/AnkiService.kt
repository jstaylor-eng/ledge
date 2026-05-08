package com.example.ledge.data.service

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote

class AnkiService(private val context: Context) {

    private val AUTHORITY = "com.ichi2.anki.api"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY")

    private val DECKS_URI = Uri.withAppendedPath(CONTENT_URI, "decks")
    private val NOTES_URI = Uri.withAppendedPath(CONTENT_URI, "notes")

    fun getDecks(): List<AnkiDeck> {
        val decks = mutableListOf<AnkiDeck>()
        val projection = arrayOf("id", "name")
        
        val cursor: Cursor? = context.contentResolver.query(
            DECKS_URI, projection, null, null, null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex("id")
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                decks.add(AnkiDeck(it.getLong(idIndex), it.getString(nameIndex)))
            }
        }
        return decks
    }

    fun getNotesInDeck(deckId: Long): List<AnkiNote> {
        val notes = mutableListOf<AnkiNote>()
        // The correct way to get notes for a deck in AnkiDroid API
        val deckNotesUri = Uri.withAppendedPath(CONTENT_URI, "decks/$deckId/notes")
        
        val projection = arrayOf("id", "flds")
        val cursor: Cursor? = context.contentResolver.query(
            deckNotesUri, projection, null, null, null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex("id")
            val fldsIndex = it.getColumnIndex("flds")
            while (it.moveToNext()) {
                val flds = it.getString(fldsIndex).split("\u001f")
                notes.add(AnkiNote(it.getLong(idIndex), flds))
            }
        }
        return notes
    }
}
