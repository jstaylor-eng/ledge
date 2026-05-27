package com.example.ledge.data.service

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GemmaService(private val context: Context) {

    private var llmInference: LlmInference? = null

    /**
     * Copies a model from a Uri (like from a File Picker) to internal storage
     * so that the MediaPipe library can read it.
     */
    suspend fun prepareModelFromUri(uri: Uri, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val destinationFile = File(context.filesDir, "gemma_model.bin")
        
        // If file already exists and is the same size, skip copy? 
        // For simplicity, let's just copy for now.
        
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

    fun initialize(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) {
            throw Exception("Model file not found at $modelPath")
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .setTemperature(0.7f)
            .setRandomSeed(42)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        try {
            llmInference?.generateResponse(prompt) ?: "LLM not initialized"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun isInitialized(): Boolean = llmInference != null
}
