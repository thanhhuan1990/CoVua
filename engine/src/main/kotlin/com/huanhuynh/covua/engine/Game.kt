package com.huanhuynh.covua.engine

/** Kết quả ván. */
enum class Outcome { NONE, WHITE_WIN, BLACK_WIN, DRAW }

/**
 * Một ván cờ: thế đầu + danh sách nước. Giữ một [Position] "sống" để đi/lùi nhanh;
 * [positionCopy] dựng bản sao có đủ lịch sử (để luồng máy/thầy dùng riêng, phát hiện lặp thế đúng).
 */
class Game(val startFen: String = Position.START_FEN) {
    val pos: Position = Position.fromFen(startFen)
    private val _moves = ArrayList<Int>()
    private val _codes = ArrayList<String>()

    val moves: List<Int> get() = _moves
    /** Ký hiệu SAN quốc tế từng nước (e4, Nf3…). */
    val codes: List<String> get() = _codes
    val sideToMove: Int get() = pos.side

    /** Ký hiệu hiển thị nước thứ [i] theo ngôn ngữ hiện tại ([codes] giữ ký hiệu nội bộ). */
    fun shown(i: Int): String = runCatching { Notation.show(positionAt(i), _moves[i]) }.getOrDefault(_codes.getOrElse(i) { "" })
    fun shownAll(): List<String> { val p = Position.fromFen(startFen); return _moves.map { m -> Notation.show(p, m).also { p.make(m) } } }

    fun legalMoves(): IntArray = pos.legalMoves()

    fun play(m: Int): Boolean {
        if (!pos.isLegal(m)) return false
        _codes += Notation.of(pos, m)
        pos.make(m)
        _moves += m
        return true
    }

    fun undo(): Boolean {
        if (_moves.isEmpty()) return false
        pos.unmake()
        _moves.removeAt(_moves.lastIndex)
        _codes.removeAt(_codes.lastIndex)
        return true
    }

    fun positionCopy(): Position {
        val p = Position.fromFen(startFen)
        for (m in _moves) p.make(m)
        return p
    }

    /** Thế sau [n] nước đầu (để xem lại ván). */
    fun positionAt(n: Int): Position {
        val p = Position.fromFen(startFen)
        for (i in 0 until n.coerceAtMost(_moves.size)) p.make(_moves[i])
        return p
    }

    fun outcome(): Outcome {
        if (pos.legalMoves().isEmpty()) {
            if (!pos.inCheck()) return Outcome.DRAW // hết nước = hoà (stalemate)
            return if (pos.side > 0) Outcome.BLACK_WIN else Outcome.WHITE_WIN
        }
        if (pos.repetitionCount() >= 2) return Outcome.DRAW // thế lặp lần 3
        if (pos.halfmove >= 100) return Outcome.DRAW // 50 nước không ăn quân, không đi Tốt
        if (pos.insufficientMaterial()) return Outcome.DRAW
        return Outcome.NONE
    }

    /** Lý do hoà (để hiển thị). */
    fun drawReason(): DrawReason? = when {
        pos.legalMoves().isEmpty() && !pos.inCheck() -> DrawReason.STALEMATE
        pos.repetitionCount() >= 2 -> DrawReason.REPETITION
        pos.halfmove >= 100 -> DrawReason.FIFTY_MOVES
        pos.insufficientMaterial() -> DrawReason.MATERIAL
        else -> null
    }

    /** PGN đơn giản (để chia sẻ / xuất ván). */
    fun pgn(white: String, black: String, result: String = "*", date: String = ""): String {
        val sb = StringBuilder()
        sb.append("[Event \"Cờ Vua Có Thầy\"]\n")
        if (date.isNotEmpty()) sb.append("[Date \"$date\"]\n")
        sb.append("[White \"$white\"]\n[Black \"$black\"]\n[Result \"$result\"]\n")
        if (startFen != Position.START_FEN) sb.append("[SetUp \"1\"]\n[FEN \"$startFen\"]\n")
        sb.append('\n')
        val p = Position.fromFen(startFen)
        val line = StringBuilder()
        for ((i, m) in _moves.withIndex()) {
            if (p.side > 0) line.append(p.fullmove).append(". ")
            else if (i == 0) line.append(p.fullmove).append("... ")
            line.append(Notation.of(p, m)).append(' ')
            p.make(m)
        }
        line.append(result)
        // Ngắt dòng ~80 ký tự
        var col = 0
        for (w in line.split(' ')) {
            if (col + w.length > 79) { sb.append('\n'); col = 0 } else if (col > 0) { sb.append(' '); col++ }
            sb.append(w); col += w.length
        }
        return sb.append('\n').toString()
    }

    /** Lưu gọn: FEN đầu | danh sách nước. */
    fun serialize(): String = startFen + "|" + _moves.joinToString(",")

    companion object {
        fun deserialize(s: String): Game? = runCatching {
            val (fen, ms) = s.split('|', limit = 2)
            val g = Game(fen)
            if (ms.isNotBlank()) for (m in ms.split(',')) if (!g.play(m.toInt())) return null
            g
        }.getOrNull()
    }
}

enum class DrawReason { STALEMATE, REPETITION, FIFTY_MOVES, MATERIAL, AGREED }
