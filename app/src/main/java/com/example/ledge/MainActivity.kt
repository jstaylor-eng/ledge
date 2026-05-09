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
    var modelPath by remember { mutableStateOf("/sdcard/Documents/gemma-2b-it-cpu-int4.bin") }
    var chatInput by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var isGemmaReady by remember { mutableStateOf(false) }
    var diagnosticInfo by remember { mutableStateOf("") }

    var hasAnkiPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, "com.ichi2.anki.permission.READ_WRITE_PERMISSION") == PackageManager.PERMISSION_GRANTED)
    }
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasAnkiPermission = isGranted
        diagnosticInfo = if (isGranted) "Handshake Success!" else "System Denied Access"
        if (isGranted) {
            ankiService.getDecks().onSuccess { decks = it }.onFailure { diagnosticInfo = it.message ?: "Fetch failed" }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMicPermission = it }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Ledge: Offline AI Tutor", style = MaterialTheme.typography.headlineMedium)
        
        // Diagnostic Status
        val ankiPkg = ankiService.getAnkiPackageName()
        if (ankiPkg == null) {
            Text("⚠️ AnkiDroid not detected!", color = Color.Red)
        } else {
            Text("✅ Found AnkiDroid ($ankiPkg)", color = Color(0xFF388E3C))
        }
        
        if (diagnosticInfo.isNotEmpty()) {
            Text("Status: $diagnosticInfo", color = Color.Magenta, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasAnkiPermission || decks.isEmpty()) {
            Text("Step 1: Connection", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { 
                ankiLauncher.launch("com.ichi2.anki.permission.READ_WRITE_PERMISSION")
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Auto-Request Access")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(onClick = { 
                ankiService.getDecks().onSuccess {
                    decks = it
                    hasAnkiPermission = true
                    diagnosticInfo = "Success! Decks loaded."
                }.onFailure {
                    diagnosticInfo = it.message ?: "Unknown Error"
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Try Fetching Directly")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("If nothing happens above:", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { 
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                Text("Open App Settings (Grant Manually)")
            }
        } else {
            // Setup Section
            if (!isGemmaReady) {
                Text("Step 2: Initialize AI", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = modelPath,
                    onValueChange = { modelPath = it },
                    label = { Text("Gemma Model Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    try {
                        gemmaService.initialize(modelPath)
                        isGemmaReady = true
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Initialize Gemma")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = "Select Deck Context:")
                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(decks) { deck ->
                        val isSelected = selectedDeck?.id == deck.id
                        TextButton(
                            onClick = { selectedDeck = deck },
                            colors = if (isSelected) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.textButtonColors()
                        ) {
                            Text(deck.name)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 AI Active | Deck: ${selectedDeck?.name ?: "None"}", modifier = Modifier.weight(1f))
                    Button(onClick = { isGemmaReady = false }) { Text("Settings") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chat Interface
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatHistory) { (user, ai) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("User: $user", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI: $ai", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { voiceService.speak(ai) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                            }
                        }
                        Divider()
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Talk to tutor...") }
                )
                IconButton(onClick = {
                    if (hasMicPermission) {
                        voiceService.startListening { chatInput = it }
                    } else {
                        micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text("🎤")
                }
                Button(onClick = {
                    val input = chatInput
                    chatInput = ""
                    scope.launch {
                        val vocab = selectedDeck?.let { 
                            ankiService.getNotesInDeck(it.id).take(20).joinToString { note -> note.fields.firstOrNull() ?: "" }
                        } ?: ""
                        val prompt = "You are a Mandarin tutor. Use these words: $vocab. User: $input"
                        val response = gemmaService.generateResponse(prompt)
                        chatHistory = chatHistory + (input to response)
                        voiceService.speak(response)
                    }
                }, enabled = isGemmaReady) {
                    Text("Send")
                }
            }
        }
    }
}
