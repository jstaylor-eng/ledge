package com.example.ledge.data.service

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GemmaService(private val context: Context) {

    private var engine: Engine? = null
    private val MODEL_FILENAME = "gemma_model.litertlm"

    fun getPersistentModelPath(): String? {
        val file = File(context.filesDir, MODEL_FILENAME)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun prepareModelFromUri(uri: Uri, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val destinationFile = File(context.filesDir, MODEL_FILENAME)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(1024 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileSize > 0) onProgress(totalBytesRead.toFloat() / fileSize.toFloat())
                }
            }
        }
        destinationFile.absolutePath
    }

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.Default) {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            maxNumTokens = 2048
        )
        engine = Engine(config)
        engine?.initialize()
    }

    fun streamResponse(prompt: String): Flow<String> = flow {
        val currentEngine = engine ?: throw Exception("AI not initialized")
        currentEngine.createConversation().use { conversation ->
            // sendMessageAsync returns Flow<Message>. We extract the text content.
            conversation.sendMessageAsync(prompt).collect { partialMessage ->
                // In LiteRT-LM 0.11.0, the partial message itself IS the chunk of text
                // if it's being streamed.
                emit(partialMessage.toString()) 
            }
        }
    }

    suspend fun generateFullResponse(prompt: String): String = withContext(Dispatchers.Default) {
        try {
            val currentEngine = engine ?: return@withContext "AI not initialized"
            var full = ""
            currentEngine.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { partial ->
                    full += partial.toString()
                }
            }
            full
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun isInitialized(): Boolean = engine != null
    fun shutdown() { engine?.close(); engine = null }
}
