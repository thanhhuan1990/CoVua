package com.huanhuynh.covua.engine

import kotlin.math.abs

/**
 * Thế cờ vua. Bàn 8 × 8, ô = r * 8 + c; r = 0 là hàng 1 (Trắng, dưới), c = 0 là cột a.
 * Quân: dương = Trắng, âm = Đen; giá trị tuyệt đối: [KING]..[PAWN]. Trắng đi trước (side = 1).
 * Có nhập thành, bắt tốt qua đường, phong cấp, luật 50 nước. Thuần Kotlin → unit test được.
 */
class Position private constructor(
    val board: IntArray,
    side: Int,
    castling: Int,
    ep: Int,
    halfmove: Int,
    fullmove: Int,
) {
    var side: Int = side
        private set
    /** Quyền nhập thành: bit 1 = Trắng cánh vua, 2 = Trắng cánh hậu, 4 = Đen cánh vua, 8 = Đen cánh hậu. */
    var castling: Int = castling
        private set
    /** Ô có thể bắt tốt qua đường (−1 = không có). */
    var ep: Int = ep
        private set
    /** Số nửa nước từ lần ăn quân / đi Tốt gần nhất (luật 50 nước). */
    var halfmove: Int = halfmove
        private set
    var fullmove: Int = fullmove
        private set
    var hash: Long = 0L
        private set

    private val undoMove = IntArray(MAX_PLY)
    private val undoCaptured = IntArray(MAX_PLY)
    private val undoWasEp = BooleanArray(MAX_PLY)
    private val undoHash = LongArray(MAX_PLY)
    private val undoCastle = IntArray(MAX_PLY)
    private val undoEp = IntArray(MAX_PLY)
    private val undoHalf = IntArray(MAX_PLY)
    private val hashHistory = LongArray(MAX_PLY + 1)
    private val kingSq = IntArray(2)
    var ply: Int = 0
        private set

    init {
        for (sq in 0 until 64) {
            if (board[sq] == KING) kingSq[0] = sq
            if (board[sq] == -KING) kingSq[1] = sq
        }
        hash = computeHash()
        hashHistory[0] = hash
    }

    fun copy(): Position = Position(board.copyOf(), side, castling, ep, halfmove, fullmove)

    operator fun get(sq: Int) = board[sq]

    fun kingSquare(s: Int): Int = if (board[kingSq[if (s > 0) 0 else 1]] == KING * s) kingSq[if (s > 0) 0 else 1] else -1

    // ---------------- Đi / lùi ----------------

    fun make(move: Int) {
        val from = Moves.from(move)
        val to = Moves.to(move)
        val promo = Moves.promo(move)
        val piece = board[from]
        val type = abs(piece)
        var cap = board[to]
        undoMove[ply] = move
        undoHash[ply] = hash
        undoCastle[ply] = castling
        undoEp[ply] = ep
        undoHalf[ply] = halfmove
        var h = hash xor Zobrist.SIDE xor Zobrist.castle(castling) xor Zobrist.ep(ep)
        h = h xor Zobrist.piece(piece, from)
        // Bắt tốt qua đường
        if (type == PAWN && to == ep && cap == 0) {
            val capSq = to - 8 * side
            cap = board[capSq]
            board[capSq] = 0
            h = h xor Zobrist.piece(cap, capSq)
            undoCaptured[ply] = cap
            undoWasEp[ply] = true
        } else {
            if (cap != 0) h = h xor Zobrist.piece(cap, to)
            undoCaptured[ply] = cap
            undoWasEp[ply] = false
        }
        val placed = if (promo != 0) promo * side else piece
        board[to] = placed
        board[from] = 0
        h = h xor Zobrist.piece(placed, to)
        // Nhập thành: dời Xe
        if (type == KING) {
            kingSq[if (side > 0) 0 else 1] = to
            if (abs(to - from) == 2) {
                val rookFrom = if (to > from) to + 1 else to - 2
                val rookTo = if (to > from) to - 1 else to + 1
                val rook = board[rookFrom]
                board[rookTo] = rook
                board[rookFrom] = 0
                h = h xor Zobrist.piece(rook, rookFrom) xor Zobrist.piece(rook, rookTo)
            }
        }
        castling = castling and CASTLE_MASK[from] and CASTLE_MASK[to]
        // Chỉ ghi ô bắt tốt qua đường khi đối phương thật sự có Tốt bắt được → hash khớp với FEN, phát hiện lặp thế đúng
        ep = if (type == PAWN && abs(to - from) == 16 && enemyPawnBeside(to, side)) (from + to) / 2 else -1
        halfmove = if (type == PAWN || cap != 0) 0 else halfmove + 1
        if (side < 0) fullmove++
        side = -side
        h = h xor Zobrist.castle(castling) xor Zobrist.ep(ep)
        hash = h
        ply++
        hashHistory[ply] = h
    }

    fun unmake() {
        ply--
        val move = undoMove[ply]
        side = -side
        if (side < 0) fullmove--
        val from = Moves.from(move)
        val to = Moves.to(move)
        val promo = Moves.promo(move)
        val moved = board[to]
        board[from] = if (promo != 0) PAWN * side else moved
        if (undoWasEp[ply]) {
            board[to] = 0
            board[to - 8 * side] = undoCaptured[ply]
        } else {
            board[to] = undoCaptured[ply]
        }
        if (abs(board[from]) == KING) {
            kingSq[if (side > 0) 0 else 1] = from
            if (abs(to - from) == 2) {
                val rookFrom = if (to > from) to + 1 else to - 2
                val rookTo = if (to > from) to - 1 else to + 1
                board[rookFrom] = board[rookTo]
                board[rookTo] = 0
            }
        }
        castling = undoCastle[ply]
        ep = undoEp[ply]
        halfmove = undoHalf[ply]
        hash = undoHash[ply]
    }

    private fun enemyPawnBeside(sq: Int, mover: Int): Boolean {
        val c = sq and 7
        if (c > 0 && board[sq - 1] == -PAWN * mover) return true
        if (c < 7 && board[sq + 1] == -PAWN * mover) return true
        return false
    }

    /** Nước đi trống (dùng cho null-move pruning). */
    fun makeNull() {
        undoMove[ply] = 0
        undoCaptured[ply] = 0
        undoWasEp[ply] = false
        undoHash[ply] = hash
        undoCastle[ply] = castling
        undoEp[ply] = ep
        undoHalf[ply] = halfmove
        hash = hash xor Zobrist.SIDE xor Zobrist.ep(ep) xor Zobrist.ep(-1)
        ep = -1
        side = -side
        ply++
        hashHistory[ply] = hash
    }

    fun unmakeNull() {
        ply--
        side = -side
        ep = undoEp[ply]
        hash = undoHash[ply]
    }

    /** Thế hiện tại đã xuất hiện trước đó (cùng bên đi) trong [window] nửa nước gần nhất. */
    fun isRepetition(window: Int = 100): Boolean {
        var i = ply - 2
        val stop = maxOf(0, ply - minOf(window, halfmove))
        while (i >= stop) {
            if (hashHistory[i] == hash) return true
            i -= 2
        }
        return false
    }

    fun repetitionCount(): Int {
        var n = 0
        var i = ply - 2
        val stop = maxOf(0, ply - halfmove)
        while (i >= stop) {
            if (hashHistory[i] == hash) n++
            i -= 2
        }
        return n
    }

    // ---------------- Tấn công / chiếu ----------------

    fun inCheck(s: Int = side): Boolean {
        val k = kingSquare(s)
        return k < 0 || isAttacked(k, -s)
    }

    fun isAttacked(sq: Int, by: Int): Boolean = attackers(sq, by, stopAtFirst = true).isNotEmpty()

    /** Các ô có quân bên [by] đang tấn công ô [sq]. */
    fun attackers(sq: Int, by: Int, stopAtFirst: Boolean = false): List<Int> {
        val out = ArrayList<Int>(4)
        val r = sq shr 3
        val c = sq and 7
        // Tốt
        val pr = r - by
        if (pr in 0..7) {
            for (dc in intArrayOf(-1, 1)) {
                val cc = c + dc
                if (cc in 0..7 && board[pr * 8 + cc] == PAWN * by) { out += pr * 8 + cc; if (stopAtFirst) return out }
            }
        }
        // Mã
        for (i in 0 until 8) {
            val rr = r + KN_DR[i]; val cc = c + KN_DC[i]
            if (rr in 0..7 && cc in 0..7 && board[rr * 8 + cc] == KNIGHT * by) { out += rr * 8 + cc; if (stopAtFirst) return out }
        }
        // Vua
        for (i in 0 until 8) {
            val rr = r + K_DR[i]; val cc = c + K_DC[i]
            if (rr in 0..7 && cc in 0..7 && board[rr * 8 + cc] == KING * by) { out += rr * 8 + cc; if (stopAtFirst) return out }
        }
        // Đường thẳng: Xe / Hậu
        for (i in 0 until 4) {
            var rr = r + K_DR[i]; var cc = c + K_DC[i]
            while (rr in 0..7 && cc in 0..7) {
                val p = board[rr * 8 + cc]
                if (p != 0) {
                    if (p == ROOK * by || p == QUEEN * by) { out += rr * 8 + cc; if (stopAtFirst) return out }
                    break
                }
                rr += K_DR[i]; cc += K_DC[i]
            }
        }
        // Đường chéo: Tượng / Hậu
        for (i in 4 until 8) {
            var rr = r + K_DR[i]; var cc = c + K_DC[i]
            while (rr in 0..7 && cc in 0..7) {
                val p = board[rr * 8 + cc]
                if (p != 0) {
                    if (p == BISHOP * by || p == QUEEN * by) { out += rr * 8 + cc; if (stopAtFirst) return out }
                    break
                }
                rr += K_DR[i]; cc += K_DC[i]
            }
        }
        return out
    }

    // ---------------- Sinh nước ----------------

    /** Nước giả hợp lệ (chưa kiểm tra vua bị chiếu). [capturesOnly]: chỉ ăn quân + phong Hậu. */
    fun pseudoMoves(out: MoveList, capturesOnly: Boolean = false) {
        out.clear()
        val s = side
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0 || (p > 0) != (s > 0)) continue
            val r = sq shr 3
            val c = sq and 7
            when (abs(p)) {
                PAWN -> pawnMoves(sq, r, c, s, out, capturesOnly)
                KNIGHT -> for (i in 0 until 8) {
                    val rr = r + KN_DR[i]; val cc = c + KN_DC[i]
                    if (rr !in 0..7 || cc !in 0..7) continue
                    val t = board[rr * 8 + cc]
                    if (t == 0) { if (!capturesOnly) out.add(Moves.encode(sq, rr * 8 + cc)) }
                    else if ((t > 0) != (s > 0)) out.add(Moves.encode(sq, rr * 8 + cc))
                }
                KING -> {
                    for (i in 0 until 8) {
                        val rr = r + K_DR[i]; val cc = c + K_DC[i]
                        if (rr !in 0..7 || cc !in 0..7) continue
                        val t = board[rr * 8 + cc]
                        if (t == 0) { if (!capturesOnly) out.add(Moves.encode(sq, rr * 8 + cc)) }
                        else if ((t > 0) != (s > 0)) out.add(Moves.encode(sq, rr * 8 + cc))
                    }
                    if (!capturesOnly) castleMoves(sq, s, out)
                }
                ROOK -> slide(sq, r, c, s, 0, 4, out, capturesOnly)
                BISHOP -> slide(sq, r, c, s, 4, 8, out, capturesOnly)
                QUEEN -> slide(sq, r, c, s, 0, 8, out, capturesOnly)
            }
        }
    }

    private fun slide(sq: Int, r: Int, c: Int, s: Int, d0: Int, d1: Int, out: MoveList, capturesOnly: Boolean) {
        for (i in d0 until d1) {
            var rr = r + K_DR[i]; var cc = c + K_DC[i]
            while (rr in 0..7 && cc in 0..7) {
                val t = board[rr * 8 + cc]
                if (t == 0) {
                    if (!capturesOnly) out.add(Moves.encode(sq, rr * 8 + cc))
                } else {
                    if ((t > 0) != (s > 0)) out.add(Moves.encode(sq, rr * 8 + cc))
                    break
                }
                rr += K_DR[i]; cc += K_DC[i]
            }
        }
    }

    private fun pawnMoves(sq: Int, r: Int, c: Int, s: Int, out: MoveList, capturesOnly: Boolean) {
        val r1 = r + s
        if (r1 !in 0..7) return
        val lastRank = if (s > 0) 7 else 0
        val startRank = if (s > 0) 1 else 6
        fun add(to: Int, quietOnly: Boolean) {
            if (r1 == lastRank) {
                out.add(Moves.encode(sq, to, QUEEN))
                if (!capturesOnly) { out.add(Moves.encode(sq, to, KNIGHT)); out.add(Moves.encode(sq, to, ROOK)); out.add(Moves.encode(sq, to, BISHOP)) }
            } else if (!(quietOnly && capturesOnly)) out.add(Moves.encode(sq, to))
        }
        val fwd = r1 * 8 + c
        if (board[fwd] == 0) {
            add(fwd, true)
            if (r == startRank && !capturesOnly && board[fwd + 8 * s] == 0) out.add(Moves.encode(sq, fwd + 8 * s))
        }
        for (dc in intArrayOf(-1, 1)) {
            val cc = c + dc
            if (cc !in 0..7) continue
            val to = r1 * 8 + cc
            val t = board[to]
            if ((t != 0 && (t > 0) != (s > 0)) || to == ep) add(to, false)
        }
    }

    private fun castleMoves(sq: Int, s: Int, out: MoveList) {
        val home = if (s > 0) 4 else 60
        if (sq != home) return
        val kRight = if (s > 0) 1 else 4
        val qRight = if (s > 0) 2 else 8
        if (castling and kRight != 0 && board[home + 1] == 0 && board[home + 2] == 0 && board[home + 3] == ROOK * s &&
            !isAttacked(home, -s) && !isAttacked(home + 1, -s) && !isAttacked(home + 2, -s)
        ) out.add(Moves.encode(home, home + 2))
        if (castling and qRight != 0 && board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0 && board[home - 4] == ROOK * s &&
            !isAttacked(home, -s) && !isAttacked(home - 1, -s) && !isAttacked(home - 2, -s)
        ) out.add(Moves.encode(home, home - 2))
    }

    fun legalMoves(): IntArray {
        val ml = MoveList()
        pseudoMoves(ml)
        val res = IntArray(ml.size)
        var n = 0
        val me = side
        for (i in 0 until ml.size) {
            val m = ml[i]
            make(m)
            if (!inCheck(me)) res[n++] = m
            unmake()
        }
        return res.copyOf(n)
    }

    fun isLegal(move: Int): Boolean = legalMoves().contains(move)

    fun isCheckmateOrStalemate(): Boolean = legalMoves().isEmpty()

    /** Không bên nào đủ quân để chiếu hết (Vua trơ, Vua + 1 Tượng/Mã, Vua + Tượng mỗi bên cùng màu ô). */
    fun insufficientMaterial(): Boolean {
        var minors = 0
        val bishopColors = HashSet<Int>()
        var bishops = 0
        for (sq in 0 until 64) {
            when (abs(board[sq])) {
                PAWN, ROOK, QUEEN -> return false
                KNIGHT -> minors++
                BISHOP -> { bishops++; bishopColors += ((sq shr 3) + (sq and 7)) and 1 }
            }
        }
        if (minors + bishops <= 1) return true
        return minors == 0 && bishopColors.size == 1
    }

    /** Thế không còn quân nào ngoài Tốt + Vua của bên [s] (để null move an toàn hơn). */
    fun hasPieces(s: Int): Boolean {
        for (sq in 0 until 64) {
            val t = board[sq] * s
            if (t == QUEEN || t == ROOK || t == BISHOP || t == KNIGHT) return true
        }
        return false
    }

    // ---------------- FEN ----------------

    fun toFen(full: Boolean = true): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (c in 0..7) {
                val p = board[r * 8 + c]
                if (p == 0) { empty++; continue }
                if (empty > 0) { sb.append(empty); empty = 0 }
                sb.append(pieceChar(p))
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (side > 0) " w " else " b ")
        if (castling == 0) sb.append('-') else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ').append(if (ep >= 0 && epCapturable()) squareName(ep) else "-")
        if (full) sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    /** Chỉ ghi ô bắt tốt qua đường khi thật sự có Tốt bắt được (giống chuẩn thực tế, khớp với Stockfish). */
    private fun epCapturable(): Boolean {
        val r = ep shr 3; val c = ep and 7
        val pr = r - side
        if (pr !in 0..7) return false
        for (dc in intArrayOf(-1, 1)) {
            val cc = c + dc
            if (cc in 0..7 && board[pr * 8 + cc] == PAWN * side) return true
        }
        return false
    }

    private fun computeHash(): Long {
        var h = 0L
        for (sq in 0 until 64) if (board[sq] != 0) h = h xor Zobrist.piece(board[sq], sq)
        if (side < 0) h = h xor Zobrist.SIDE
        h = h xor Zobrist.castle(castling) xor Zobrist.ep(ep)
        return h
    }

    companion object {
        const val KING = 1
        const val QUEEN = 2
        const val ROOK = 3
        const val BISHOP = 4
        const val KNIGHT = 5
        const val PAWN = 6

        const val MAX_PLY = 1024

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val KN_DR = intArrayOf(2, 2, -2, -2, 1, 1, -1, -1)
        val KN_DC = intArrayOf(1, -1, 1, -1, 2, -2, 2, -2)
        /** 4 hướng thẳng rồi 4 hướng chéo. */
        val K_DR = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val K_DC = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        private val CASTLE_MASK = IntArray(64) { 15 }.also {
            it[4] = 15 and 3.inv(); it[0] = 15 and 2.inv(); it[7] = 15 and 1.inv()
            it[60] = 15 and 12.inv(); it[56] = 15 and 8.inv(); it[63] = 15 and 4.inv()
        }

        fun start(): Position = fromFen(START_FEN)

        fun squareName(sq: Int): String = "${'a' + (sq and 7)}${(sq shr 3) + 1}"

        fun parseSquare(s: String): Int {
            require(s.length == 2 && s[0] in 'a'..'h' && s[1] in '1'..'8') { "Ô lạ: $s" }
            return (s[1] - '1') * 8 + (s[0] - 'a')
        }

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            val rows = parts[0].split('/')
            require(rows.size == 8) { "FEN cần 8 hàng" }
            val b = IntArray(64)
            for ((i, row) in rows.withIndex()) {
                val r = 7 - i
                var c = 0
                for (ch in row) {
                    if (ch.isDigit()) { c += ch - '0'; continue }
                    require(c < 8) { "FEN hàng quá dài" }
                    b[r * 8 + c] = charPiece(ch)
                    c++
                }
            }
            val side = if (parts.getOrNull(1) == "b") -1 else 1
            var castle = 0
            parts.getOrNull(2)?.let { cs ->
                if ('K' in cs) castle = castle or 1
                if ('Q' in cs) castle = castle or 2
                if ('k' in cs) castle = castle or 4
                if ('q' in cs) castle = castle or 8
            }
            // Bỏ quyền nhập thành không khớp vị trí quân (FEN tay / cờ chấp)
            if (b[4] != KING || b[7] != ROOK) castle = castle and 1.inv()
            if (b[4] != KING || b[0] != ROOK) castle = castle and 2.inv()
            if (b[60] != -KING || b[63] != -ROOK) castle = castle and 4.inv()
            if (b[60] != -KING || b[56] != -ROOK) castle = castle and 8.inv()
            val ep = parts.getOrNull(3)?.takeIf { it != "-" }?.let { runCatching { parseSquare(it) }.getOrNull() } ?: -1
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
            return Position(b, side, castle, ep, half, full)
        }

        fun charPiece(ch: Char): Int {
            val t = when (ch.lowercaseChar()) {
                'k' -> KING; 'q' -> QUEEN; 'r' -> ROOK; 'b' -> BISHOP; 'n' -> KNIGHT; 'p' -> PAWN
                else -> error("Quân lạ: $ch")
            }
            return if (ch.isUpperCase()) t else -t
        }

        fun pieceChar(p: Int): Char {
            val ch = "?kqrbnp"[abs(p)]
            return if (p > 0) ch.uppercaseChar() else ch
        }
    }
}

/** Mã hoá nước đi: from | (to shl 7) | (phong cấp shl 14). 0 = không có nước. */
object Moves {
    fun encode(from: Int, to: Int, promo: Int = 0) = from or (to shl 7) or (promo shl 14)
    fun from(m: Int) = m and 0x7F
    fun to(m: Int) = (m shr 7) and 0x7F
    /** Loại quân phong cấp (0 = không phong). */
    fun promo(m: Int) = (m shr 14) and 0x7

    /** Ký hiệu UCI: e2e4, e7e8q. */
    fun uci(m: Int): String {
        if (m == 0) return "0000"
        val p = promo(m)
        return Position.squareName(from(m)) + Position.squareName(to(m)) + (if (p != 0) "?kqrbnp"[p].toString() else "")
    }

    /** Đọc nước UCI trong thế [pos] (trả 0 nếu không hợp lệ). */
    fun fromUci(pos: Position, s: String): Int {
        if (s.length < 4) return 0
        val from = runCatching { Position.parseSquare(s.substring(0, 2)) }.getOrNull() ?: return 0
        val to = runCatching { Position.parseSquare(s.substring(2, 4)) }.getOrNull() ?: return 0
        val promo = if (s.length >= 5) "?kqrbnp".indexOf(s[4].lowercaseChar()).coerceAtLeast(0) else 0
        val m = Moves.encode(from, to, promo)
        return if (pos.isLegal(m)) m else 0
    }
}

class MoveList(capacity: Int = 128) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun clear() { size = 0 }
    fun add(m: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = m
    }
    operator fun get(i: Int) = data[i]
    operator fun set(i: Int, v: Int) { data[i] = v }
}

internal object Zobrist {
    private val table: LongArray
    private val castleKeys: LongArray
    private val epKeys: LongArray
    val SIDE: Long

    init {
        val rnd = java.util.Random(0x5EED_C0_7E5AL)
        table = LongArray(13 * 64) { rnd.nextLong() }
        castleKeys = LongArray(16) { rnd.nextLong() }
        epKeys = LongArray(8) { rnd.nextLong() }
        SIDE = rnd.nextLong()
    }

    fun piece(p: Int, sq: Int): Long = table[(p + 6) * 64 + sq]
    fun castle(c: Int): Long = castleKeys[c]
    fun ep(sq: Int): Long = if (sq < 0) 0L else epKeys[sq and 7]
}
