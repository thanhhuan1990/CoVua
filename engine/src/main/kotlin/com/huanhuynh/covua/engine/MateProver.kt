package com.huanhuynh.covua.engine

/**
 * Chứng minh chiếu hết bằng tìm kiếm ĐỦ (không null move, không LMR): mọi biến đến 2N−1 nửa nước đều được xét,
 * nên "có chiếu hết trong ≤ N nước" và "không có" đều đáng tin. Dùng chung cho app (kiểm bài) và :tools (đào bài).
 */
class MateProver(private val nodeBudget: Long = 3_000_000L) {
    private val search = Search(ttBits = 18, pruning = false)

    data class Proof(
        /** Số nước chiếu hết ngắn nhất tìm được (0 = không có trong giới hạn). */
        val mateIn: Int,
        val firstMove: Int,
        /** Nước đầu là duy nhất (bỏ nó ra thì không còn chiếu hết trong ≤ mateIn nước). */
        val unique: Boolean,
        /** Hết ngân sách node – kết quả chưa đáng tin. */
        val exhausted: Boolean,
    )

    /** Tìm chiếu hết ngắn nhất ≤ [maxN] nước cho bên đang đi. */
    fun prove(pos: Position, maxN: Int, checkUnique: Boolean = true): Proof {
        for (n in 1..maxN) {
            search.clear()
            val r = search.think(pos, 2 * n - 1, maxNodes = nodeBudget)
            if (search.wasStopped) return Proof(0, 0, false, true)
            if (Search.isMate(r.score) && r.score > 0 && Search.mateIn(r.score) <= n) {
                val m = Search.mateIn(r.score)
                if (!checkUnique) return Proof(m, r.move, false, false)
                search.clear()
                val alt = search.think(pos, 2 * m - 1, exclude = setOf(r.move), maxNodes = nodeBudget)
                if (search.wasStopped) return Proof(m, r.move, false, true)
                val altMate = Search.isMate(alt.score) && alt.score > 0 && Search.mateIn(alt.score) <= m
                return Proof(m, r.move, !altMate, false)
            }
        }
        return Proof(0, 0, false, false)
    }

    /** Đường giải chính: bên tấn công đi nước chiếu hết ngắn nhất, bên phòng thủ chống lâu nhất. */
    fun line(pos: Position, n: Int): List<Int>? {
        val out = ArrayList<Int>()
        var made = 0
        try {
            for (ply in 0 until 2 * n - 1) {
                search.clear()
                val r = search.think(pos, 2 * n - 1 - ply, maxNodes = nodeBudget)
                if (r.move == 0 || search.wasStopped) return null
                out += r.move
                pos.make(r.move); made++
                if (pos.legalMoves().isEmpty() && pos.inCheck()) return out
            }
            return if (pos.legalMoves().isEmpty() && pos.inCheck()) out else null
        } finally {
            repeat(made) { pos.unmake() }
        }
    }
}
