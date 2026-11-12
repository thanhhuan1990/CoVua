package com.huanhuynh.covua.ads

import android.content.Context
import java.util.Calendar

/**
 * Bộ đếm theo ngày lưu trong SharedPreferences để cap "tối đa N lần/ngày"
 * không bị reset khi process bị kill (người dùng mở app nhiều lần/ngày).
 */
class DailyCounter(context: Context, private val name: String) {
    private val prefs = context.applicationContext.getSharedPreferences("ad_caps", Context.MODE_PRIVATE)

    private fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    fun get(): Int =
        if (prefs.getInt("${name}_day", -1) == todayKey()) prefs.getInt("${name}_count", 0) else 0

    fun increment() {
        prefs.edit()
            .putInt("${name}_day", todayKey())
            .putInt("${name}_count", get() + 1)
            .apply()
    }
}
