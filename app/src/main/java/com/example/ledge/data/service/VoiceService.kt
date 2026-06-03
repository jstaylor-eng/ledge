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
    private var currentRate = 1.0f

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
                tts?.setSpeechRate(currentRate)
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

    fun setSpeechRate(rate: Float) {
        currentRate = rate
        if (isTtsReady) {
            tts?.setSpeechRate(rate)
        }
    }

    private fun sanitizeText(text: String): String {
        val clean = text.split("|").firstOrNull() ?: text
        val noMarkdown = clean.replace(Regex("[*#_>\\[\\]]"), " ")
        val noPinyin = noMarkdown.replace(Regex("[a-zA-Z0-9]"), "")
        val chineseRegex = Regex("[\\u4e00-\\u9fa5，。？！、：；“”‘’（）《》]")
        val matches = chineseRegex.findAll(noPinyin)
        return matches.joinToString("") { it.value }
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
