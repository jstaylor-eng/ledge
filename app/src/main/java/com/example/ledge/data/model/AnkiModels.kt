package com.example.ledge.data.model

data class AnkiDeck(
    val id: Long,
    val name: String
)

enum class WordStatus {
    DUE, NEW, KNOWN, NONE
}

data class AnkiNote(
    val id: Long,
    val fields: List<String>,
    val status: WordStatus = WordStatus.NONE,
    val interval: Int = 0
)

enum class LessonMode {
    FREE_CHAT, DAILY_STORY, INTENSIVE_REVIEW
}
