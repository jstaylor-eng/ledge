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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ledge.data.db.AppDatabase
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.ChatMessage
import com.example.ledge.data.model.DictionaryEntry
import com.example.ledge.data.service.AnkiService
import com.example.ledge.data.service.DictionaryService
import com.example.ledge.data.service.GemmaService
import com.example.ledge.data.service.SettingsService
import com.example.ledge.data.service.VoiceService
import com.example.ledge.ui.components.WordPopup
import com.example.ledge.ui.theme.LedgeTheme
import kotlinx.coroutines.flow.first
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
    val db = remember { AppDatabase.getDatabase(context) }
    val chatDao = remember { db.chatDao() }
    val ankiService = remember { AnkiService(context) }
    val gemmaService = remember { GemmaService(context) }
    val dictionaryService = remember { DictionaryService(context) }
    val settingsService = remember { SettingsService(context) }

    var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<AnkiDeck?>(null) }
    var currentDeckNotes by remember { mutableStateOf<List<AnkiNote>>(emptyList()) }
    var noteModels by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isGemmaReady by remember { mutableStateOf(false) }
    var diagnosticInfo by remember { mutableStateOf("") }
    var copyProgress by remember { mutableStateOf(-1f) }
    var showSettings by remember { mutableStateOf(false) }

    // Persistent Prefs
    val useWordSpaces by settingsService.useWordSpaces.collectAsState(initial = true)

    // UI state
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var wordEntries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var currentlySpeakingText by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(voiceService) {
        voiceService.setSpeechListener { isSpeaking ->
            if (!isSpeaking) currentlySpeakingText = null
        }
    }

    LaunchedEffect(Unit) {
        chatHistory = chatDao.getAllMessages()
        
        // Auto-init AI
        val path = gemmaService.getPersistentModelPath()
        if (path != null && !isGemmaReady) {
            diagnosticInfo = "Initializing AI..."
            try {
                gemmaService.initialize(path)
                isGemmaReady = true
                diagnosticInfo = "AI Ready!"
            } catch (e: Exception) {
                diagnosticInfo = "AI error: ${e.message}"
            }
        }
        
        if (!dictionaryService.isInitialized()) {
            diagnosticInfo = "Dictionary empty. Go to settings to import CC-CEDICT."
        }

        if (hasAnkiPermission) {
            ankiService.getDecks().onSuccess { availableDecks ->
                decks = availableDecks
                noteModels = ankiService.getModels()
                
                // Auto-select last deck
                val savedDeckId = settingsService.selectedDeckId.first()
                if (savedDeckId != null) {
                    val deck = availableDecks.find { it.id == savedDeckId }
                    if (deck != null) {
                        selectedDeck = deck
                        currentDeckNotes = ankiService.getPriorityNotesInDeck(deck.id)
                    }
                }
            }
        }
    }

    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAnkiPermission = isGranted
        if (isGranted) {
            ankiService.getDecks().onSuccess { decks = it }
            noteModels = ankiService.getModels()
        }
    }
    
    val modelPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    diagnosticInfo = "Copying model..."
                    val path = gemmaService.prepareModelFromUri(it) { p -> copyProgress = p }
                    copyProgress = -1f
                    gemmaService.initialize(path)
                    isGemmaReady = true
                    showSettings = false
                    diagnosticInfo = "AI Ready!"
                } catch (e: Exception) {
                    diagnosticInfo = "Model Error: ${e.message}"
                    copyProgress = -1f
                }
            }
        }
    }

    val dictPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    diagnosticInfo = "Importing Dictionary..."
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        dictionaryService.importFromStream(stream) { p -> copyProgress = p }
                    }
                    copyProgress = -1f
                    diagnosticInfo = "Dictionary Ready!"
                } catch (e: Exception) {
                    diagnosticInfo = "Dict Error: ${e.message}"
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
            onAddToAnki = { entry ->
                scope.launch {
                    val deck = selectedDeck ?: decks.firstOrNull()
                    val model = noteModels.find { it.second.contains("Basic", true) } ?: noteModels.firstOrNull()
                    if (deck != null && model != null) {
                        val fields = listOf(entry.simplified, entry.pinyin, entry.definitions)
                        if (ankiService.addNote(deck.id, model.first, fields)) {
                            Toast.makeText(context, "Added to Anki!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { selectedWord = null }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Ledge", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showSettings = !showSettings }) {
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

        if (showSettings) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show Word Boundaries (Spaces)", modifier = Modifier.weight(1f))
                        Switch(checked = useWordSpaces, onCheckedChange = { 
                            scope.launch { settingsService.setUseWordSpaces(it) }
                        })
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { modelPickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Update AI Model")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { dictPickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Import CC-CEDICT Dictionary")
                    }
                    TextButton(onClick = { showSettings = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }

        if (decks.isEmpty()) {
            Button(onClick = { ankiLauncher.launch(modernPermission) }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to AnkiDroid")
            }
        } else if (isGemmaReady) {
            if (selectedDeck == null) {
                Text("Select Anki Deck Context:", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(decks) { deck ->
                        TextButton(onClick = { 
                            scope.launch {
                                selectedDeck = deck 
                                currentDeckNotes = ankiService.getPriorityNotesInDeck(deck.id)
                                settingsService.setSelectedDeckId(deck.id)
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(deck.name, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖 ${selectedDeck?.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedDeck = null }) { Text("Switch Deck") }
                    IconButton(onClick = { scope.launch { chatDao.clearHistory(); chatHistory = emptyList() } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chat Interface
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chatHistory) { message ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("You: ${message.userText}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.padding(top = 2.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val rawAi = message.aiResponse.replace(Regex("[*#]"), "")
                                    val displayAi = if (useWordSpaces) rawAi else rawAi.replace(" ", "")
                                    
                                    ClickableText(
                                        text = AnnotatedString(displayAi),
                                        style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                                        onClick = { offset ->
                                            scope.launch {
                                                if (useWordSpaces) {
                                                    val before = displayAi.substring(0, offset).lastIndexOf(" ")
                                                    val after = displayAi.indexOf(" ", offset).let { if (it == -1) displayAi.length else it }
                                                    val word = displayAi.substring(before + 1, after).trim().replace(Regex("[，。？！、]"), "")
                                                    if (word.isNotEmpty()) {
                                                        val entries = dictionaryService.lookup(word)
                                                        if (entries.isNotEmpty()) {
                                                            selectedWord = word
                                                            wordEntries = entries
                                                            return@launch
                                                        }
                                                    }
                                                }
                                                for (len in 4 downTo 1) {
                                                    val testEnd = (offset + len).coerceAtMost(displayAi.length)
                                                    if (testEnd <= offset) continue
                                                    val word = displayAi.substring(offset, testEnd)
                                                    val entries = dictionaryService.lookup(word)
                                                    if (entries.isNotEmpty()) {
                                                        selectedWord = word
                                                        wordEntries = entries
                                                        return@launch
                                                    }
                                                }
                                                val singleChar = displayAi[offset].toString()
                                                selectedWord = singleChar
                                                wordEntries = dictionaryService.lookup(singleChar)
                                            }
                                        }
                                    )
                                    
                                    val usedWords = currentDeckNotes.filter { note -> 
                                        val hanzi = note.fields.firstOrNull() ?: ""
                                        hanzi.isNotEmpty() && rawAi.contains(hanzi)
                                    }.take(6)

                                    if (usedWords.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LazyRow {
                                            items(usedWords) { note ->
                                                var showRating by remember { mutableStateOf(false) }
                                                val word = note.fields.firstOrNull() ?: ""
                                                Column(modifier = Modifier.padding(end = 4.dp)) {
                                                    InputChip(selected = showRating, onClick = { showRating = !showRating }, label = { Text(word) })
                                                    if (showRating) {
                                                        Row {
                                                            listOf("Again" to 1, "Good" to 3).forEach { (label, ease) ->
                                                                TextButton(onClick = { ankiService.answerNote(note.id, ease); showRating = false }) {
                                                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    IconButton(onClick = { 
                                        if (currentlySpeakingText == message.aiResponse) {
                                            voiceService.stop()
                                            currentlySpeakingText = null
                                        } else {
                                            currentlySpeakingText = message.aiResponse
                                            voiceService.speak(message.aiResponse)
                                        }
                                    }) {
                                        if (currentlySpeakingText == message.aiResponse) Text("⏹️") else Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                                    }
                                }
                            }
                        }
                    }
                }

                var chatInput by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Talk to AI...") })
                    IconButton(onClick = { if (hasMicPermission) voiceService.startListening { chatInput = it } else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text("🎤") }
                    Button(onClick = {
                        val input = chatInput
                        chatInput = ""
                        scope.launch {
                            val vocab = currentDeckNotes.take(30).joinToString { note -> note.fields.firstOrNull() ?: "" }
                            val prompt = """
                                You are a professional Mandarin tutor. 
                                IMMERSION MODE: Speak ONLY in Chinese characters (Hanzi). NEVER use English unless the user explicitly asks for a translation.
                                VOCABULARY: Naturally use some of these words: $vocab.
                                FORMAT: Separate every Chinese word with a space to help the student identify word boundaries.
                                User: $input
                            """.trimIndent()
                            val response = gemmaService.generateResponse(prompt)
                            val newMessage = ChatMessage(userText = input, aiResponse = response, deckName = selectedDeck?.name)
                            chatDao.insertMessage(newMessage); chatHistory = chatHistory + newMessage
                            currentlySpeakingText = response; voiceService.speak(response)
                        }
                    }, enabled = isGemmaReady) { Text("Send") }
                }
            }
        }
    }
}
