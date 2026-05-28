package com.example.ledge

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.service.AnkiService
import com.example.ledge.data.service.GemmaService
import com.example.ledge.data.service.VoiceService
import com.example.ledge.ui.theme.LedgeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var voiceService: VoiceService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceService = VoiceService(this)
        setContent {
            LedgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LedgeApp(voiceService!!)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.shutdown()
    }
}

@Composable
fun LedgeApp(voiceService: VoiceService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ankiService = remember { AnkiService(context) }
    val gemmaService = remember { GemmaService(context) }

    var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<AnkiDeck?>(null) }
    var currentDeckNotes by remember { mutableStateOf<List<AnkiNote>>(emptyList()) }
    
    var chatInput by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var isGemmaReady by remember { mutableStateOf(false) }
    var diagnosticInfo by remember { mutableStateOf("") }
    var copyProgress by remember { mutableStateOf(-1f) }

    val modernPermission = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    var hasAnkiPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, modernPermission) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, "com.ichi2.anki.permission.READ_WRITE_PERMISSION") == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAnkiPermission = isGranted
        if (isGranted) {
            diagnosticInfo = "Permission Granted! Fetching..."
            ankiService.getDecks().onSuccess { decks = it }.onFailure { diagnosticInfo = "Fetch error: ${it.message}" }
        }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    diagnosticInfo = "Copying model to LiteRT storage..."
                    val path = gemmaService.prepareModelFromUri(it) { progress -> copyProgress = progress }
                    copyProgress = -1f
                    diagnosticInfo = "Initializing LiteRT Engine..."
                    gemmaService.initialize(path)
                    isGemmaReady = true
                    diagnosticInfo = "AI Ready! (Gemma 4)"
                } catch (e: Exception) {
                    diagnosticInfo = "Model Error: ${e.message}"
                    copyProgress = -1f
                }
            }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMicPermission = it }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Ledge: Offline AI Tutor", style = MaterialTheme.typography.headlineMedium)
        
        if (diagnosticInfo.isNotEmpty()) {
            Text("Status: $diagnosticInfo", color = Color.Magenta, style = MaterialTheme.typography.bodySmall)
        }
        
        if (copyProgress >= 0f) {
            LinearProgressIndicator(progress = copyProgress, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (decks.isEmpty()) {
            Button(onClick = { ankiLauncher.launch(modernPermission) }, modifier = Modifier.fillMaxWidth()) {
                Text("1. Connect to Anki")
            }
        } else if (!isGemmaReady) {
            Text("Step 2: Load AI Model", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Select .litertlm File")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Step 3: Select Anki Deck Context:")
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(decks) { deck ->
                    val isSelected = selectedDeck?.id == deck.id
                    TextButton(
                        onClick = { 
                            selectedDeck = deck 
                            currentDeckNotes = ankiService.getNotesInDeck(deck.id)
                        },
                        colors = if (isSelected) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.textButtonColors()
                    ) {
                        Text(deck.name)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖 AI Active | ${selectedDeck?.name ?: "None"}", modifier = Modifier.weight(1f))
                Button(onClick = { isGemmaReady = false }) { Text("Settings") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chat Interface
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatHistory) { (user, ai) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("User: $user", style = MaterialTheme.typography.bodyLarge)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("AI: $ai", color = MaterialTheme.colorScheme.primary)
                                
                                // Smart Word Chips
                                val usedWords = currentDeckNotes.filter { note -> 
                                    val hanzi = note.fields.firstOrNull() ?: ""
                                    hanzi.isNotEmpty() && ai.contains(hanzi)
                                }.take(8)

                                if (usedWords.isNotEmpty()) {
                                    Text("Words used:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                    LazyRow(modifier = Modifier.padding(top = 2.dp)) {
                                        items(usedWords) { note ->
                                            var showRating by remember { mutableStateOf(false) }
                                            val word = note.fields.firstOrNull() ?: ""
                                            
                                            Column {
                                                SuggestionChip(
                                                    onClick = { showRating = !showRating },
                                                    label = { Text(word) },
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                                if (showRating) {
                                                    Row {
                                                        listOf("Again" to 1, "Good" to 3).forEach { (label, ease) ->
                                                            TextButton(onClick = {
                                                                ankiService.answerNote(note.id, ease)
                                                                showRating = false
                                                                Toast.makeText(context, "Rate $word as $label", Toast.LENGTH_SHORT).show()
                                                            }) {
                                                                Text(label, style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { voiceService.speak(ai) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Talk to Gemma 4...") }
                )
                IconButton(onClick = {
                    if (hasMicPermission) {
                        voiceService.startListening { chatInput = it }
                    } else {
                        micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }) { Text("🎤") }
                Button(onClick = {
                    val input = chatInput
                    chatInput = ""
                    scope.launch {
                        val vocab = currentDeckNotes.take(30).joinToString { note -> note.fields.firstOrNull() ?: "" }
                        val prompt = "You are a Mandarin tutor. Chat with the user and try to naturally incorporate some of these vocabulary words: $vocab. Keep your responses concise. User: $input"
                        val response = gemmaService.generateResponse(prompt)
                        chatHistory = chatHistory + (input to response)
                        voiceService.speak(response)
                    }
                }, enabled = isGemmaReady) { Text("Send") }
            }
        }
    }
}
