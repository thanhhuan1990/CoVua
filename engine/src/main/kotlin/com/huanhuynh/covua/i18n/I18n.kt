package com.huanhuynh.covua.i18n

/** Ngôn ngữ hiển thị (Việt mặc định, English, 中文简体, 日本語). */
enum class Lang(val tag: String) {
    VI("vi"), EN("en"), ZH("zh"), JA("ja");

    companion object {
        fun of(tag: String?): Lang = entries.firstOrNull { it.tag == tag?.take(2) } ?: VI
    }
}

/** Ngôn ngữ hiện tại – app đặt khi khởi động / khi người dùng đổi. Module thuần Kotlin đọc từ đây. */
object I18n {
    @Volatile var lang: Lang = Lang.VI
}

/** Chọn chuỗi theo ngôn ngữ hiện tại. Dùng template Kotlin như thường: t("Còn $n", "$n left", "剩$n", "残り${n}"). */
fun t(vi: String, en: String, zh: String, ja: String = en): String = when (I18n.lang) {
    Lang.EN -> en
    Lang.ZH -> zh
    Lang.JA -> ja
    Lang.VI -> vi
}
