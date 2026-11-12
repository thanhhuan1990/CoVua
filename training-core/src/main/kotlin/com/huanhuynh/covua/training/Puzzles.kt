package com.huanhuynh.covua.training

import kotlin.random.Random

/** Thông tin bài tập cần cho việc chọn bài (không cần FEN). */
data class PuzzleMeta(val id: Int, val difficulty: Int, val theme: String, val mateIn: Int)

/**
 * Chọn "Bài vừa sức": bài CHƯA giải ở đúng bậc, ưu tiên chủ đề ít gặp gần đây.
 * Hết bài ở bậc đó → bậc kề (gần nhất, ưu tiên bậc dưới khi cách đều). Hết cả kho → bài đã giải lâu nhất.
 */
class PuzzleSelector(private val puzzles: List<PuzzleMeta>, private val random: Random = Random.Default) {

    fun next(
        band: Int,
        solved: Set<Int>,
        recentThemes: List<String> = emptyList(),
        exclude: Set<Int> = emptySet(),
        /** id → thời điểm giải lần cuối (để chọn lại bài cũ nhất khi hết kho). */
        lastSolvedAt: Map<Int, Long> = emptyMap(),
    ): PuzzleMeta? {
        if (puzzles.isEmpty()) return null
        val open = puzzles.filter { it.id !in solved && it.id !in exclude }
        if (open.isNotEmpty()) {
            val bands = (0..4).flatMap { d -> listOf(band - d, band + d) }.distinct().filter { it in 1..5 }
            for (b in bands) {
                val pool = open.filter { it.difficulty == b }
                if (pool.isEmpty()) continue
                val recent = recentThemes.takeLast(5)
                val minCount = pool.minOf { p -> recent.count { it == p.theme } }
                val best = pool.filter { p -> recent.count { it == p.theme } == minCount }
                return best[random.nextInt(best.size)]
            }
        }
        val pool = puzzles.filter { it.id !in exclude }
        return pool.minByOrNull { lastSolvedAt[it.id] ?: 0L }
    }
}

/**
 * Bậc bài 1–5 của người chơi: đúng [upAfter] bài liền không dùng gợi ý → lên; sai [downAfter] bài liền → xuống.
 * "Sai" = phải Làm lại hoặc dùng gợi ý bậc 3 (xem lời giải).
 */
data class BandState(val band: Int, val okStreak: Int = 0, val failStreak: Int = 0)

class BandTracker(private val upAfter: Int = 3, private val downAfter: Int = 2) {
    fun record(state: BandState, solvedClean: Boolean, failed: Boolean): BandState {
        return when {
            failed -> {
                val f = state.failStreak + 1
                if (f >= downAfter) BandState((state.band - 1).coerceAtLeast(1), 0, 0) else state.copy(okStreak = 0, failStreak = f)
            }
            solvedClean -> {
                val o = state.okStreak + 1
                if (o >= upAfter) BandState((state.band + 1).coerceAtMost(5), 0, 0) else state.copy(okStreak = o, failStreak = 0)
            }
            else -> state.copy(okStreak = 0, failStreak = 0) // giải được nhưng có dùng gợi ý bậc 1–2: giữ nguyên
        }
    }
}
