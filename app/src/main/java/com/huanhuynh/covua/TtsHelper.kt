package com.huanhuynh.covua

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Đọc lời thầy bằng giọng tiếng Việt của máy (offline nếu máy có gói giọng nói). Khởi tạo lười. */
class TtsHelper(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var lang: com.huanhuynh.covua.i18n.Lang? = null

    private fun localeOf(l: com.huanhuynh.covua.i18n.Lang) = when (l) {
        com.huanhuynh.covua.i18n.Lang.EN -> Locale.US
        com.huanhuynh.covua.i18n.Lang.ZH -> Locale.SIMPLIFIED_CHINESE
        com.huanhuynh.covua.i18n.Lang.JA -> Locale.JAPANESE
        com.huanhuynh.covua.i18n.Lang.VI -> Locale("vi", "VN")
    }

    fun speak(text: String) {
        val t = tts
        if (t == null) {
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    lang = com.huanhuynh.covua.i18n.I18n.lang
                    tts?.language = localeOf(lang!!)
                    tts?.setSpeechRate(1.05f)
                    pending?.let { say(it) }
                    pending = null
                }
            }
            return
        }
        if (ready) say(text) else pending = text
    }

    private fun say(text: String) {
        val l = com.huanhuynh.covua.i18n.I18n.lang
        if (l != lang) { lang = l; tts?.language = localeOf(l) }
        tts?.speak(text.replace("–", ",").replace(Regex("\\(([^)]*)\\)"), ""), TextToSpeech.QUEUE_FLUSH, null, "coach")
    }

    fun stop() { tts?.stop() }
}
