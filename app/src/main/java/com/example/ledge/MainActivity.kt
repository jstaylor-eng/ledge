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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ledge.data.db.AppDatabase
import com.example.ledge.data.model.AnkiDeck
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.ChatMessage
import com.example.ledge.data.model.DictionaryEntry
import com.example.ledge.data.service.*
import com.example.ledge.ui.components.*
import com.example.ledge.ui.theme.LedgeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var voiceService: VoiceService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceService = VoiceService(this)
        setContent {
            val context = LocalContext.current
            val settingsService = remember { SettingsService(context) }
            val savedDarkMode by settingsService.isDarkMode.collectAsState(initial = null)
            val isDark = savedDarkMode ?: isSystemInDarkTheme()

            LedgeTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LedgeApp(voiceService!!, settingsService, isDark)
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
fun LedgeApp(voiceService: VoiceService, settingsService: SettingsService, isDarkMode: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val db = remember { AppDatabase.getDatabase(context) }
    val chatDao = remember { db.chatDao() }
    val ankiService = remember { AnkiService(context) }
    val gemmaService = remember { GemmaService(context) }
    val dictionaryService = remember { DictionaryService(context) }

    var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<AnkiDeck?>(null) }
    var currentDeckNotes by remember { mutableStateOf<List<AnkiNote>>(emptyList()) }
    var noteModels by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isGemmaReady by remember { mutableStateOf(false) }
    var diagnosticInfo by remember { mutableStateOf("") }
    var copyProgress by remember { mutableStateOf(-1f) }

    var selectedWord by remember { mutableStateOf<String?>(null) }
    var matchingAnkiNote by remember { mutableStateOf<AnkiNote?>(null) }
    var dictEntries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var currentlySpeakingText by remember { mutableStateOf<String?>(null) }

    val useWordSpaces by settingsService.useWordSpaces.collectAsState(initial = true)
    val modernPermission = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    var hasAnkiPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, modernPermission) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, "com.ichi2.anki.permission.READ_WRITE_PERMISSION") == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(voiceService) {
        voiceService.setSpeechListener { if (!it) currentlySpeakingText = null }
    }

    LaunchedEffect(Unit) {
        chatHistory = chatDao.getAllMessages()
        val path = gemmaService.getPersistentModelPath()
        if (path != null) {
            try {
                gemmaService.initialize(path)
                isGemmaReady = true
            } catch (e: Exception) {
                diagnosticInfo = "AI init error: ${e.message}"
            }
        }
        
        if (hasAnkiPermission) {
            ankiService.getDecks().onSuccess { availableDecks ->
                decks = availableDecks
                noteModels = ankiService.getModels()
                val savedDeckId = settingsService.selectedDeckId.first()
                if (savedDeckId != null) {
                    availableDecks.find { it.id == savedDeckId }?.let {
                        selectedDeck = it
                        currentDeckNotes = ankiService.getPriorityNotesInDeck(it.id)
                    }
                }
            }
        }
    }

    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAnkiPermission = isGranted
        if (isGranted) ankiService.getDecks().onSuccess { decks = it }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    diagnosticInfo = "Copying model..."
                    val path = gemmaService.prepareModelFromUri(it) { p -> copyProgress = p }
                    copyProgress = -1f; gemmaService.initialize(path); isGemmaReady = true; diagnosticInfo = "AI Ready!"
                } catch (e: Exception) { diagnosticInfo = "Model Error: ${e.message}" }
            }
        }
    }

    val dictPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    diagnosticInfo = "Importing Dictionary..."; context.contentResolver.openInputStream(it)?.use { s -> dictionaryService.importFromStream(s) { p -> copyProgress = p } }
                    copyProgress = -1f; diagnosticInfo = "Dictionary Ready!"
                } catch (e: Exception) { diagnosticInfo = "Dict Error: ${e.message}"; copyProgress = -1f }
            }
        }
    }

    NavHost(navController = navController, startDestination = "landing") {
        composable("landing") {
            LandingPage(
                decks = decks, selectedDeck = selectedDeck, isGemmaReady = isGemmaReady,
                onDeckSelect = { 
                    selectedDeck = it; currentDeckNotes = ankiService.getPriorityNotesInDeck(it.id)
                    scope.launch { settingsService.setSelectedDeckId(it.id) }
                },
                onOpenSettings = { navController.navigate("settings") },
                onStartChat = { navController.navigate("chat") },
                onRequestAnki = { ankiLauncher.launch(modernPermission) }
            )
        }
        
        composable("settings") {
            SettingsView(
                isDarkMode = isDarkMode, useWordSpaces = useWordSpaces, diagnosticInfo = diagnosticInfo,
                onBack = { navController.popBackStack() },
                onToggleTheme = { scope.launch { settingsService.setDarkMode(it) } },
                onToggleSpaces = { scope.launch { settingsService.setUseWordSpaces(it) } },
                onImportModel = { modelPicker.launch("*/*") },
                onImportDict = { dictPicker.launch("*/*") }
            )
        }

        composable("chat") {
            ChatView(
                chatHistory = chatHistory, currentDeckNotes = currentDeckNotes, currentlySpeakingText = currentlySpeakingText,
                onBack = { navController.popBackStack() },
                onSendMessage = { input ->
                    scope.launch {
                        val vocab = currentDeckNotes.take(30).joinToString { it.fields.firstOrNull() ?: "" }
                        val prompt = "You are a Mandarin tutor. Chat naturally. Vocabulary: $vocab. IMMERSION: Speak Hanzi only. FORMAT: Use spaces between words. User: $input"
                        val response = gemmaService.generateResponse(prompt)
                        val newMessage = ChatMessage(userText = input, aiResponse = response, deckName = selectedDeck?.name)
                        chatDao.insertMessage(newMessage); chatHistory = chatHistory + newMessage
                        currentlySpeakingText = response; voiceService.speak(response)
                    }
                },
                onSpeak = { voiceService.speak(it); currentlySpeakingText = it },
                onStopSpeech = { voiceService.stop(); currentlySpeakingText = null },
                onWordClick = { word, extra -> 
                    selectedWord = word
                    matchingAnkiNote = currentDeckNotes.find { it.fields.firstOrNull() == word }
                    scope.launch { dictEntries = dictionaryService.lookup(word) }
                },
                onAnkiRate = { note, ease -> ankiService.answerNote(note.id, ease); Toast.makeText(context, "Rated OK", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    selectedWord?.let { word ->
        WordPopup(
            word = word, ankiNote = matchingAnkiNote, dictEntries = dictEntries,
            onAddToAnki = { entry ->
                scope.launch {
                    val deck = selectedDeck ?: decks.firstOrNull()
                    val model = noteModels.find { it.second.contains("Basic", true) } ?: noteModels.firstOrNull()
                    if (deck != null && model != null) {
                        if (ankiService.addNote(deck.id, model.first, listOf(entry.simplified, entry.pinyin, entry.definitions))) {
                            Toast.makeText(context, "Added!", Toast.LENGTH_SHORT).show()
                            currentDeckNotes = ankiService.getPriorityNotesInDeck(deck.id)
                        }
                    }
                }
            },
            onDismiss = { selectedWord = null }
        )
    }
}
