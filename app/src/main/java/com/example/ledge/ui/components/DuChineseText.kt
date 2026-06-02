package com.example.ledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.WordStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuChineseText(
    text: String,
    vocabMap: Map<String, WordStatus>,
    ankiNotes: List<AnkiNote>,
    onWordClick: (String, AnkiNote?) -> Unit
) {
    val words = text.split(" ").filter { it.isNotBlank() }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        words.forEach { word ->
            val status = vocabMap[word] ?: WordStatus.NONE
            val matchingNote = ankiNotes.find { it.fields.firstOrNull() == word }

            WordItem(
                word = word,
                status = status,
                pinyin = matchingNote?.fields?.getOrNull(1),
                onClick = { onWordClick(word, matchingNote) }
            )
        }
    }
}

@Composable
fun WordItem(
    word: String,
    status: WordStatus,
    pinyin: String?,
    onClick: () -> Unit
) {
    // Underline color based on status
    val underlineColor = when (status) {
        WordStatus.DUE -> Color(0xFFFFD700) // Gold
        WordStatus.NEW -> Color(0xFF4CAF50) // Green
        else -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        if (pinyin != null) {
            Text(
                text = pinyin,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Light
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = word,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (underlineColor != Color.Transparent) TextDecoration.Underline else null
            // Note: Modern Compose doesn't easily support colored underlines on Text directly 
            // without custom drawing, but we'll use standard underline for now.
        )
        
        // Custom color bar if underlined
        if (underlineColor != Color.Transparent) {
            Divider(color = underlineColor, thickness = 2.dp, modifier = Modifier.width(20.dp))
        }
    }
}
