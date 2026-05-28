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
import com.example.ledge.data.model.DictionaryEntry

@Composable
fun WordPopup(
    word: String,
    entries: List<DictionaryEntry>,
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
            if (entries.isEmpty()) {
                Text("No definition found in offline dictionary.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(entries) { entry ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "[${entry.pinyin}]",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { onAddToAnki(entry) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Add to Anki", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                text = entry.definitions,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Divider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    )
}
