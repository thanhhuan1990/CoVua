package com.huanhuynh.covua.training

/**
 * Lịch tập (chuỗi ngày mềm). Tính lại hoàn toàn từ tập ngày có hoạt động – không lưu biến đếm.
 *
 * - Mỗi tuần (từ Thứ Hai) có [freeRestPerWeek] ngày nghỉ tự động: ngày lỡ được tự dùng làm ngày nghỉ, chuỗi giữ nguyên
 *   (ngày nghỉ không cộng vào số ngày).
 * - Lỡ quá số ngày nghỉ → chuỗi TẠM DỪNG (không về 0). Nếu lỡ tối đa [pauseDays] ngày liền rồi hoạt động lại
 *   (chậm nhất ngày thứ pauseDays+1 kể từ ngày lỡ đầu) thì chuỗi nối tiếp; các ngày lỡ không được cộng.
 * - Quá [pauseDays] ngày → về 0. Kỷ lục giữ nguyên.
 * - Hôm nay chưa hoạt động không bị tính lỡ. Ngày ở tương lai (chỉnh lùi giờ máy) bị bỏ qua.
 */
class StreakCalculator(private val freeRestPerWeek: Int = 2, private val pauseDays: Int = 3) {

    enum class Status { NONE, ACTIVE, PAUSED }
    enum class Dot { DONE, REST, MISSED, TODAY_PENDING, FUTURE, NONE }

    data class State(
        val current: Int,
        val best: Int,
        val status: Status,
        /** Các ngày nghỉ tự động đã dùng (để ghi streak_freeze / vẽ lịch). */
        val restDays: Set<Long>,
        /** Số ngày hoạt động khác nhau (để biết "từ ngày hoạt động thứ 2"). */
        val activeDayCount: Int,
        /** Ngày đầu tiên của đợt tạm dừng hiện tại (nếu đang PAUSED). */
        val pausedSince: Long?,
    )

    fun compute(activeDays: Set<Long>, today: Long): State {
        val days = activeDays.filter { it <= today }.toSortedSet()
        if (days.isEmpty()) return State(0, 0, Status.NONE, emptySet(), 0, null)
        var current = 0
        var best = 0
        var status = Status.NONE
        var pausedSince: Long? = null
        val rest = HashSet<Long>()
        val restPerWeek = HashMap<Long, Int>()
        var d = days.first()
        while (d <= today) {
            val active = d in days
            when {
                active -> {
                    current++
                    status = Status.ACTIVE
                    pausedSince = null
                    if (current > best) best = current
                }
                d == today -> Unit // hôm nay còn thời gian
                status == Status.ACTIVE -> {
                    val wk = DayClock.weekStart(d)
                    val used = restPerWeek[wk] ?: 0
                    if (used < freeRestPerWeek) {
                        restPerWeek[wk] = used + 1
                        rest += d
                    } else {
                        status = Status.PAUSED
                        pausedSince = d
                    }
                }
                status == Status.PAUSED -> {
                    if (d - pausedSince!! + 1 > pauseDays) {
                        current = 0
                        status = Status.NONE
                        pausedSince = null
                    }
                }
                else -> Unit
            }
            d++
        }
        // Đang tạm dừng mà hôm nay đã quá hạn (hôm nay chưa hoạt động)
        if (status == Status.PAUSED && pausedSince != null && today - pausedSince > pauseDays) {
            current = 0; status = Status.NONE; pausedSince = null
        }
        return State(current, best, status, rest, days.size, pausedSince)
    }

    /** 7 chấm tuần chứa [today] (Thứ Hai → Chủ nhật). */
    fun weekDots(state: State, activeDays: Set<Long>, today: Long): List<Dot> {
        val start = DayClock.weekStart(today)
        val first = activeDays.filter { it <= today }.minOrNull()
        return (0 until 7).map { i ->
            val d = start + i
            when {
                d > today -> Dot.FUTURE
                d in activeDays -> Dot.DONE
                d in state.restDays -> Dot.REST
                d == today -> Dot.TODAY_PENDING
                first == null || d < first -> Dot.NONE
                else -> Dot.MISSED
            }
        }
    }

    companion object {
        val MILESTONES = listOf(3, 7, 14, 30, 100, 365)
    }
}
