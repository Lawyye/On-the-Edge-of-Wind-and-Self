package com.nutritiontracker.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechState(
    val listening: Boolean = false,
    val partial: String = "",
    val level: Float = 0f,
    val error: String? = null,
)

/**
 * Обёртка над системным распознаванием речи Android. Аудио не сохраняется:
 * приложение получает только распознанный текст.
 */
class SpeechController(private val context: Context, private val onFinal: (String) -> Unit) {
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable) {
            _state.value = SpeechState(error = "Распознавание речи на этом устройстве недоступно. Используйте системный диалог или введите текст.")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = SpeechState(listening = true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { _state.value = _state.value.copy(listening = false, level = 0f) }
            override fun onError(error: Int) {
                _state.value = SpeechState(error = errorText(error))
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = SpeechState()
                if (text.isNotBlank()) onFinal(text) else _state.value = SpeechState(error = "Речь не распознана. Попробуйте ещё раз.")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = _state.value.copy(partial = text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(recognizerIntent())
    }

    fun stop() {
        recognizer?.let {
            it.stopListening()
            it.cancel()
            it.destroy()
        }
        recognizer = null
        _state.value = _state.value.copy(listening = false, level = 0f)
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, 11 ->
            "Для распознавания нужен интернет, либо скачайте русский языковой пакет для офлайн-распознавания в настройках голосового ввода."
        SpeechRecognizer.ERROR_AUDIO -> "Не удалось получить звук с микрофона."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не распознана. Попробуйте ещё раз или введите текст."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание занято. Попробуйте через секунду."
        12, 13 -> "Русский язык для распознавания недоступен. Установите его в настройках голосового ввода."
        else -> "Ошибка распознавания (код $code). Попробуйте системный диалог или введите текст."
    }

    companion object {
        fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Что вы съели?")
        }
    }
}
