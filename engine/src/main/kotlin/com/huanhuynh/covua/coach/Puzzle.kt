package com.huanhuynh.covua.coach

import com.huanhuynh.covua.i18n.t
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Bài tập: bên đi (Trắng hoặc Đen) chiếu hết trong [mateIn] nước. [solution] = đường chính (SAN, cả nước bên kia). */
@Serializable
data class Puzzle(
    val id: Int,
    val fen: String,
    val mateIn: Int,
    val theme: String,
    val stars: Int,
    val solution: List<String>,
    /** Độ khó 1–5 (nội dung v2); 0 = chưa phân loại. */
    val difficulty: Int = 0,
) {
    /** Bên đi trước trong bài (1 Trắng, −1 Đen) – người giải cầm bên này. */
    val side: Int get() = if (fen.trim().split(' ').getOrNull(1) == "b") -1 else 1

    val title: String get() = if (side > 0) {
        if (mateIn == 1) t("Trắng đi – chiếu hết 1 nước", "White to move – mate in 1", "白先——一步杀", "白番 – 1手メイト")
        else t("Trắng đi – chiếu hết trong $mateIn nước", "White to move – mate in $mateIn", "白先——${mateIn}步杀", "白番 – ${mateIn}手メイト")
    } else {
        if (mateIn == 1) t("Đen đi – chiếu hết 1 nước", "Black to move – mate in 1", "黑先——一步杀", "黒番 – 1手メイト")
        else t("Đen đi – chiếu hết trong $mateIn nước", "Black to move – mate in $mateIn", "黑先——${mateIn}步杀", "黒番 – ${mateIn}手メイト")
    }

    /** Lời giải hiển thị theo ngôn ngữ hiện tại (lời giải lưu ký hiệu nội bộ). */
    fun solutionShown(): String = runCatching {
        val p = Position.fromFen(fen)
        solution.joinToString(", ") { code ->
            val m = Notation.parse(p, code).also { require(it != 0) }
            Notation.show(p, m).also { p.make(m) }
        }
    }.getOrDefault(solution.joinToString(", "))

    /** Chủ đề theo ngôn ngữ hiện tại (dữ liệu lưu tiếng Việt). */
    val themeName: String get() = themeLabel(theme)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parseAll(raw: String): List<Puzzle> = json.decodeFromString(raw)

        /** Nội dung v2: {"content_version":2,"puzzles":[...]} */
        fun parsePack(raw: String): Pair<Int, List<Puzzle>> {
            val o = json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
            val ver = (o["content_version"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
            return ver to json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(serializer()), o["puzzles"]!!)
        }
    }
}

/**
 * Phiên giải bài. Người chơi cầm bên đi trước của bài; nước đúng = nước vẫn giữ được chiếu hết trong số nước còn lại
 * (không bắt buộc đúng y lời giải). Máy chống đỡ bằng nước kéo dài nhất.
 */
class PuzzleSession(val puzzle: Puzzle, private val search: Search = Search(16)) {
    val pos: Position = Position.fromFen(puzzle.fen)
    var movesLeft = puzzle.mateIn
        private set
    var solved = false
        private set
    val played = ArrayList<Int>()

    /** Kiểm tra nước của người chơi. true = đúng hướng (đã đi), false = sai (không đi). */
    fun tryMove(m: Int): Boolean {
        if (!pos.isLegal(m)) return false
        pos.make(m)
        val replies = pos.legalMoves()
        if (replies.isEmpty()) {
            if (pos.inCheck()) { solved = true; played += m; movesLeft = 0; return true }
            pos.unmake(); return false // hết nước mà không bị chiếu = hoà, không phải lời giải
        }
        if (movesLeft <= 1) { pos.unmake(); return false }
        // Bên kia chống đỡ; nếu thoát được chiếu hết trong số nước còn lại → sai
        val r = search.think(pos, 2 * (movesLeft - 1) + 1)
        val stillMate = Search.isMate(r.score) && r.score < 0 && Search.mateIn(r.score) <= movesLeft - 1
        if (!stillMate) { pos.unmake(); return false }
        played += m
        return true
    }

    /** Bên kia đáp (gọi sau khi [tryMove] đúng mà chưa xong). */
    fun opponentReply(): Int {
        val r = search.think(pos, 2 * (movesLeft - 1) + 1)
        val m = r.move
        if (m != 0) { pos.make(m); played += m }
        movesLeft--
        return m
    }

    /** Nước đúng tiếp theo cho gợi ý: theo lời giải nếu còn khớp, không thì tìm bằng máy. */
    fun nextSolutionMove(): Int {
        val idx = played.size
        val code = puzzle.solution.getOrNull(idx)
        if (code != null) Notation.parse(pos, code).takeIf { it != 0 }?.let { return it }
        return search.think(pos, 2 * movesLeft + 1).move
    }

    fun hintText(level: Int): String {
        val m = nextSolutionMove()
        if (m == 0) return ""
        val piece = Notation.pieceName(pos[Moves.from(m)])
        return when (level) {
            1 -> t("Quân ra đòn là $piece.", "The piece to move is the $piece.", "出击的棋子是${piece}。", "動かす駒は${piece}です。")
            2 -> {
                val f = Notation.fileName(Moves.to(m), 1)
                val cap = pos[Moves.to(m)].takeIf { it != 0 }?.let { Notation.pieceName(it) }
                if (cap != null) t("$piece đi tới $f, ăn $cap.", "The $piece goes to $f, taking the $cap.", "${piece}走到${f}，吃${cap}。", "${piece}が${f}へ、${cap}を取ります。")
                else t("$piece đi tới $f.", "The $piece goes to $f.", "${piece}走到${f}。", "${piece}が${f}へ。")
            }
            else -> t("Nước đúng: ${Notation.spoken(pos, m)} (${Notation.show(pos, m)}).", "Solution: ${Notation.spoken(pos, m)} (${Notation.show(pos, m)}).", "正解：${Notation.spoken(pos, m)}（${Notation.show(pos, m)}）。", "正解：${Notation.spoken(pos, m)}（${Notation.show(pos, m)}）。")
        }
    }
}

/** Tên chủ đề bài theo ngôn ngữ (dữ liệu kho bài lưu tiếng Việt). */
fun themeLabel(vi: String): String {
    val m = THEMES[vi] ?: return vi
    return t(vi, m[0], m[1], m[2])
}

private val THEMES = mapOf(
    "Hàng cuối" to listOf("Back-rank mate", "底线杀", "バックランクメイト"),
    "Chiếu ngột" to listOf("Smothered mate", "闷杀", "スマザードメイト"),
    "Đòn Hậu" to listOf("Queen mate", "后杀", "クイーンのメイト"),
    "Đòn Xe" to listOf("Rook mate", "车杀", "ルークのメイト"),
    "Đòn Mã" to listOf("Knight mate", "马杀", "ナイトのメイト"),
    "Đòn Tượng" to listOf("Bishop mate", "象杀", "ビショップのメイト"),
    "Tốt chiếu" to listOf("Pawn mate", "兵杀", "ポーンのメイト"),
    "Phong cấp" to listOf("Promotion", "升变", "プロモーション"),
    "Thí quân" to listOf("Sacrifice", "弃子", "サクリファイス"),
    "Phối hợp" to listOf("Combination", "组合杀", "コンビネーション"),
)
