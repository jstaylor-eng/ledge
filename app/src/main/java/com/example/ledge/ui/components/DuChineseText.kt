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
    showAllPinyin: Boolean,
    onWordClick: (String, AnkiNote?) -> Unit
) {
    // Only parse the Hanzi part (AI responds with Hanzi | English)
    val hanziText = text.split("|").firstOrNull() ?: text
    val words = hanziText.split(" ").filter { it.isNotBlank() }

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
                showAllPinyin = showAllPinyin,
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
    showAllPinyin: Boolean,
    pinyin: String?,
    onClick: () -> Unit
) {
    val underlineColor = when (status) {
        WordStatus.DUE -> Color(0xFFFFD700)
        WordStatus.NEW -> Color(0xFF4CAF50)
        else -> Color.Transparent
    }

    // Show pinyin if it's a NEW word OR if 'Show All' is toggled ON
    val shouldShowPinyin = (status == WordStatus.NEW || showAllPinyin)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clickable { onClick() }
    ) {
        if (shouldShowPinyin && pinyin != null) {
            Text(
                text = pinyin,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Normal
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = word,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (underlineColor != Color.Transparent) TextDecoration.Underline else null
        )
        
        if (underlineColor != Color.Transparent) {
            Divider(color = underlineColor, thickness = 2.dp, modifier = Modifier.width(20.dp))
        }
    }
}
