package com.example.ledge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ledge.data.model.AnkiDeck

@Composable
fun LandingPage(
    decks: List<AnkiDeck>,
    selectedDeck: AnkiDeck?,
    isGemmaReady: Boolean,
    diagnosticInfo: String,
    isDarkMode: Boolean,
    onDeckSelect: (AnkiDeck) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onStartChat: () -> Unit,
    onRequestAnki: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Ledge", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Offline AI Language Tutor", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        
        if (diagnosticInfo.isNotEmpty()) {
            Text("Status: $diagnosticInfo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Settings
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", modifier = Modifier.weight(1f))
                Switch(checked = isDarkMode, onCheckedChange = onToggleTheme)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (decks.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Button(onClick = onRequestAnki) {
                    Text("Connect to AnkiDroid")
                }
            }
        } else {
            Text("Choose a Deck to Study:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(decks) { deck ->
                    val isSelected = selectedDeck?.id == deck.id
                    OutlinedButton(
                        onClick = { onDeckSelect(deck) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(deck.name)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isGemmaReady && selectedDeck != null
            ) {
                Text("Start Chatting")
            }
            if (!isGemmaReady) {
                Text("Please initialize AI in settings first", style = MaterialTheme.typography.labelSmall, color = Color.Red)
            }
        }
    }
}
