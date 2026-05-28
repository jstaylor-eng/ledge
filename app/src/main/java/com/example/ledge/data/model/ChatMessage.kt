package com.example.ledge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userText: String,
    val aiResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deckName: String? = null
)
