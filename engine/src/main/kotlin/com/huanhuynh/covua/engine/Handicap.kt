package com.huanhuynh.covua.engine

import com.huanhuynh.covua.i18n.t

/**
 * Cờ chấp (odds) kiểu truyền thống: bên chấp bỏ quân ở **cánh Hậu** – Mã b, Xe a, Hậu d, Tốt f.
 * [byAi] = true: máy chấp (máy bỏ quân, người dễ hơn); false: người chấp máy.
 * Bậc thang từ dễ (cho người) đến khó: [LADDER].
 */
enum class Handicap(val id: String, private val labelVi: String, val byAi: Boolean, val pieces: List<Int>) {
    NONE("none", "Không chấp", true, emptyList()),
    AI_PAWN("ai_pawn", "Máy chấp Tốt f", true, listOf(Position.PAWN)),
    AI_KNIGHT("ai_knight", "Máy chấp Mã", true, listOf(Position.KNIGHT)),
    AI_ROOK("ai_rook", "Máy chấp Xe", true, listOf(Position.ROOK)),
    AI_QUEEN("ai_queen", "Máy chấp Hậu", true, listOf(Position.QUEEN)),
    YOU_KNIGHT("you_knight", "Bạn chấp Mã", false, listOf(Position.KNIGHT)),
    YOU_ROOK("you_rook", "Bạn chấp Xe", false, listOf(Position.ROOK));

    /** Nhãn theo ngôn ngữ hiện tại. */
    val label: String
        get() {
            val names = pieces.joinToString(" + ") { Notation.pieceName(it) }
            return when {
                this == NONE -> t("Không chấp", "No handicap", "不让子", "ハンデなし")
                byAi -> t(labelVi, "Computer gives $names odds", "电脑让$names", "コンピュータが${names}落ち")
                else -> t(labelVi, "You give $names odds", "你让$names", "あなたが${names}落ち")
            }
        }

    /** Nhãn ngắn cho thẻ kết thúc / phân tích. */
    val gameLabel: String
        get() = when {
            this == NONE -> ""
            byAi -> pieces.joinToString(" + ") { Notation.pieceName(it) }.let { t("Ván chấp $it", "Odds game: computer gives $it", "让子棋：电脑让$it", "ハンデ戦：コンピュータが${it}落ち") }
            else -> pieces.joinToString(" + ") { Notation.pieceName(it) }.let { t("Ván bạn chấp $it", "Odds game: you give $it", "让子棋：你让$it", "ハンデ戦：あなたが${it}落ち") }
        }

    /** FEN thế đầu khi người chơi cầm [humanSide] (1 Trắng, −1 Đen). */
    fun startFen(humanSide: Int): String {
        if (this == NONE) return Position.START_FEN
        val giver = if (byAi) -humanSide else humanSide
        val b = Position.start().board.copyOf()
        for (t in pieces) b[squareOf(t, giver)] = 0
        return fenOf(b, 1)
    }

    companion object {
        /** Dễ → khó (với người chơi). */
        val LADDER: List<List<Handicap>> = listOf(
            listOf(AI_QUEEN), listOf(AI_ROOK), listOf(AI_KNIGHT), listOf(AI_PAWN), listOf(NONE), listOf(YOU_KNIGHT), listOf(YOU_ROOK),
        )

        fun of(id: String?): Handicap = entries.firstOrNull { it.id == id } ?: NONE

        fun rung(h: Handicap): Int = LADDER.indexOfFirst { h in it }

        /** Ô ban đầu của quân chấp [type] bên [side]. */
        fun squareOf(type: Int, side: Int): Int {
            val col = when (type) {
                Position.ROOK -> 0
                Position.KNIGHT -> 1
                Position.QUEEN -> 3
                Position.PAWN -> 5
                else -> error("Không chấp quân loại $type")
            }
            val row = when (type) {
                Position.PAWN -> if (side > 0) 1 else 6
                else -> if (side > 0) 0 else 7
            }
            return row * 8 + col
        }

        /** FEN từ mảng quân; quyền nhập thành tự suy từ vị trí Vua/Xe. */
        fun fenOf(b: IntArray, side: Int): String {
            val sb = StringBuilder()
            for (r in 7 downTo 0) {
                var e = 0
                for (c in 0..7) {
                    val v = b[r * 8 + c]
                    if (v == 0) { e++; continue }
                    if (e > 0) { sb.append(e); e = 0 }
                    sb.append(Position.pieceChar(v))
                }
                if (e > 0) sb.append(e)
                if (r > 0) sb.append('/')
            }
            sb.append(if (side > 0) " w " else " b ")
            var cs = ""
            if (b[4] == Position.KING && b[7] == Position.ROOK) cs += "K"
            if (b[4] == Position.KING && b[0] == Position.ROOK) cs += "Q"
            if (b[60] == -Position.KING && b[63] == -Position.ROOK) cs += "k"
            if (b[60] == -Position.KING && b[56] == -Position.ROOK) cs += "q"
            return sb.append(cs.ifEmpty { "-" }).append(" - 0 1").toString()
        }
    }
}
