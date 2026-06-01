package com.example.ledge.ui.components

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
import androidx.compose.ui.unit.dp
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    chatHistory: List<ChatMessage>,
    currentDeckNotes: List<AnkiNote>,
    currentlySpeakingText: String?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onWordClick: (String, List<String>) -> Unit,
    onAnkiRate: (AnkiNote, Int) -> Unit
) {
    var chatInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Tutor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatHistory) { message ->
                    ChatBubble(
                        message = message,
                        ankiNotes = currentDeckNotes,
                        isSpeaking = currentlySpeakingText == message.aiResponse,
                        onSpeak = { onSpeak(message.aiResponse) },
                        onStop = onStopSpeech,
                        onWordClick = onWordClick,
                        onAnkiRate = onAnkiRate
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type in Chinese...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { 
                    onSendMessage(chatInput)
                    chatInput = ""
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
    ankiNotes: List<AnkiNote>,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    onWordClick: (String, List<String>) -> Unit,
    onAnkiRate: (AnkiNote, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // User message
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
                // Du Chinese Style Rendering
                DuChineseText(
                    text = message.aiResponse,
                    ankiNotes = ankiNotes,
                    onWordClick = onWordClick
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = { if (isSpeaking) onStop() else onSpeak() }) {
                        if (isSpeaking) Text("⏹️") else Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Priority words used in this message
                    val usedNotes = ankiNotes.filter { note ->
                        val hanzi = note.fields.firstOrNull() ?: ""
                        hanzi.isNotEmpty() && message.aiResponse.contains(hanzi)
                    }.take(3)
                    
                    usedNotes.forEach { note ->
                        TextButton(onClick = { onAnkiRate(note, 3) }) {
                            Text("Mark ${note.fields.firstOrNull()} OK", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
