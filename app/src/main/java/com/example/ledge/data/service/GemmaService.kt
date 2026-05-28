package com.example.ledge.data.service

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GemmaService(private val context: Context) {

    private var engine: Engine? = null
    private val MODEL_FILENAME = "gemma_model.litertlm"

    /**
     * Checks if the model is already in internal storage.
     */
    fun getPersistentModelPath(): String? {
        val file = File(context.filesDir, MODEL_FILENAME)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Copies a model from a Uri (like from a File Picker) to internal storage
     * so that the LiteRT library can read it.
     */
    suspend fun prepareModelFromUri(uri: Uri, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val destinationFile = File(context.filesDir, MODEL_FILENAME)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var bytesRead: Int
                var totalBytesRead = 0L
                val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileSize > 0) {
                        onProgress(totalBytesRead.toFloat() / fileSize.toFloat())
                    }
                }
            }
        }
        destinationFile.absolutePath
    }

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.Default) {
        val file = File(modelPath)
        if (!file.exists()) {
            throw Exception("Model file not found at $modelPath")
        }

        // Configure LiteRT-LM Engine
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(), // CPU is more stable for general devices
            maxNumTokens = 2048
        )

        engine = Engine(config)
        engine?.initialize()
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        try {
            val currentEngine = engine ?: return@withContext "AI not initialized"
            
            var finalResult = ""
            currentEngine.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { partial ->
                    finalResult += partial
                }
            }
            finalResult
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun isInitialized(): Boolean = engine != null

    fun shutdown() {
        engine?.close()
        engine = null
    }
}
