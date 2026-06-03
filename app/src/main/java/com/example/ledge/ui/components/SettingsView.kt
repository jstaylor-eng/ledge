package com.example.ledge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    isDarkMode: Boolean,
    useWordSpaces: Boolean,
    showAllPinyin: Boolean,
    speechSpeed: Float,
    diagnosticInfo: String,
    onBack: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onToggleSpaces: (Boolean) -> Unit,
    onTogglePinyin: (Boolean) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onImportModel: () -> Unit,
    onImportDict: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    trailingContent = { Switch(checked = isDarkMode, onCheckedChange = onToggleTheme) }
                )
                ListItem(
                    headlineContent = { Text("Show Word Spaces") },
                    trailingContent = { Switch(checked = useWordSpaces, onCheckedChange = onToggleSpaces) }
                )
                ListItem(
                    headlineContent = { Text("Always Show Pinyin") },
                    trailingContent = { Switch(checked = showAllPinyin, onCheckedChange = onTogglePinyin) }
                )
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Audio", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Speech Speed: ${"%.1f".format(speechSpeed)}x", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = speechSpeed,
                        onValueChange = onSetSpeed,
                        valueRange = 0.5f..1.5f,
                        steps = 9
                    )
                }
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("AI & Dictionary", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("AI Model") },
                    trailingContent = { Button(onClick = onImportModel) { Text("Update") } }
                )
                ListItem(
                    headlineContent = { Text("Full Dictionary") },
                    trailingContent = { Button(onClick = onImportDict) { Text("Import") } }
                )
                
                if (diagnosticInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Status: $diagnosticInfo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
