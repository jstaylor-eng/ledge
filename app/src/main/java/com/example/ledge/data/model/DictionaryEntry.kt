package com.example.ledge.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary",
    indices = [
        Index(value = ["simplified"]),
        Index(value = ["traditional"])
    ]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val simplified: String,
    val traditional: String,
    val pinyin: String,
    val definitions: String,
    val hskLevel: Int = 0 // 0 = None, 1-6 = HSK Level
)
