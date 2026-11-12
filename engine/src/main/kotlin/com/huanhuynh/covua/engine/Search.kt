package com.huanhuynh.covua.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tìm kiếm alpha-beta (negamax, PVS) + iterative deepening + quiescence + bảng băm + killer/history.
 * Mỗi luồng dùng một [Search] riêng (không chia sẻ trạng thái).
 */
class Search(
    ttBits: Int = 18,
    /** false = tìm đủ (full-width): không null move, không LMR – dùng để CHỨNG MINH chiếu hết (MateProver). */
    private val pruning: Boolean = true,
) {

    data class Result(
        val move: Int,
        /** Điểm theo góc nhìn bên đi ở thế gốc. */
        val score: Int,
        val depth: Int,
        val pv: List<Int>,
        val nodes: Long,
    )

    private val ttSize = 1 shl ttBits
    private val ttMask = ttSize - 1
    private val ttKey = LongArray(ttSize)
    private val ttMove = IntArray(ttSize)
    private val ttScore = IntArray(ttSize)
    private val ttDepth = ByteArray(ttSize)
    private val ttFlag = ByteArray(ttSize)

    private val killers = Array(MAX_DEPTH) { IntArray(2) }
    private val history = IntArray(128 * 128)
    private val lists = Array(MAX_DEPTH + 16) { MoveList() }
    private val scores = Array(MAX_DEPTH + 16) { IntArray(160) }

    private var nodes = 0L
    private var nodeLimit = Long.MAX_VALUE
    private var deadline = Long.MAX_VALUE
    private var stopped = false
    @Volatile var cancel = false

    fun clear() {
        ttKey.fill(0); ttMove.fill(0); ttDepth.fill(0)
        history.fill(0)
        for (k in killers) k.fill(0)
    }

    /**
     * Tìm nước tốt nhất. [maxDepth] độ sâu tối đa, [timeMs] giới hạn thời gian (0 = không giới hạn).
     * [exclude] bỏ qua các nước này ở gốc.
     */
    fun think(pos: Position, maxDepth: Int, timeMs: Long = 0, exclude: Set<Int> = emptySet(), maxNodes: Long = Long.MAX_VALUE): Result {
        nodes = 0
        nodeLimit = maxNodes
        stopped = false
        cancel = false
        deadline = if (timeMs > 0) System.currentTimeMillis() + timeMs else Long.MAX_VALUE
        for (k in killers) k.fill(0)
        for (i in history.indices) history[i] = history[i] shr 2

        val rootMoves = pos.legalMoves().filter { it !in exclude }.toIntArray()
        if (rootMoves.isEmpty()) {
            return Result(0, if (pos.inCheck()) -MATE else 0, 0, emptyList(), 0)
        }
        var best = rootMoves[0]
        var bestScore = -INF
        var completedDepth = 0
        val rootScores = IntArray(rootMoves.size)
        for (depth in 1..maxDepth) {
            // Sắp nước gốc: nước tốt nhất lần trước lên đầu
            orderRoot(rootMoves, rootScores, best)
            var alpha = -INF
            val beta = INF
            var iterBest = 0
            var iterScore = -INF
            for ((i, m) in rootMoves.withIndex()) {
                pos.make(m)
                var s: Int
                if (i == 0) {
                    s = -negamax(pos, depth - 1, -beta, -alpha, 1, true)
                } else {
                    s = -negamax(pos, depth - 1, -alpha - 1, -alpha, 1, true)
                    if (!stopped && s > alpha) s = -negamax(pos, depth - 1, -beta, -alpha, 1, true)
                }
                pos.unmake()
                if (stopped) break
                rootScores[i] = s
                if (s > iterScore) { iterScore = s; iterBest = m }
                if (s > alpha) alpha = s
            }
            if (stopped) {
                // Dùng kết quả dở dang nếu đã tìm được nước tốt hơn ở lượt này
                if (iterBest != 0 && iterScore > bestScore && completedDepth > 0) { best = iterBest; bestScore = iterScore }
                break
            }
            best = iterBest
            bestScore = iterScore
            completedDepth = depth
            storeTT(pos.hash, depth, bestScore, EXACT, best, 0)
            if (abs(bestScore) > MATE - 200) break // đã thấy chiếu hết
            if (timeMs > 0 && System.currentTimeMillis() > deadline - timeMs / 2) break
        }
        return Result(best, bestScore, completedDepth, extractPv(pos, best), nodes)
    }

    /**
     * Điểm (góc nhìn bên đi) của MỌI nước ở gốc, mỗi nước tìm đủ cửa sổ ở [depth].
     * Dùng cho máy cấp thấp (chọn nước kém có chủ đích) và cảnh báo của thầy.
     */
    fun scoreAll(pos: Position, depth: Int, moves: IntArray = pos.legalMoves()): IntArray {
        nodeLimit = Long.MAX_VALUE
        stopped = false
        cancel = false
        deadline = Long.MAX_VALUE
        val out = IntArray(moves.size)
        for ((i, m) in moves.withIndex()) {
            pos.make(m)
            out[i] = -negamax(pos, depth - 1, -INF, INF, 1, true)
            pos.unmake()
        }
        return out
    }

    /** Điểm của thế [pos] theo góc nhìn bên đi ở độ sâu [depth]. */
    fun evaluateDeep(pos: Position, depth: Int): Int {
        nodeLimit = Long.MAX_VALUE
        stopped = false
        cancel = false
        deadline = Long.MAX_VALUE
        if (pos.legalMoves().isEmpty()) return if (pos.inCheck()) -MATE else 0
        return negamax(pos, depth, -INF, INF, 0, true)
    }

    /** Lần [think] gần nhất bị dừng sớm (hết giờ / hết node / huỷ). */
    val wasStopped: Boolean get() = stopped
    val lastNodes: Long get() = nodes

    private fun orderRoot(moves: IntArray, scores: IntArray, best: Int) {
        val idx = moves.indexOf(best)
        if (idx > 0) {
            val m = moves[idx]; val s = scores[idx]
            for (j in idx downTo 1) { moves[j] = moves[j - 1]; scores[j] = scores[j - 1] }
            moves[0] = m; scores[0] = s
        }
    }

    private fun timeUp(): Boolean {
        if (nodes >= nodeLimit) stopped = true
        else if ((nodes and 1023L) == 0L && (cancel || System.currentTimeMillis() > deadline)) stopped = true
        return stopped
    }

    private fun negamax(pos: Position, depthIn: Int, alphaIn: Int, beta: Int, ply: Int, allowNull: Boolean): Int {
        nodes++
        if (timeUp()) return 0
        var alpha = alphaIn
        if (ply > 0 && (pos.isRepetition(16) || pos.halfmove >= 100)) return 0
        val inCheck = pos.inCheck()
        var depth = depthIn
        if (inCheck) depth++ // gia hạn khi bị chiếu
        if (depth <= 0) return quiesce(pos, alpha, beta, ply)
        if (ply >= MAX_DEPTH - 1) return Eval.evaluate(pos)

        // Bảng băm
        val slot = (pos.hash.toInt() xor (pos.hash ushr 32).toInt()) and ttMask
        var ttBest = 0
        if (ttKey[slot] == pos.hash) {
            ttBest = ttMove[slot]
            if (ttDepth[slot] >= depth) {
                val s = fromTT(ttScore[slot], ply)
                when (ttFlag[slot]) {
                    EXACT -> return s
                    LOWER -> if (s >= beta) return s
                    UPPER -> if (s <= alpha) return s
                }
            }
        }

        // Null move (không dùng khi bị chiếu / tàn cuộc ít quân)
        if (pruning && allowNull && !inCheck && depth >= 3 && beta < MATE - 200 && pos.hasPieces(pos.side)) {
            pos.makeNull()
            val s = -negamax(pos, depth - 3, -beta, -beta + 1, ply + 1, false)
            pos.unmakeNull()
            if (stopped) return 0
            if (s >= beta) return beta
        }

        val list = lists[ply]
        pos.pseudoMoves(list)
        val sc = scoreMoves(pos, list, ply, ttBest)
        var legal = 0
        var bestScore = -INF
        var bestMove = 0
        val me = pos.side
        for (i in 0 until list.size) {
            pickBest(list, sc, i)
            val m = list[i]
            val isCap = pos.board[Moves.to(m)] != 0 || Moves.promo(m) != 0
            pos.make(m)
            if (pos.inCheck(me)) { pos.unmake(); continue }
            legal++
            var s: Int
            if (legal == 1) {
                s = -negamax(pos, depth - 1, -beta, -alpha, ply + 1, true)
            } else {
                // Giảm độ sâu nước muộn, không ăn quân
                var r = 0
                if (pruning && !inCheck && !isCap && depth >= 3 && legal > 4) r = if (legal > 10) 2 else 1
                s = -negamax(pos, depth - 1 - r, -alpha - 1, -alpha, ply + 1, true)
                if (!stopped && s > alpha && r > 0) s = -negamax(pos, depth - 1, -alpha - 1, -alpha, ply + 1, true)
                if (!stopped && s > alpha && s < beta) s = -negamax(pos, depth - 1, -beta, -alpha, ply + 1, true)
            }
            pos.unmake()
            if (stopped) return 0
            if (s > bestScore) {
                bestScore = s
                bestMove = m
                if (s > alpha) {
                    alpha = s
                    if (s >= beta) {
                        if (!isCap) {
                            val k = killers[ply]
                            if (k[0] != m) { k[1] = k[0]; k[0] = m }
                            history[(Moves.from(m) shl 7) or Moves.to(m)] += depth * depth
                        }
                        break
                    }
                }
            }
        }
        if (legal == 0) return if (inCheck) -MATE + ply else 0 // bị chiếu hết / hết nước = hoà
        val flag = when {
            bestScore >= beta -> LOWER
            bestScore > alphaIn -> EXACT
            else -> UPPER
        }
        storeTT(pos.hash, depth, bestScore, flag, bestMove, ply)
        return bestScore
    }

    private fun quiesce(pos: Position, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        if (timeUp()) return 0
        var alpha = alphaIn
        val stand = Eval.evaluate(pos)
        if (ply >= MAX_DEPTH - 1) return stand
        if (stand >= beta) return stand
        if (stand > alpha) alpha = stand
        val list = lists[ply]
        pos.pseudoMoves(list, capturesOnly = true)
        val sc = scoreMoves(pos, list, ply, 0)
        var best = stand
        val me = pos.side
        for (i in 0 until list.size) {
            pickBest(list, sc, i)
            val m = list[i]
            pos.make(m)
            if (pos.inCheck(me)) { pos.unmake(); continue }
            val s = -quiesce(pos, -beta, -alpha, ply + 1)
            pos.unmake()
            if (stopped) return 0
            if (s > best) {
                best = s
                if (s > alpha) {
                    alpha = s
                    if (s >= beta) break
                }
            }
        }
        return best
    }

    private fun scoreMoves(pos: Position, list: MoveList, ply: Int, ttBest: Int): IntArray {
        val sc = scores[ply].let { if (it.size < list.size) IntArray(list.size).also { n -> scores[ply] = n } else it }
        val k = if (ply < MAX_DEPTH) killers[ply] else IntArray(2)
        for (i in 0 until list.size) {
            val m = list[i]
            val victim = pos.board[Moves.to(m)]
            sc[i] = when {
                m == ttBest -> 1_000_000
                Moves.promo(m) == Position.QUEEN -> 200_000
                victim != 0 -> 100_000 + Eval.VALUE[abs(victim)] * 10 - Eval.VALUE[abs(pos.board[Moves.from(m)])] / 10
                m == k[0] -> 90_000
                m == k[1] -> 80_000
                else -> min(history[(Moves.from(m) shl 7) or Moves.to(m)], 70_000)
            }
        }
        return sc
    }

    private fun pickBest(list: MoveList, sc: IntArray, from: Int) {
        var bi = from
        for (j in from + 1 until list.size) if (sc[j] > sc[bi]) bi = j
        if (bi != from) {
            val m = list[bi]; list[bi] = list[from]; list[from] = m
            val s = sc[bi]; sc[bi] = sc[from]; sc[from] = s
        }
    }

    private fun storeTT(hash: Long, depth: Int, score: Int, flag: Byte, move: Int, ply: Int) {
        val slot = (hash.toInt() xor (hash ushr 32).toInt()) and ttMask
        if (ttKey[slot] == hash && ttDepth[slot] > depth && flag != EXACT) return
        ttKey[slot] = hash
        ttDepth[slot] = min(depth, 127).toByte()
        ttScore[slot] = toTT(score, ply)
        ttFlag[slot] = flag
        if (move != 0 || ttKey[slot] != hash) ttMove[slot] = move
    }

    private fun toTT(s: Int, ply: Int) = when {
        s > MATE - 1000 -> s + ply
        s < -MATE + 1000 -> s - ply
        else -> s
    }

    private fun fromTT(s: Int, ply: Int) = when {
        s > MATE - 1000 -> s - ply
        s < -MATE + 1000 -> s + ply
        else -> s
    }

    private fun extractPv(pos: Position, first: Int): List<Int> {
        val pv = ArrayList<Int>()
        if (first == 0) return pv
        var made = 0
        var m = first
        val seen = HashSet<Long>()
        while (m != 0 && pv.size < 12) {
            if (!pos.isLegal(m)) break
            pv += m
            pos.make(m); made++
            if (!seen.add(pos.hash)) break
            val slot = (pos.hash.toInt() xor (pos.hash ushr 32).toInt()) and ttMask
            m = if (ttKey[slot] == pos.hash) ttMove[slot] else 0
        }
        repeat(made) { pos.unmake() }
        return pv
    }

    companion object {
        const val INF = 1_000_000
        const val MATE = 100_000
        const val MAX_DEPTH = 64
        private const val EXACT: Byte = 0
        private const val LOWER: Byte = 1
        private const val UPPER: Byte = 2

        fun isMate(score: Int) = abs(score) > MATE - 1000
        /** Số nước (của bên được lợi) tới chiếu hết. */
        fun mateIn(score: Int) = (MATE - abs(score) + 1) / 2
        fun clampScore(s: Int) = max(-MATE, min(MATE, s))
    }
}
