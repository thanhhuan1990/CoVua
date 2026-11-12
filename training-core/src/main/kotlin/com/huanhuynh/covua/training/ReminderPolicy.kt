package com.huanhuynh.covua.training

import com.huanhuynh.covua.i18n.t

/**
 * Luật "Thầy nhắc" (thông báo cục bộ). Thuần logic để test; Worker chỉ đọc dữ liệu rồi hỏi các hàm ở đây.
 * - Tối đa 1 thông báo/ngày; không gửi trong giờ yên lặng; không gửi nếu hôm nay đã hoạt động.
 * - Bị bỏ qua [reduceAfter] lần liền → chỉ còn 2 lần/tuần (Thứ Ba, Thứ Sáu); [stopAfter] lần → dừng hẳn.
 */
class ReminderPolicy(
    private val quietStartMinute: Int = 21 * 60,
    private val quietEndMinute: Int = 7 * 60 + 30,
    private val reduceAfter: Int = 3,
    private val stopAfter: Int = 7,
) {
    enum class Frequency { DAILY, TWICE_WEEKLY, STOPPED }

    fun frequency(consecutiveIgnored: Int): Frequency = when {
        consecutiveIgnored >= stopAfter -> Frequency.STOPPED
        consecutiveIgnored >= reduceAfter -> Frequency.TWICE_WEEKLY
        else -> Frequency.DAILY
    }

    fun inQuiet(minute: Int): Boolean =
        if (quietStartMinute > quietEndMinute) minute >= quietStartMinute || minute < quietEndMinute
        else minute in quietStartMinute until quietEndMinute

    /** Giờ gửi thực tế: nếu giờ chọn rơi vào giờ yên lặng thì lùi về 30 phút trước giờ yên lặng. */
    fun effectiveMinute(target: Int): Int = if (inQuiet(target)) (quietStartMinute - 30).coerceAtLeast(0) else target

    fun shouldSend(
        day: Long,
        nowMinute: Int,
        enabled: Boolean,
        activeToday: Boolean,
        lastSentDay: Long?,
        consecutiveIgnored: Int,
    ): Boolean {
        if (!enabled || activeToday) return false
        if (lastSentDay == day) return false
        if (inQuiet(nowMinute)) return false
        return when (frequency(consecutiveIgnored)) {
            Frequency.STOPPED -> false
            Frequency.DAILY -> true
            Frequency.TWICE_WEEKLY -> DayClock.dayOfWeek(day).value in setOf(2, 5)
        }
    }

    /** Trung vị phút mở app (để "Theo giờ tôi hay mở app"); rỗng → [fallback]. */
    fun medianMinute(minutes: List<Int>, fallback: Int): Int {
        if (minutes.isEmpty()) return fallback
        val s = minutes.sorted()
        return s[s.size / 2]
    }

    enum class Type { REVIEW, GAME, DAILY, WEEK }
    data class Notice(val type: Type, val title: String, val text: String)

    /** Chọn nội dung theo thứ tự ưu tiên; [variant] để xoay câu (vd theo ngày). */
    fun pickContent(
        dueCards: Int,
        gameInProgress: String?,
        dailyText: String?,
        weekDone: Int?,
        variant: Int,
    ): Notice? {
        fun <T> v(vararg a: T) = a[Math.floorMod(variant, a.size)]
        return when {
            dueCards > 0 -> Notice(
                Type.REVIEW, t("Thầy giao bài ôn", "Review time", "老师布置复习", "復習の時間"),
                v(
                    t("Có $dueCards thế cờ hôm qua bạn đi chưa đúng – 2 phút thôi.", "$dueCards positions you got wrong are due – just 2 minutes.", "有${dueCards}个你走错的局面待复习——只要2分钟。", "間違えた局面が${dueCards}件、復習の時期です – たった2分。"),
                    t("Sổ tay có $dueCards thế đến hạn ôn – ôn lại để không lặp lỗi nhé.", "Your notebook has $dueCards positions to review – so you don't repeat mistakes.", "错题本里有${dueCards}个局面到期——复习一下，别再犯同样的错。", "ミスノートに復習する局面が${dueCards}件 – 同じミスをくり返さないように。"),
                    t("$dueCards thế cờ đang chờ bạn sửa – vào ôn với thầy 2 phút.", "$dueCards positions are waiting to be fixed – 2 minutes with your coach.", "${dueCards}个局面等你改正——和老师复习2分钟。", "${dueCards}件の局面が直されるのを待っています – 先生と2分。"),
                ),
            )
            gameInProgress != null -> Notice(Type.GAME, t("Ván cờ còn dang dở", "Unfinished game", "未下完的棋局", "指しかけの対局"), gameInProgress)
            dailyText != null -> Notice(Type.DAILY, t("Thầy giao bài hôm nay", "Today's lesson", "今日练习", "今日の課題"), dailyText)
            weekDone != null -> Notice(
                Type.WEEK, t("Lịch tập tuần này", "This week's practice", "本周练习", "今週の練習"),
                v(
                    t("Tuần này bạn đã tập $weekDone/7 ngày. Thêm một buổi nhé?", "You've practised $weekDone/7 days this week. One more session?", "本周已练习${weekDone}/7天。再来一次吧？", "今週は${weekDone}/7日練習しました。もう1回やりませんか？"),
                    t("Đã tập $weekDone ngày trong tuần – giữ nhịp nào.", "$weekDone days practised this week – keep it up.", "本周已练${weekDone}天——保持节奏。", "今週${weekDone}日練習 – この調子で。"),
                ),
            )
            else -> null
        }
    }

    companion object {
        fun parseQuietEnd(v: Double): Int = (v * 60).toInt()
    }
}
