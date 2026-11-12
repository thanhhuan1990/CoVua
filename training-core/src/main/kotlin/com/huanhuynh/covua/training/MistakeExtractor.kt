package com.huanhuynh.covua.training

/** Một nước của người chơi kèm đánh giá của thầy (đọc từ move_eval / lời thầy trong ván). */
data class PlayerMoveEval(
    val ply: Int,
    val fenBefore: String,
    val move: Int,
    val lossCp: Int,
    /** Số "nước đạt" (mất ≤ 50 cp) ở phân tích đầu lượt. */
    val okMoveCount: Int,
    val tags: Int,
    /** Đã đi lại theo gợi ý (true), tự tìm lại (false) hoặc không đi lại (null). */
    val retried: RetryKind? = null,
    /** Khi tự tìm: nước thay thế đạt hay không. */
    val retryFoundGood: Boolean = false,
)

enum class RetryKind { FOLLOWED_HINT, SELF }

data class MistakeCandidate(val eval: PlayerMoveEval, val startBox: Int)

/**
 * Chọn thế để ghi Sổ tay: nước Sai lầm trở lên (loss ≥ minLoss), đề rõ ràng (≤ maxOkMoves nước đạt),
 * tối đa [maxPerGame] thẻ – lấy các nước mất nhiều nhất. Ván cấp < minLevel hoặc ván legacy: không lấy.
 */
class MistakeExtractor(
    private val minLevel: Int = 2,
    private val minLossCp: Int = 150,
    private val maxPerGame: Int = 3,
    private val maxOkMoves: Int = 3,
) {
    fun extract(level: Int, legacy: Boolean, moves: List<PlayerMoveEval>): List<MistakeCandidate> {
        if (legacy || level < minLevel) return emptyList()
        return moves
            .filter { it.lossCp >= minLossCp && it.okMoveCount in 1..maxOkMoves }
            .sortedByDescending { it.lossCp }
            .distinctBy { it.fenBefore }
            .take(maxPerGame)
            .map { MistakeCandidate(it, if (it.retried == RetryKind.SELF && it.retryFoundGood) 2 else 1) }
    }
}
