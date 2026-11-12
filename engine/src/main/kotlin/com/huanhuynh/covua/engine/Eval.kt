package com.huanhuynh.covua.engine

import com.huanhuynh.covua.engine.Position.Companion.BISHOP
import com.huanhuynh.covua.engine.Position.Companion.KING
import com.huanhuynh.covua.engine.Position.Companion.KNIGHT
import com.huanhuynh.covua.engine.Position.Companion.PAWN
import com.huanhuynh.covua.engine.Position.Companion.QUEEN
import com.huanhuynh.covua.engine.Position.Companion.ROOK

/**
 * Đánh giá tĩnh (centi-tốt: Tốt = 100) cho engine dự phòng / thầy kèm.
 * Vật chất + bảng vị trí (khai/trung cuộc và tàn cuộc, nội suy theo lượng quân) + cặp Tượng + Tốt thông + an toàn Vua đơn giản.
 * Bảng viết theo góc nhìn Trắng, hàng 8 trước (dễ đọc như bàn cờ thật); Đen dùng đối xứng.
 */
object Eval {
    /** Chỉ số theo loại quân: 0, Vua, Hậu, Xe, Tượng, Mã, Tốt. */
    val VALUE = intArrayOf(0, 20000, 900, 500, 330, 320, 100)
    private val PHASE = intArrayOf(0, 0, 4, 2, 1, 1, 0)

    private val PAWN_MG = table(
        "  0   0   0   0   0   0   0   0",
        " 50  50  50  50  50  50  50  50",
        " 10  10  20  30  30  20  10  10",
        "  5   5  10  25  25  10   5   5",
        "  0   0   0  20  20   0   0   0",
        "  5  -5 -10   0   0 -10  -5   5",
        "  5  10  10 -20 -20  10  10   5",
        "  0   0   0   0   0   0   0   0",
    )
    private val PAWN_EG = table(
        "  0   0   0   0   0   0   0   0",
        " 90  90  90  90  90  90  90  90",
        " 50  50  50  50  50  50  50  50",
        " 30  30  30  30  30  30  30  30",
        " 15  15  15  15  15  15  15  15",
        "  5   5   5   5   5   5   5   5",
        "  0   0   0   0   0   0   0   0",
        "  0   0   0   0   0   0   0   0",
    )
    private val KNIGHT_T = table(
        "-50 -40 -30 -30 -30 -30 -40 -50",
        "-40 -20   0   0   0   0 -20 -40",
        "-30   0  10  15  15  10   0 -30",
        "-30   5  15  20  20  15   5 -30",
        "-30   0  15  20  20  15   0 -30",
        "-30   5  10  15  15  10   5 -30",
        "-40 -20   0   5   5   0 -20 -40",
        "-50 -40 -30 -30 -30 -30 -40 -50",
    )
    private val BISHOP_T = table(
        "-20 -10 -10 -10 -10 -10 -10 -20",
        "-10   0   0   0   0   0   0 -10",
        "-10   0   5  10  10   5   0 -10",
        "-10   5   5  10  10   5   5 -10",
        "-10   0  10  10  10  10   0 -10",
        "-10  10  10  10  10  10  10 -10",
        "-10   5   0   0   0   0   5 -10",
        "-20 -10 -10 -10 -10 -10 -10 -20",
    )
    private val ROOK_T = table(
        "  0   0   0   0   0   0   0   0",
        "  5  10  10  10  10  10  10   5",
        " -5   0   0   0   0   0   0  -5",
        " -5   0   0   0   0   0   0  -5",
        " -5   0   0   0   0   0   0  -5",
        " -5   0   0   0   0   0   0  -5",
        " -5   0   0   0   0   0   0  -5",
        "  0   0   0   5   5   0   0   0",
    )
    private val QUEEN_T = table(
        "-20 -10 -10  -5  -5 -10 -10 -20",
        "-10   0   0   0   0   0   0 -10",
        "-10   0   5   5   5   5   0 -10",
        " -5   0   5   5   5   5   0  -5",
        "  0   0   5   5   5   5   0  -5",
        "-10   5   5   5   5   5   0 -10",
        "-10   0   5   0   0   0   0 -10",
        "-20 -10 -10  -5  -5 -10 -10 -20",
    )
    private val KING_MG = table(
        "-30 -40 -40 -50 -50 -40 -40 -30",
        "-30 -40 -40 -50 -50 -40 -40 -30",
        "-30 -40 -40 -50 -50 -40 -40 -30",
        "-30 -40 -40 -50 -50 -40 -40 -30",
        "-20 -30 -30 -40 -40 -30 -30 -20",
        "-10 -20 -20 -20 -20 -20 -20 -10",
        " 20  20   0   0   0   0  20  20",
        " 20  30  10   0   0  10  30  20",
    )
    private val KING_EG = table(
        "-50 -40 -30 -20 -20 -30 -40 -50",
        "-30 -20 -10   0   0 -10 -20 -30",
        "-30 -10  20  30  30  20 -10 -30",
        "-30 -10  30  40  40  30 -10 -30",
        "-30 -10  30  40  40  30 -10 -30",
        "-30 -10  20  30  30  20 -10 -30",
        "-30 -30   0   0   0   0 -30 -30",
        "-50 -30 -30 -30 -30 -30 -30 -50",
    )

    /** Bảng 8 dòng (hàng 8 → hàng 1) → mảng theo ô nội bộ (hàng 1 = chỉ số 0). */
    private fun table(vararg rows: String): IntArray {
        val t = IntArray(64)
        for ((i, row) in rows.withIndex()) {
            val vals = row.trim().split(Regex("\\s+")).map { it.toInt() }
            val r = 7 - i
            for (c in 0..7) t[r * 8 + c] = vals[c]
        }
        return t
    }

    /** Ô theo góc nhìn Trắng: Đen lật dọc. */
    private fun rel(sq: Int, s: Int): Int = if (s > 0) sq else sq xor 56

    fun pst(type: Int, sq: Int, s: Int, endgame: Boolean = false): Int {
        val i = rel(sq, s)
        return when (type) {
            PAWN -> if (endgame) PAWN_EG[i] else PAWN_MG[i]
            KNIGHT -> KNIGHT_T[i]
            BISHOP -> BISHOP_T[i]
            ROOK -> ROOK_T[i]
            QUEEN -> QUEEN_T[i]
            KING -> if (endgame) KING_EG[i] else KING_MG[i]
            else -> 0
        }
    }

    /** Giá trị quân (dùng cho trao đổi / thầy kèm). */
    fun pieceValue(p: Int, @Suppress("UNUSED_PARAMETER") sq: Int = 0): Int = VALUE[kotlin.math.abs(p)]

    /** Điểm theo góc nhìn bên đang đi. */
    fun evaluate(pos: Position): Int {
        val b = pos.board
        var mg = 0
        var eg = 0
        var phase = 0
        val bishops = IntArray(2)
        val pawnFiles = Array(2) { IntArray(8) }
        for (sq in 0 until 64) {
            val p = b[sq]
            if (p == 0) continue
            val s = if (p > 0) 1 else -1
            val t = if (p > 0) p else -p
            phase += PHASE[t]
            if (t == BISHOP) bishops[if (s > 0) 0 else 1]++
            if (t == PAWN) pawnFiles[if (s > 0) 0 else 1][sq and 7]++
            val v = if (t == KING) 0 else VALUE[t]
            mg += (v + pst(t, sq, s, false)) * s
            eg += (v + pst(t, sq, s, true)) * s
        }
        // Cặp Tượng
        if (bishops[0] >= 2) { mg += 30; eg += 50 }
        if (bishops[1] >= 2) { mg -= 30; eg -= 50 }
        // Tốt chồng / Tốt cô lập / Tốt thông
        for (sq in 0 until 64) {
            val p = b[sq]
            if (p != PAWN && p != -PAWN) continue
            val s = if (p > 0) 1 else -1
            val me = if (s > 0) 0 else 1
            val f = sq and 7
            if (pawnFiles[me][f] > 1) { mg -= 10 * s; eg -= 20 * s }
            val left = if (f > 0) pawnFiles[me][f - 1] else 0
            val right = if (f < 7) pawnFiles[me][f + 1] else 0
            if (left == 0 && right == 0) { mg -= 10 * s; eg -= 15 * s }
            if (isPassed(b, sq, s)) {
                val adv = if (s > 0) (sq shr 3) else 7 - (sq shr 3)
                mg += (adv * 5) * s
                eg += (adv * adv * 4) * s
            }
        }
        val ph = phase.coerceAtMost(24)
        val score = (mg * ph + eg * (24 - ph)) / 24
        return score * pos.side + 10 // +10: lợi thế được đi
    }

    private fun isPassed(b: IntArray, sq: Int, s: Int): Boolean {
        val f = sq and 7
        var r = (sq shr 3) + s
        while (r in 0..7) {
            for (df in -1..1) {
                val ff = f + df
                if (ff in 0..7 && b[r * 8 + ff] == -PAWN * s) return false
            }
            r += s
        }
        return true
    }
}
