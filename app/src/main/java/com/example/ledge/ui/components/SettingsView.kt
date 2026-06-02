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
    diagnosticInfo: String,
    onBack: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onToggleSpaces: (Boolean) -> Unit,
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
                    trailingContent = {
                        Switch(checked = isDarkMode, onCheckedChange = onToggleTheme)
                    }
                )
                ListItem(
                    headlineContent = { Text("Show Word Spaces") },
                    supportingContent = { Text("Helps with word boundaries and lookups") },
                    trailingContent = {
                        Switch(checked = useWordSpaces, onCheckedChange = onToggleSpaces)
                    }
                )
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("AI & Content", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("AI Model") },
                    supportingContent = { Text("Select or update Gemma .litertlm file") },
                    trailingContent = {
                        Button(onClick = onImportModel) { Text("Update") }
                    }
                )
                ListItem(
                    headlineContent = { Text("Dictionary") },
                    supportingContent = { Text("Import full CC-CEDICT (cedict.txt)") },
                    trailingContent = {
                        Button(onClick = onImportDict) { Text("Import") }
                    }
                )
                
                if (diagnosticInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Status: $diagnosticInfo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
