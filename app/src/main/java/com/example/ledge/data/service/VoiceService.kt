package com.example.ledge.data.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.*

class VoiceService(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(context, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    /**
     * Sanitizes text by removing markdown characters and non-Chinese text 
     * to prevent TTS from reading out formatting or metadata.
     */
    private fun sanitizeText(text: String): String {
        // 1. Remove Markdown special characters (*, #, _, [, ], etc)
        var clean = text.replace(Regex("[*#_>\\[\\]]"), " ")
        
        // 2. Extract only Chinese characters and standard punctuation
        // This Regex matches CJK Unified Ideographs and common punctuation
        val chineseRegex = Regex("[\\u4e00-\\u9fa5，。？！、：；“”‘’（）《》]")
        val matches = chineseRegex.findAll(clean)
        val result = matches.joinToString("") { it.value }
        
        return if (result.isBlank()) clean else result
    }

    fun speak(text: String) {
        if (isTtsReady) {
            val speechText = sanitizeText(text)
            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                data?.firstOrNull()?.let { onResult(it) }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
