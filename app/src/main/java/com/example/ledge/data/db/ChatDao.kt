package com.example.ledge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ledge.data.model.ChatMessage

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_history")
    suspend fun clearHistory()
}
