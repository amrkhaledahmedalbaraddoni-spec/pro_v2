package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale

/**
 * مدير الشروح النصية الحية للمكالمات (Live Captions)
 * يعمل محلياً عبر محرك التعرف الصوتي في النظام دون أي اعتمادية على خدمات سحابية مغلقة،
 * مما يحافظ على التشفير التام وسيادة البيانات (E2EE Sovereign).
 */
class LiveCaptionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isProcessing = false
    private var currentLanguage: String = Locale.getDefault().language

    interface Listener {
        fun onCaption(text: String, isFinal: Boolean, language: String)
        fun onError(message: String)
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    /**
     * بدء تفريغ الكلام المباشر في المكالمة
     */
    fun start(listener: Listener) {
        this.listener = listener
        if (isProcessing) return

        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    listener.onError("التعرف على الصوت غير متاح على هذا الجهاز")
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        
                        override fun onError(error: Int) {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "لم يتم التقاط كلام واضح"
                                SpeechRecognizer.ERROR_NETWORK -> "خطأ في الاتصال المحلي"
                                SpeechRecognizer.ERROR_AUDIO -> "خطأ في مدخل الصوت"
                                else -> "خطأ في محرك التعرف: $error"
                            }
                            listener.onError(msg)
                            if (isProcessing) {
                                mainHandler.postDelayed({ restartListening() }, 1000)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                listener.onCaption(text, isFinal = true, language = currentLanguage)
                            }
                            if (isProcessing) {
                                restartListening()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                listener.onCaption(text, isFinal = false, language = currentLanguage)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                startListening()
                isProcessing = true
            } catch (e: Exception) {
                Log.e("LiveCaptionManager", "Failed to start live captions", e)
                listener.onError("فشل بدء الشروح الحية: ${e.message}")
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun restartListening() {
        if (!isProcessing) return
        try {
            speechRecognizer?.cancel()
            startListening()
        } catch (e: Exception) {
            Log.w("LiveCaptionManager", "Restart listening failed: ${e.message}")
        }
    }

    /**
     * إيقاف الشروح النصية الحية
     */
    fun stop() {
        isProcessing = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w("LiveCaptionManager", "Error stopping recognizer", e)
            } finally {
                speechRecognizer = null
            }
        }
    }
}
