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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPage(
    decks: List<AnkiDeck>,
    selectedDeck: AnkiDeck?,
    isGemmaReady: Boolean,
    onDeckSelect: (AnkiDeck) -> Unit,
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
        
        Spacer(modifier = Modifier.height(48.dp))

        if (decks.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Button(onClick = onRequestAnki) {
                    Text("Connect to AnkiDroid")
                }
            }
        } else {
            Text("Select Your Study Deck:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(decks) { deck ->
                    val isSelected = selectedDeck?.id == deck.id
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { onDeckSelect(deck) },
                        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = deck.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (isSelected) Text("✅", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = isGemmaReady && selectedDeck != null
            ) {
                Text("Start Learning Session")
            }
            if (!isGemmaReady) {
                Text("AI Engine not ready. Go to Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
