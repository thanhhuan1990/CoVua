package com.huanhuynh.covua.training

import com.huanhuynh.covua.i18n.t

/**
 * Lịch "Bài hôm nay" cố định (sinh bởi ContentPacker): mỗi ngày từ [startEpochDay] có sẵn 1 bài cho mỗi số nước
 * chiếu hết (1/2/3) – app chọn theo thứ trong tuần (RC daily_difficulty_by_weekday). Mọi người cùng ngày nhận cùng bài.
 * Thêm bài ở bản sau KHÔNG làm đổi các ngày đã qua vì lịch là danh sách cố định.
 */
data class DailySchedule(val startEpochDay: Long, val days: List<Map<Int, Int>>) {
    fun entry(day: Long): Map<Int, Int>? {
        if (days.isEmpty()) return null
        val i = Math.floorMod(day - startEpochDay, days.size.toLong()).toInt()
        return days[i]
    }
}

data class DailyPlan(
    val day: Long,
    val reviewCardIds: List<Long>,
    val dailyPuzzleId: Int,
    val dailyMateIn: Int,
    val extraPuzzleIds: List<Int>,
) {
    /** Phần ôn sổ tay có cần làm không (không có thẻ đến hạn → coi như xong). */
    val hasReview get() = reviewCardIds.isNotEmpty()
}

object SessionParts {
    const val REVIEW = 1
    const val DAILY = 2
    const val EXTRA = 4
    const val ALL = REVIEW or DAILY or EXTRA

    fun count(mask: Int, plan: DailyPlan): Int {
        var n = 0
        if (mask and REVIEW != 0 || !plan.hasReview) n++
        if (mask and DAILY != 0) n++
        if (mask and EXTRA != 0) n++
        return n
    }

    fun isComplete(mask: Int, plan: DailyPlan) = count(mask, plan) == 3
}

class DailyPlanner(
    /** Số nước chiếu hết theo thứ, Thứ Hai → Chủ nhật. RC "1,1,2,2,2,2,3". */
    private val weekdayMateIn: List<Int> = DEFAULT_WEEKDAY,
    private val reviewCards: Int = 3,
    private val extraPuzzles: Int = 2,
) {
    fun dailyPuzzle(schedule: DailySchedule, day: Long): Pair<Int, Int>? {
        val e = schedule.entry(day) ?: return null
        val want = weekdayMateIn[DayClock.dayOfWeek(day).value - 1]
        val key = e.keys.minByOrNull { kotlin.math.abs(it - want) * 10 + it } ?: return null
        return e[key]!! to key
    }

    /**
     * [dueCardIds]: thẻ đến hạn, sắp ưu tiên (cũ nhất trước). [pickExtra]: chọn bài vừa sức, nhận danh sách id loại trừ.
     */
    fun plan(day: Long, schedule: DailySchedule, dueCardIds: List<Long>, pickExtra: (Set<Int>) -> Int?): DailyPlan? {
        val (pid, mate) = dailyPuzzle(schedule, day) ?: return null
        val extras = ArrayList<Int>()
        repeat(extraPuzzles) { pickExtra(extras.toSet() + pid)?.let { extras += it } }
        return DailyPlan(day, dueCardIds.take(reviewCards), pid, mate, extras)
    }

    companion object {
        val DEFAULT_WEEKDAY = listOf(1, 1, 2, 2, 2, 2, 3)

        fun parseWeekday(rc: String?): List<Int> =
            rc?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 7 && it.all { v -> v in 1..4 } } ?: DEFAULT_WEEKDAY

        val WEEKDAY_NAMES: List<String> get() = when (com.huanhuynh.covua.i18n.I18n.lang) {
            com.huanhuynh.covua.i18n.Lang.EN -> listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            com.huanhuynh.covua.i18n.Lang.ZH -> listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            com.huanhuynh.covua.i18n.Lang.JA -> listOf("月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日", "日曜日")
            else -> listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật")
        }

        fun weekdayName(day: Long) = WEEKDAY_NAMES[DayClock.dayOfWeek(day).value - 1]
    }
}
