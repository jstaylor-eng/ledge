package com.example.ledge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledge.data.model.AnkiNote
import com.example.ledge.data.model.DictionaryEntry

@Composable
fun WordPopup(
    word: String,
    ankiNote: AnkiNote?,
    dictEntries: List<DictionaryEntry>,
    onAddToAnki: (DictionaryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text(text = word, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                if (ankiNote != null) {
                    Text("From Anki:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Fields: [0]=Hanzi, [1]=Pinyin, [2]=English
                            Text(text = ankiNote.fields.getOrNull(1) ?: "", fontWeight = FontWeight.Bold)
                            Text(text = ankiNote.fields.getOrNull(2) ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (dictEntries.isNotEmpty()) {
                    Text("Dictionary Definitions:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(dictEntries) { entry ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "[${entry.pinyin}]",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (ankiNote == null) {
                                        Button(
                                            onClick = { onAddToAnki(entry) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Add to Anki", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Text(text = entry.definitions, style = MaterialTheme.typography.bodyMedium)
                                Divider(modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                } else if (ankiNote == null) {
                    Text("No definition found offline. Try importing CC-CEDICT in settings.")
                }
            }
        }
    )
}
