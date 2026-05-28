package com.example.ledge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledge.data.model.DictionaryEntry

@Composable
fun WordPopup(
    word: String,
    entries: List<DictionaryEntry>,
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
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(entries) { entry ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "[${entry.pinyin}]",
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.definitions,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Divider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    )
}
