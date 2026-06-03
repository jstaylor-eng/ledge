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

    // Global State
    var decks by remember { mutableStateOf<List<AnkiDeck>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<AnkiDeck?>(null) }
    var selectedMode by remember { mutableStateOf(LessonMode.FREE_CHAT) }
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
    val showAllPinyin by settingsService.showAllPinyin.collectAsState(initial = false)
    
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
                decks = decks, selectedDeck = selectedDeck, selectedMode = selectedMode, isGemmaReady = isGemmaReady,
                onDeckSelect = { 
                    selectedDeck = it 
                    sessionVocab = ankiService.getSessionVocabulary(it.name)
                    scope.launch { settingsService.setSelectedDeckId(it.id) }
                },
                onModeSelect = { selectedMode = it },
                onOpenSettings = { navController.navigate("settings") },
                onStartChat = { navController.navigate("chat") },
                onRequestAnki = { ankiLauncher.launch(modernPermission) }
            )
        }
        
        composable("settings") {
            SettingsView(
                isDarkMode = isDarkMode, useWordSpaces = useWordSpaces, showAllPinyin = showAllPinyin, diagnosticInfo = diagnosticInfo,
                onBack = { navController.popBackStack() },
                onToggleTheme = { scope.launch { settingsService.setDarkMode(it) } },
                onToggleSpaces = { scope.launch { settingsService.setUseWordSpaces(it) } },
                onTogglePinyin = { scope.launch { settingsService.setShowAllPinyin(it) } },
                onImportModel = { modelPicker.launch("*/*") },
                onImportDict = { dictPicker.launch("*/*") }
            )
        }

        composable("chat") {
            ChatView(
                chatHistory = chatHistory, sessionVocab = sessionVocab, currentlySpeakingText = currentlySpeakingText,
                isMicPermissionGranted = hasMicPermission,
                showAllPinyin = showAllPinyin,
                transcription = pendingTranscription,
                onBack = { navController.popBackStack() },
                onSendMessage = { input ->
                    pendingTranscription = null
                    scope.launch {
                        // Behavioral Sync: Mark non-tapped Due words as GOOD
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
                        
                        val modeInstruction = when(selectedMode) {
                            LessonMode.DAILY_STORY -> "TELL A SHORT STORY using the vocab. Make it engaging."
                            LessonMode.INTENSIVE_REVIEW -> "Strictly quiz me on the DUE words. Don't use other complex Hanzi."
                            else -> "Lead a natural conversation."
                        }

                        val prompt = """
                            You are a proactive Mandarin tutor. 
                            GOAL: $modeInstruction
                            VOCAB: DUE Today: $dueWords. INTRODUCE: $newWords. BASELINE: $knownWords.
                            IMMERSION: Speak ONLY in Chinese characters. Use spaces between words. 
                            FORMAT: Provide your response as 'Hanzi | English Translation'.
                            User: $input
                        """.trimIndent()
                        
                        // Streaming UI Implementation
                        var streamedResponse = ""
                        val streamingMessage = ChatMessage(userText = input, aiResponse = "...", deckName = selectedDeck?.name)
                        chatHistory = chatHistory + streamingMessage
                        
                        gemmaService.streamResponse(prompt).collect { partial ->
                            streamedResponse += partial
                            chatHistory = chatHistory.dropLast(1) + streamingMessage.copy(aiResponse = streamedResponse)
                        }
                        
                        chatDao.insertMessage(streamingMessage.copy(aiResponse = streamedResponse))
                        currentlySpeakingText = streamedResponse
                        voiceService.speak(streamedResponse)
                        wordsTappedInSession.clear()
                    }
                },
                onTeachMe = {
                    scope.launch {
                        val newWordNote = sessionVocab[WordStatus.NEW]?.firstOrNull()
                        val targetWord = newWordNote?.fields?.firstOrNull() ?: "a useful idiom"
                        val knownWords = sessionVocab[WordStatus.KNOWN]?.take(20)?.joinToString { it.fields.firstOrNull() ?: "" } ?: ""
                        
                        val prompt = """
                            TEACH ME: I want to learn '$targetWord'.
                            1. Use it in 3 distinct example sentences.
                            2. Use ONLY simple Chinese characters I already know: $knownWords.
                            3. Explain it like I'm a student.
                            IMMERSION: Speak ONLY in Chinese characters. Use spaces between words. 
                            FORMAT: Provide your response as 'Hanzi | English Translation'.
                        """.trimIndent()

                        var streamedResponse = ""
                        val streamingMessage = ChatMessage(userText = "Teach me '$targetWord'", aiResponse = "...", deckName = selectedDeck?.name)
                        chatHistory = chatHistory + streamingMessage
                        
                        gemmaService.streamResponse(prompt).collect { partial ->
                            streamedResponse += partial
                            chatHistory = chatHistory.dropLast(1) + streamingMessage.copy(aiResponse = streamedResponse)
                        }
                        
                        chatDao.insertMessage(streamingMessage.copy(aiResponse = streamedResponse))
                        currentlySpeakingText = streamedResponse
                        voiceService.speak(streamedResponse)
                    }
                },
                onSpeak = { voiceService.speak(it); currentlySpeakingText = it },
                onStopSpeech = { voiceService.stop(); currentlySpeakingText = null },
                onWordLongClick = { word, note -> 
                    selectedWord = word
                    matchingAnkiNote = note
                    note?.let {
                        wordsTappedInSession.add(it.id)
                        ankiService.pushReview(it.id, 2)
                    }
                    scope.launch { dictEntries = dictionaryService.lookup(word) }
                },
                onTogglePinyin = { scope.launch { settingsService.setShowAllPinyin(it) } },
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
