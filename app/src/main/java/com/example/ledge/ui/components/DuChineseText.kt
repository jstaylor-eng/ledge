package com.example.ledge.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onWordLongClick: (String, AnkiNote?) -> Unit
) {
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
                onLongClick = { onWordLongClick(word, matchingNote) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordItem(
    word: String,
    status: WordStatus,
    showAllPinyin: Boolean,
    pinyin: String?,
    onLongClick: () -> Unit
) {
    var showPinyinLocal by remember { mutableStateOf(false) }
    
    val underlineColor = when (status) {
        WordStatus.DUE -> Color(0xFFFFD700) // Gold
        WordStatus.NEW -> Color(0xFF4CAF50) // Green
        else -> Color.Transparent
    }

    // Determine if pinyin should be visible
    val isPinyinVisible = showAllPinyin || (status == WordStatus.NEW) || showPinyinLocal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .combinedClickable(
                onClick = { if (!showAllPinyin) showPinyinLocal = !showPinyinLocal },
                onLongClick = onLongClick
            )
    ) {
        if (isPinyinVisible && pinyin != null) {
            Text(
                text = pinyin,
                fontSize = 11.sp,
                color = if (showPinyinLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontWeight = if (showPinyinLocal) FontWeight.Bold else FontWeight.Normal
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = word,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (underlineColor != Color.Transparent) TextDecoration.Underline else null
        )
        
        if (underlineColor != Color.Transparent) {
            Divider(color = underlineColor, thickness = 2.5.dp, modifier = Modifier.width(24.dp))
        }
    }
}
