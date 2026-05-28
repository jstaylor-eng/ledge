package com.example.ledge.data.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class VoiceService(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private var isSpeaking = false
    private var onSpeechStateChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                setupProgressListener()
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onSpeechStateChanged?.invoke(true)
            }
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onSpeechStateChanged?.invoke(false)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                onSpeechStateChanged?.invoke(false)
            }
        })
    }

    fun setSpeechListener(listener: (Boolean) -> Unit) {
        onSpeechStateChanged = listener
    }

    /**
     * Aggressively sanitizes text for Mandarin TTS.
     * Removes Markdown, Pinyin (Latin chars), and numbers to ensure
     * ONLY Chinese characters are read.
     */
    private fun sanitizeText(text: String): String {
        // 1. Remove Markdown special characters
        val noMarkdown = text.replace(Regex("[*#_>\\[\\]]"), " ")
        
        // 2. Remove Pinyin/English/Numbers (a-z, A-Z, 0-9)
        // This is key to ensuring it doesn't read "ni3 hao3" or "Hello"
        val noPinyin = noMarkdown.replace(Regex("[a-zA-Z0-9]"), "")

        // 3. Keep ONLY Chinese characters and standard punctuation
        val chineseRegex = Regex("[\\u4e00-\\u9fa5，。？！、：；“”‘’（）《》]")
        val matches = chineseRegex.findAll(noPinyin)
        val result = matches.joinToString("") { it.value }
        
        return result.ifBlank { "" }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            val speechText = sanitizeText(text)
            if (speechText.isNotEmpty()) {
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ledge_speech")
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "ledge_speech")
            }
        }
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        onSpeechStateChanged?.invoke(false)
    }

    fun isSpeakingNow(): Boolean = isSpeaking

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
