package com.example.ledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledge.data.model.AnkiNote

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuChineseText(
    text: String,
    ankiNotes: List<AnkiNote>,
    onWordClick: (String, List<String>) -> Unit
) {
    // Split text into words (using spaces as delimiters provided by Gemma)
    val words = text.split(" ").filter { it.isNotBlank() }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        words.forEach { word ->
            // Check if word exists in Anki notes
            val matchingNote = ankiNotes.find { note ->
                note.fields.firstOrNull() == word
            }

            WordItem(
                word = word,
                pinyin = matchingNote?.fields?.getOrNull(1), // Assuming 2nd field is Pinyin
                translation = matchingNote?.fields?.getOrNull(2), // Assuming 3rd field is English
                onClick = { p, t -> onWordClick(word, listOfNotNull(p, t)) }
            )
        }
    }
}

@Composable
fun WordItem(
    word: String,
    pinyin: String?,
    translation: String?,
    onClick: (String?, String?) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clickable { onClick(pinyin, translation) }
    ) {
        if (pinyin != null) {
            Text(
                text = pinyin,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Light
            )
        } else {
            // Placeholder to keep baseline alignment if no pinyin
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = word,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
