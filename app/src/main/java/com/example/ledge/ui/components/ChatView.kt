package com.example.ledge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.ChatMessage
import com.example.ledge.data.model.WordStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    chatHistory: List<ChatMessage>,
    sessionVocab: Map<WordStatus, List<AnkiNote>>,
    currentlySpeakingText: String?,
    isMicPermissionGranted: Boolean,
    showAllPinyin: Boolean,
    transcription: String?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onWordClick: (String, AnkiNote?) -> Unit,
    onRequestMic: () -> Unit,
    onStartMic: () -> Unit
) {
    var chatInput by remember { mutableStateOf("") }
    var focusedTranslation by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(transcription) { if (transcription != null) chatInput = transcription }
    
    val vocabMap = remember(sessionVocab) {
        val map = mutableMapOf<String, WordStatus>()
        sessionVocab.forEach { (status, notes) ->
            notes.forEach { note -> note.fields.firstOrNull()?.let { map[it] = status } }
        }
        map
    }
    val allNotes = remember(sessionVocab) { sessionVocab.values.flatten() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AI Tutor") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
                )
                AnimatedVisibility(visible = focusedTranslation != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = focusedTranslation ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatHistory) { message ->
                    ChatBubble(
                        message = message,
                        vocabMap = vocabMap,
                        ankiNotes = allNotes,
                        showAllPinyin = showAllPinyin,
                        isSpeaking = currentlySpeakingText == message.aiResponse,
                        onSpeak = { onSpeak(message.aiResponse) },
                        onStop = onStopSpeech,
                        onWordClick = onWordClick,
                        onFocus = { focusedTranslation = it }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)) {
                OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Chat...") })
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { if (isMicPermissionGranted) onStartMic() else onRequestMic() }) { Text("🎤") }
                Button(onClick = { if (chatInput.isNotBlank()) { onSendMessage(chatInput); chatInput = "" } }) { Text("Send") }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    vocabMap: Map<String, WordStatus>,
    ankiNotes: List<AnkiNote>,
    showAllPinyin: Boolean,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    onWordClick: (String, AnkiNote?) -> Unit,
    onFocus: (String) -> Unit
) {
    val parts = message.aiResponse.split("|")
    val hanziPart = parts.firstOrNull() ?: ""
    val englishPart = parts.getOrNull(1)?.trim() ?: "Translation loading..."

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = "You: ${message.userText}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.align(Alignment.End))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.padding(top = 4.dp).clickable { onFocus(englishPart) }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DuChineseText(
                    text = hanziPart,
                    vocabMap = vocabMap,
                    ankiNotes = ankiNotes,
                    showAllPinyin = showAllPinyin,
                    onWordClick = onWordClick
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = { if (isSpeaking) onStop() else onSpeak() }) {
                        if (isSpeaking) Text("⏹️") else Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Tap for English", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = Color.Gray)
                }
            }
        }
    }
}
