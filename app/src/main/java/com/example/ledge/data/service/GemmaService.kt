package com.example.ledge.data.service

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaService(private val context: Context) {

    private var llmInference: LlmInference? = null

    fun initialize(modelPath: String) {
        if (!File(modelPath).exists()) {
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
