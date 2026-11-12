package com.huanhuynh.covua.engine

import com.huanhuynh.covua.engine.Position.Companion.BISHOP
import com.huanhuynh.covua.engine.Position.Companion.KING
import com.huanhuynh.covua.engine.Position.Companion.KNIGHT
import com.huanhuynh.covua.engine.Position.Companion.PAWN
import com.huanhuynh.covua.engine.Position.Companion.QUEEN
import com.huanhuynh.covua.engine.Position.Companion.ROOK
import com.huanhuynh.covua.i18n.I18n
import com.huanhuynh.covua.i18n.Lang
import kotlin.math.abs

/**
 * Ký hiệu nước đi cờ vua.
 * - [of]: SAN chuẩn quốc tế (Nf3, exd5, O-O, e8=Q+) – dùng làm mã nội bộ (sách khai cuộc, Sổ tay, PGN).
 * - [show]: hiển thị – SAN với chữ quân theo ngôn ngữ (tiếng Việt: M f3, X d1…; mặc định giữ chữ quốc tế).
 * - [spoken]: câu nói cho lời thầy ("Mã f3", "Knight to f3", "马f3", "ナイトf3").
 */
object Notation {
    private val LETTER = arrayOf("", "K", "Q", "R", "B", "N", "")
    private val NAME_VI = arrayOf("", "Vua", "Hậu", "Xe", "Tượng", "Mã", "Tốt")
    private val NAME_EN = arrayOf("", "King", "Queen", "Rook", "Bishop", "Knight", "Pawn")
    private val NAME_ZH = arrayOf("", "王", "后", "车", "象", "马", "兵")
    private val NAME_JA = arrayOf("", "キング", "クイーン", "ルーク", "ビショップ", "ナイト", "ポーン")

    /** Tên ô: "e4". */
    fun fileName(sq: Int, @Suppress("UNUSED_PARAMETER") s: Int = 1): String = Position.squareName(sq)

    /** SAN quốc tế. Phải gọi TRƯỚC khi đi [m] trên [pos]. */
    fun of(pos: Position, m: Int): String {
        val from = Moves.from(m); val to = Moves.to(m)
        val p = pos[from]
        if (p == 0) return Moves.uci(m)
        val t = abs(p)
        val sb = StringBuilder()
        if (t == KING && abs(to - from) == 2) {
            sb.append(if (to > from) "O-O" else "O-O-O")
        } else {
            val capture = pos[to] != 0 || (t == PAWN && (from and 7) != (to and 7))
            if (t == PAWN) {
                if (capture) sb.append('a' + (from and 7)).append('x')
                sb.append(Position.squareName(to))
                val pr = Moves.promo(m)
                if (pr != 0) sb.append('=').append(LETTER[pr])
            } else {
                sb.append(LETTER[t])
                // Phân biệt khi hai quân cùng loại cùng tới được ô đích
                val others = pos.legalMoves().filter { o -> o != m && Moves.to(o) == to && pos[Moves.from(o)] == p }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { (Moves.from(it) and 7) == (from and 7) }
                    val sameRank = others.any { (Moves.from(it) shr 3) == (from shr 3) }
                    when {
                        !sameFile -> sb.append('a' + (from and 7))
                        !sameRank -> sb.append('1' + (from shr 3))
                        else -> sb.append(Position.squareName(from))
                    }
                }
                if (capture) sb.append('x')
                sb.append(Position.squareName(to))
            }
        }
        pos.make(m)
        if (pos.inCheck()) sb.append(if (pos.legalMoves().isEmpty()) '#' else '+')
        pos.unmake()
        return sb.toString()
    }

    /** Hiển thị theo ngôn ngữ: chữ quân dịch theo ngôn ngữ (tiếng Việt: V H X T M), còn lại SAN quốc tế. */
    fun show(pos: Position, m: Int): String {
        val san = of(pos, m)
        if (I18n.lang != Lang.VI) return san
        return localize(san)
    }

    /** Đổi chữ quân SAN sang tiếng Việt (K→V, Q→H, R→X, B→T, N→M). Nhập thành giữ O-O. */
    fun localize(san: String): String {
        if (I18n.lang != Lang.VI || san.startsWith("O-O")) return san
        val sb = StringBuilder()
        for (ch in san) sb.append(
            when (ch) { 'K' -> 'V'; 'Q' -> 'H'; 'R' -> 'X'; 'B' -> 'T'; 'N' -> 'M'; else -> ch },
        )
        return sb.toString()
    }

    /** Đọc SAN (quốc tế) trong thế [pos]; 0 nếu không hợp lệ. */
    fun parse(pos: Position, san: String): Int {
        val clean = san.trim().trimEnd('+', '#', '!', '?')
        for (m in pos.legalMoves()) if (of(pos, m).trimEnd('+', '#') == clean) return m
        return 0
    }

    /** Câu nói cho lời thầy. */
    fun spoken(pos: Position, m: Int): String {
        val from = Moves.from(m); val to = Moves.to(m)
        val p = pos[from]
        if (p == 0) return "?"
        val t = abs(p)
        val target = Position.squareName(to)
        if (t == KING && abs(to - from) == 2) {
            val king = to > from
            return when (I18n.lang) {
                Lang.VI -> if (king) "nhập thành cánh Vua" else "nhập thành cánh Hậu"
                Lang.EN -> if (king) "castling kingside" else "castling queenside"
                Lang.ZH -> if (king) "短易位" else "长易位"
                Lang.JA -> if (king) "キングサイドキャスリング" else "クイーンサイドキャスリング"
            }
        }
        val capture = pos[to] != 0 || (t == PAWN && (from and 7) != (to and 7))
        val promo = Moves.promo(m)
        val name = pieceName(p)
        val base = when (I18n.lang) {
            Lang.VI -> if (capture) "$name ăn ở $target" else "$name lên $target"
            Lang.EN -> if (capture) "$name takes on $target" else "$name to $target"
            Lang.ZH -> if (capture) "${name}吃$target" else "${name}到$target"
            Lang.JA -> if (capture) "${name}が${target}で取る" else "${name}を${target}へ"
        }
        if (promo == 0) return base
        val pn = pieceName(promo * (if (p > 0) 1 else -1))
        return base + when (I18n.lang) {
            Lang.VI -> ", phong $pn"
            Lang.EN -> ", promoting to a $pn"
            Lang.ZH -> "，升变为$pn"
            Lang.JA -> "、${pn}に昇格"
        }
    }

    fun pieceName(p: Int): String = when (I18n.lang) {
        Lang.VI -> NAME_VI[abs(p)]
        Lang.EN -> NAME_EN[abs(p)]
        Lang.ZH -> NAME_ZH[abs(p)]
        Lang.JA -> NAME_JA[abs(p)]
    }

    /** Ký tự hình quân (figurine) Unicode – để vẽ quân và chia sẻ dạng chữ. */
    fun glyph(p: Int): String = if (p > 0) "?♔♕♖♗♘♙"[abs(p)].toString() else "?♚♛♜♝♞♟"[abs(p)].toString()

    @Suppress("unused")
    private val TYPES = intArrayOf(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN)
}
