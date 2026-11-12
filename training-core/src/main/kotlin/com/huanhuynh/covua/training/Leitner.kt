package com.huanhuynh.covua.training

import kotlin.math.roundToInt
import kotlin.random.Random

enum class CardStatus { ACTIVE, GRADUATED, SUSPENDED }

data class CardSchedule(val box: Int, val dueDay: Long, val reps: Int, val lapses: Int, val status: CardStatus)

/**
 * Lịch ôn Leitner 5 ngăn. Khoảng cách (ngày) tới lần ôn sau theo ngăn: mặc định 1,3,7,16,35 (RC review_intervals_days).
 * Đúng → lên 1 ngăn; đúng ở ngăn 5 → GRADUATED ("Đã sửa"). Sai → về ngăn 1, lapses+1.
 * Fuzz ±10% (làm tròn, tối thiểu 1 ngày) để thẻ không dồn một ngày.
 */
class LeitnerScheduler(
    val intervals: List<Int> = DEFAULT_INTERVALS,
    private val random: Random = Random.Default,
    private val fuzz: Double = 0.10,
) {
    init { require(intervals.size == 5 && intervals.all { it >= 1 }) }

    fun interval(box: Int): Int {
        val base = intervals[(box - 1).coerceIn(0, 4)]
        if (fuzz <= 0.0 || base <= 1) return base
        val f = 1.0 + (random.nextDouble() * 2 - 1) * fuzz
        return (base * f).roundToInt().coerceAtLeast(1)
    }

    /** Thẻ mới tạo ở ngăn [box] (1 hoặc 2), đến hạn sau [interval] của ngăn đó tính từ [today]. Ngăn 1 → mai. */
    fun newCard(box: Int, today: Long): CardSchedule = CardSchedule(box, today + interval(box), 0, 0, CardStatus.ACTIVE)

    fun answer(current: CardSchedule, correct: Boolean, today: Long): CardSchedule {
        if (!correct) return CardSchedule(1, today + interval(1), current.reps + 1, current.lapses + 1, CardStatus.ACTIVE)
        if (current.box >= 5) return current.copy(reps = current.reps + 1, status = CardStatus.GRADUATED, dueDay = Long.MAX_VALUE / 4)
        val nb = current.box + 1
        return CardSchedule(nb, today + interval(nb), current.reps + 1, current.lapses, CardStatus.ACTIVE)
    }

    /** Gặp lại đúng lỗi cũ trong ván mới: về ngăn 1, lapses+1, đến hạn mai. */
    fun relapse(current: CardSchedule, today: Long): CardSchedule =
        CardSchedule(1, today + 1, current.reps, current.lapses + 1, CardStatus.ACTIVE)

    companion object {
        val DEFAULT_INTERVALS = listOf(1, 3, 7, 16, 35)

        /** Đọc "1,3,7,16,35"; sai định dạng → mặc định. */
        fun parse(rc: String?): List<Int> =
            rc?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 5 && it.all { v -> v >= 1 } } ?: DEFAULT_INTERVALS
    }
}
