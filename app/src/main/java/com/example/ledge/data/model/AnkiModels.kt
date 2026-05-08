package com.example.ledge.data.model

data class AnkiDeck(
    val id: Long,
    val name: String
)

data class AnkiNote(
    val id: Long,
    val fields: List<String>
)
