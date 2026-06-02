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
import com.example.ledge.data.model.*
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
    val settingsServiceInstance = remember { settingsService }

    // Global State
    var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<AnkiDeck?>(null) }
    var sessionVocab by remember { mutableStateOf<Map<WordStatus, List<AnkiNote>>>(emptyMap()) }
    var noteModels by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isGemmaReady by remember { mutableStateOf(false) }
    var diagnosticInfo by remember { mutableStateOf("") }
    var copyProgress by remember { mutableStateOf(-1f) }

    // Session Tracking
    val wordsTappedInSession = remember { mutableSetOf<Long>() }

    // Dictionary popup state
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var matchingAnkiNote by remember { mutableStateOf<AnkiNote?>(null) }
    var dictEntries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var currentlySpeakingText by remember { mutableStateOf<String?>(null) }

    // STT helper
    var pendingTranscription by remember { mutableStateOf<String?>(null) }

    val useWordSpaces by settingsService.useWordSpaces.collectAsState(initial = true)
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
        voiceService.setSpeechListener { if (!it) currentlySpeakingText = null }
    }

    LaunchedEffect(Unit) {
        chatHistory = chatDao.getAllMessages()
        val path = gemmaService.getPersistentModelPath()
        if (path != null) {
            try { gemmaService.initialize(path); isGemmaReady = true } 
            catch (e: Exception) { diagnosticInfo = "AI init error: ${e.message}" }
        }
        
        if (hasAnkiPermission) {
            ankiService.getDecks().onSuccess { availableDecks ->
                decks = availableDecks
                noteModels = ankiService.getModels()
                val savedDeckId = settingsService.selectedDeckId.first()
                if (savedDeckId != null) {
                    availableDecks.find { it.id == savedDeckId }?.let {
                        selectedDeck = it
                        sessionVocab = ankiService.getSessionVocabulary(it.name)
                    }
                }
            }
        }
    }

    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAnkiPermission = isGranted
        if (isGranted) ankiService.getDecks().onSuccess { decks = it }
    }
    
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMicPermission = it }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                diagnosticInfo = "Copying model..."; val path = gemmaService.prepareModelFromUri(it) { p -> copyProgress = p }
                copyProgress = -1f; gemmaService.initialize(path); isGemmaReady = true; diagnosticInfo = "AI Ready!"
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
                    selectedDeck = it 
                    sessionVocab = ankiService.getSessionVocabulary(it.name)
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
                chatHistory = chatHistory, sessionVocab = sessionVocab, currentlySpeakingText = currentlySpeakingText,
                isMicPermissionGranted = hasMicPermission,
                transcription = pendingTranscription,
                onBack = { navController.popBackStack() },
                onSendMessage = { input ->
                    pendingTranscription = null
                    scope.launch {
                        val lastAiResponse = chatHistory.lastOrNull()?.aiResponse ?: ""
                        sessionVocab[WordStatus.DUE]?.forEach { note ->
                            val word = note.fields.firstOrNull() ?: ""
                            if (lastAiResponse.contains(word) && !wordsTappedInSession.contains(note.id)) {
                                ankiService.pushReview(note.id, 3) 
                            }
                        }

                        val dueWords = sessionVocab[WordStatus.DUE]?.joinToString { it.fields.firstOrNull() ?: "" } ?: ""
                        val newWords = sessionVocab[WordStatus.NEW]?.take(5)?.joinToString { it.fields.firstOrNull() ?: "" } ?: ""
                        val knownWords = sessionVocab[WordStatus.KNOWN]?.take(20)?.joinToString { it.fields.firstOrNull() ?: "" } ?: ""
                        
                        val prompt = """
                            You are a proactive Mandarin tutor. 
                            GOAL: Lead an immersive conversation. 
                            1. Priority: Review words due today: $dueWords. 
                            2. Intro: If I do well, naturally use a new word: $newWords.
                            3. Simplicity: If I'm stuck, explain using these words I know: $knownWords.
                            IMMERSION: Speak ONLY in Chinese characters. Use spaces between words. 
                            User: $input
                        """.trimIndent()
                        
                        val response = gemmaService.generateResponse(prompt)
                        val newMessage = ChatMessage(userText = input, aiResponse = response, deckName = selectedDeck?.name)
                        chatDao.insertMessage(newMessage); chatHistory = chatHistory + newMessage
                        currentlySpeakingText = response; voiceService.speak(response)
                        wordsTappedInSession.clear()
                    }
                },
                onSpeak = { voiceService.speak(it); currentlySpeakingText = it },
                onStopSpeech = { voiceService.stop(); currentlySpeakingText = null },
                onWordClick = { word, note -> 
                    selectedWord = word
                    matchingAnkiNote = note
                    note?.let {
                        wordsTappedInSession.add(it.id)
                        ankiService.pushReview(it.id, 2)
                    }
                    scope.launch { dictEntries = dictionaryService.lookup(word) }
                },
                onRequestMic = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                onStartMic = { voiceService.startListening { pendingTranscription = it } }
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
                            sessionVocab = ankiService.getSessionVocabulary(deck.name)
                        }
                    }
                }
            },
            onDismiss = { selectedWord = null }
        )
    }
}
