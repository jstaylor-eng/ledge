package com.example.ledge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onWordClick: (String, AnkiNote?) -> Unit,
    onRequestMic: () -> Unit,
    onStartMic: () -> Unit
) {
    var chatInput by remember { mutableStateOf("") }
    
    // Flatten vocab for easy lookup in bubbles
    val vocabMap = remember(sessionVocab) {
        val map = mutableMapOf<String, WordStatus>()
        sessionVocab.forEach { (status, notes) ->
            notes.forEach { note ->
                note.fields.firstOrNull()?.let { map[it] = status }
            }
        }
        map
    }
    
    val allNotes = remember(sessionVocab) { sessionVocab.values.flatten() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("AI Tutor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reviewing ${sessionVocab[WordStatus.DUE]?.size ?: 0} words", 
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatHistory) { message ->
                    ChatBubble(
                        message = message,
                        vocabMap = vocabMap,
                        ankiNotes = allNotes,
                        isSpeaking = currentlySpeakingText == message.aiResponse,
                        onSpeak = { onSpeak(message.aiResponse) },
                        onStop = onStopSpeech,
                        onWordClick = onWordClick
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Speak or type...") }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(onClick = { if (isMicPermissionGranted) onStartMic() else onRequestMic() }) {
                    Text("🎤", style = MaterialTheme.typography.headlineSmall)
                }
                
                Button(onClick = { 
                    if (chatInput.isNotBlank()) {
                        onSendMessage(chatInput)
                        chatInput = ""
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    vocabMap: Map<String, WordStatus>,
    ankiNotes: List<AnkiNote>,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    onWordClick: (String, AnkiNote?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "You: ${message.userText}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DuChineseText(
                    text = message.aiResponse,
                    vocabMap = vocabMap,
                    ankiNotes = ankiNotes,
                    onWordClick = onWordClick
                )

                IconButton(
                    onClick = { if (isSpeaking) onStop() else onSpeak() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (isSpeaking) Text("⏹️") else Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                }
            }
        }
    }
}
