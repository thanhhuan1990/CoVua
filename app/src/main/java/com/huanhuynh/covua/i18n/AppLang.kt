package com.huanhuynh.covua.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Ngôn ngữ trong app (vi / en / zh / ja) – per-app language của AndroidX AppCompat.
 * Lựa chọn còn được lưu vào SharedPreferences để Worker/thông báo (không có Activity) cũng đúng ngôn ngữ.
 * Chuỗi giao diện dùng [t] (module engine); res/values-en, values-zh, values-ja chỉ chứa tên app.
 */
object AppLang {
    val SUPPORTED = listOf("vi", "en", "zh", "ja")
    private const val PREFS = "app_lang"
    private const val KEY = "lang"

    private fun tagOf(lang: String) = if (lang == "zh") "zh-CN" else lang

    fun label(lang: String) = when (lang) {
        "en" -> "English"
        "zh" -> "中文（简体）"
        "ja" -> "日本語"
        else -> "Tiếng Việt"
    }

    fun shortLabel(lang: String) = when (lang) {
        "en" -> "English"
        "zh" -> "中文"
        "ja" -> "日本語"
        else -> "Tiếng Việt"
    }

    /** Đã chọn ngôn ngữ (màn chọn lần đầu đã qua). */
    fun chosen(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    /** Ngôn ngữ đang dùng: đã chọn trong app → AppCompat → ngôn ngữ máy (không hỗ trợ → tiếng Việt). */
    fun current(context: Context): String {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { if (it in SUPPORTED) return it }
        val app = AppCompatDelegate.getApplicationLocales()
        val lang = if (!app.isEmpty) app[0]?.language else LocaleListCompat.getAdjustedDefault()[0]?.language
        return lang?.takeIf { it in SUPPORTED } ?: "vi"
    }

    /** Gợi ý ở màn chọn lần đầu: ngôn ngữ máy nếu hỗ trợ, ngược lại English. */
    fun suggested(): String {
        val lang = LocaleListCompat.getAdjustedDefault()[0]?.language
        return lang?.takeIf { it in SUPPORTED } ?: "en"
    }

    /** Đồng bộ [I18n.lang] với lựa chọn đã lưu (gọi ở Application.onCreate và Activity.onCreate). */
    fun sync(context: Context) { I18n.lang = Lang.of(current(context)) }

    /** Đổi ngôn ngữ: lưu, áp dụng ngay cho chuỗi [t]; Activity tự tạo lại nếu locale đổi. */
    fun apply(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
        I18n.lang = Lang.of(lang)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagOf(lang)))
    }
}
