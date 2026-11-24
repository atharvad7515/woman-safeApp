package com.womansafe.womanapp.sensor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoskVoiceDetector(
    private val context: Context,
    private var triggerWord: String = "ok ok ok",
    private val onTrigger: () -> Unit
) : RecognitionListener {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private val TAG = "VoskVoiceDetector"

    init {
        initModel()
    }

    fun updateTriggerWord(newWord: String) {
        val oldWord = this.triggerWord
        this.triggerWord = newWord.lowercase().trim()
        
        if (oldWord == this.triggerWord && speechService != null) return

        Log.d(TAG, "Updating trigger word to: $triggerWord")
        
        stop()
        
        Handler(Looper.getMainLooper()).postDelayed({
            startListening()
        }, 500)
    }

    private fun initModel() {
        Log.d(TAG, "Starting Vosk model initialization...")
        StorageService.unpack(context, "model-en-in", "vosk-model",
            { model: Model ->
                this.model = model
                Log.d(TAG, "✅ Model loaded successfully!")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Voice System Ready: Say '$triggerWord'", Toast.LENGTH_SHORT).show()
                }
                startListening()
            },
            { exception: IOException ->
                Log.e(TAG, "❌ Model Unpack Error: ${exception.message}")
            })
    }

    private fun startListening() {
        val model = this.model ?: return
        if (speechService != null) return
        
        try {
            val recognizer = Recognizer(model, 16000.0f, "[\"$triggerWord\", \"[unk]\"]")
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            Log.d(TAG, "🎙️ MICROPHONE ACTIVE! Listening for '$triggerWord'...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Recognizer init failed: ${e.message}")
        }
    }

    fun resume() {
        Log.d(TAG, "Resuming voice detection...")
        startListening()
    }

    override fun onResult(hypothesis: String) {
        if (hypothesis.contains(triggerWord)) {
            Log.d(TAG, "🎯 TRIGGER!")
            Handler(Looper.getMainLooper()).post { 
                Toast.makeText(context, "Voice Trigger Detected!", Toast.LENGTH_SHORT).show()
                onTrigger() 
            }
        }
    }

    override fun onPartialResult(hypothesis: String) {
        if (hypothesis.contains(triggerWord)) {
            Log.d(TAG, "🎯 PARTIAL TRIGGER!")
            Handler(Looper.getMainLooper()).post { onTrigger() }
        }
    }

    override fun onFinalResult(hypothesis: String) {
        if (hypothesis.contains(triggerWord)) {
            Handler(Looper.getMainLooper()).post { onTrigger() }
        }
    }

    override fun onError(exception: Exception) {
        Log.e(TAG, "Vosk Error: ${exception.message}")
    }

    override fun onTimeout() {}

    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }
}
