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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.DictionaryEntry
import com.example.ledge.data.service.AnkiService
import com.example.ledge.data.service.DictionaryService
import com.example.ledge.data.service.GemmaService
import com.example.ledge.data.service.VoiceService
import com.example.ledge.ui.components.WordPopup
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgeApp(voiceService: VoiceService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ankiService = remember { AnkiService(context) }
    val gemmaService = remember { GemmaService(context) }
    val dictionaryService = remember { DictionaryService(context) }

    var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<AnkiDeck?>(null) }
    var currentDeckNotes by remember { mutableStateOf<List<AnkiNote>>(emptyList()) }
    
    var chatInput by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var isGemmaReady by remember { mutableStateOf(false) }
    var isDictionaryReady by remember { mutableStateOf(false) }
    var diagnosticInfo by remember { mutableStateOf("") }
    var copyProgress by remember { mutableStateOf(-1f) }
    var showModelSettings by remember { mutableStateOf(false) }

    // TTS state
    var currentlySpeakingText by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(voiceService) {
        voiceService.setSpeechListener { isSpeaking ->
            if (!isSpeaking) {
                currentlySpeakingText = null
            }
        }
    }

    // Dictionary Popup State
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var wordEntries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }

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

    // Auto-load & Init
    LaunchedEffect(Unit) {
        // Init AI
        val path = gemmaService.getPersistentModelPath()
        if (path != null && !isGemmaReady) {
            diagnosticInfo = "Found AI model. Initializing..."
            try {
                gemmaService.initialize(path)
                isGemmaReady = true
                diagnosticInfo = "AI Ready!"
            } catch (e: Exception) {
                diagnosticInfo = "AI error: ${e.message}"
            }
        }
        
        // Init Dictionary
        diagnosticInfo = "Loading dictionary..."
        dictionaryService.initializeIfNeeded { progress ->
            copyProgress = progress
        }
        isDictionaryReady = true
        copyProgress = -1f
        diagnosticInfo = if (isGemmaReady) "Ready!" else "AI Model needed."

        if (hasAnkiPermission) {
            ankiService.getDecks().onSuccess { decks = it }
        }
    }

    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAnkiPermission = isGranted
        if (isGranted) {
            diagnosticInfo = "Permission Granted!"
            ankiService.getDecks().onSuccess { decks = it }.onFailure { diagnosticInfo = "Fetch error: ${it.message}" }
        }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    diagnosticInfo = "Copying model to internal storage..."
                    val path = gemmaService.prepareModelFromUri(it) { progress -> copyProgress = progress }
                    copyProgress = -1f
                    diagnosticInfo = "Initializing LiteRT Engine..."
                    gemmaService.initialize(path)
                    isGemmaReady = true
                    showModelSettings = false
                    diagnosticInfo = "AI Ready!"
                } catch (e: Exception) {
                    diagnosticInfo = "Model Error: ${e.message}"
                    copyProgress = -1f
                }
            }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMicPermission = it }

    // Dictionary Popup
    selectedWord?.let { word ->
        WordPopup(
            word = word,
            entries = wordEntries,
            onDismiss = { selectedWord = null }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Ledge", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showModelSettings = !showModelSettings }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        
        if (diagnosticInfo.isNotEmpty()) {
            Text("Status: $diagnosticInfo", color = Color.Magenta, style = MaterialTheme.typography.bodySmall)
        }
        
        if (copyProgress >= 0f) {
            LinearProgressIndicator(progress = copyProgress, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showModelSettings || !isGemmaReady) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Engine Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isGemmaReady) "Change Model (.litertlm)" else "Select Model (.litertlm)")
                    }
                    if (isGemmaReady) {
                        TextButton(onClick = { showModelSettings = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("Close")
                        }
                    }
                }
            }
        }

        if (decks.isEmpty()) {
            Button(onClick = { ankiLauncher.launch(modernPermission) }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to AnkiDroid")
            }
        } else if (isGemmaReady) {
            // Main Chat Experience
            if (selectedDeck == null) {
                Text("Select Anki Deck Context:", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(decks) { deck ->
                        TextButton(
                            onClick = { 
                                selectedDeck = deck 
                                currentDeckNotes = ankiService.getNotesInDeck(deck.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(deck.name, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖 ${selectedDeck?.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedDeck = null }) { Text("Switch Deck") }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chat Interface
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chatHistory) { (user, ai) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("You: $user", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val cleanAi = ai.replace(Regex("[*#]"), "")
                                    
                                    // Interactive Text
                                    ClickableText(
                                        text = AnnotatedString(cleanAi),
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                        ),
                                        onClick = { offset ->
                                            scope.launch {
                                                // Longest matching word algorithm
                                                var found = false
                                                for (len in 4 downTo 1) {
                                                    val testEnd = (offset + len).coerceAtMost(cleanAi.length)
                                                    if (testEnd <= offset) continue
                                                    
                                                    val word = cleanAi.substring(offset, testEnd)
                                                    val entries = dictionaryService.lookup(word)
                                                    if (entries.isNotEmpty()) {
                                                        selectedWord = word
                                                        wordEntries = entries
                                                        found = true
                                                        break
                                                    }
                                                }
                                                if (!found) {
                                                    val char = cleanAi[offset].toString()
                                                    selectedWord = char
                                                    wordEntries = dictionaryService.lookup(char)
                                                }
                                            }
                                        }
                                    )
                                    
                                    val usedWords = currentDeckNotes.filter { note -> 
                                        val hanzi = note.fields.firstOrNull() ?: ""
                                        hanzi.isNotEmpty() && ai.contains(hanzi)
                                    }.take(6)

                                    if (usedWords.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LazyRow {
                                            items(usedWords) { note ->
                                                var showRating by remember { mutableStateOf(false) }
                                                val word = note.fields.firstOrNull() ?: ""
                                                
                                                Column(modifier = Modifier.padding(end = 4.dp)) {
                                                    InputChip(
                                                        selected = showRating,
                                                        onClick = { showRating = !showRating },
                                                        label = { Text(word) }
                                                    )
                                                    if (showRating) {
                                                        Row {
                                                            listOf("Again" to 1, "Good" to 3).forEach { (label, ease) ->
                                                                TextButton(onClick = {
                                                                    ankiService.answerNote(note.id, ease)
                                                                    showRating = false
                                                                    Toast.makeText(context, "Rated $word", Toast.LENGTH_SHORT).show()
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

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        val isSpeakingThis = currentlySpeakingText == ai
                                        IconButton(onClick = { 
                                            if (isSpeakingThis) {
                                                voiceService.stop()
                                                currentlySpeakingText = null
                                            } else {
                                                currentlySpeakingText = ai
                                                voiceService.speak(ai)
                                            }
                                        }) {
                                            if (isSpeakingThis) {
                                                Text("⏹️") // Stop Emoji
                                            } else {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Speak or type...") }
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
                            val prompt = "You are a Mandarin tutor. Chat naturally. Vocabulary context: $vocab. Use Hanzi and keep responses short. User: $input"
                            val response = gemmaService.generateResponse(prompt)
                            chatHistory = chatHistory + (input to response)
                            currentlySpeakingText = response
                            voiceService.speak(response)
                        }
                    }, enabled = isGemmaReady) { Text("Send") }
                }
            }
        }
    }
}
