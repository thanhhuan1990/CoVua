package com.huanhuynh.covua.coach

import com.huanhuynh.covua.engine.Engines
import com.huanhuynh.covua.engine.Eval
import com.huanhuynh.covua.engine.GoLimit
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.OpeningBook
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import com.huanhuynh.covua.i18n.t
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/** Nhãn thầy chấm cho một nước. */
enum class Grade(private val vi: String, private val en: String, private val zh: String, private val ja: String) {
    BRILLIANT("Nước hay", "Brilliant", "妙着", "好手"),
    BOOK("Đúng bài", "Book move", "定式", "定跡"),
    GOOD("Tốt", "Good", "好棋", "良い手"),
    INACCURACY("Chưa chính xác", "Inaccuracy", "欠准确", "不正確"),
    MISTAKE("Sai lầm", "Mistake", "失误", "悪手"),
    BLUNDER("Sai lầm nghiêm trọng", "Blunder", "严重失误", "大悪手");

    val label: String get() = t(vi, en, zh, ja)

    val isBad get() = this == INACCURACY || this == MISTAKE || this == BLUNDER
}

/** Lời thầy cho một nước của người chơi. */
data class MoveReview(
    val move: Int,
    val code: String,
    val grade: Grade,
    val title: String,
    val body: String,
    /** Nước tốt hơn (nếu nước vừa đi mất ≥ ~0,5 Tốt). */
    val better: Int = 0,
    val betterCode: String = "",
    /** Đòn đáp trả của đối phương (để vẽ mũi tên đỏ). */
    val threat: Int = 0,
    /** Điểm mất so với nước tốt nhất (centi-tốt). */
    val loss: Int = 0,
    /** Độ chính xác của nước 0–100. */
    val accuracy: Int = 100,
    /** Nhận xét chi tiết: nước vừa đi làm gì, đối phương đáp thế nào, đánh giá thế cờ. */
    val details: List<String> = emptyList(),
    /** Phân tích nước tốt hơn (nếu có): vì sao tốt hơn, diễn biến dự kiến, chênh lệch. */
    val betterDetails: List<String> = emptyList(),
    /** Mã lý do (bitmask [CoachTags]) – dùng cho chủ đề thẻ Sổ tay. */
    val tags: Int = 0,
    /** Các "nước đạt" (mất ≤ 50 cp so với nước tốt nhất) ở phân tích đầu lượt. */
    val okMoves: List<Int> = emptyList(),
    /** Điểm nước tốt nhất / nước đã đi (góc nhìn người đi). */
    val bestScore: Int = 0,
    val playedScore: Int = 0,
)

/** Mã lý do của thầy (bitmask), lưu vào move_eval.tags và theme thẻ Sổ tay. */
object CoachTags {
    const val HANGING = 1          // đi vào ô không được bảo vệ / quân bị treo
    const val FORK = 2             // bị bắt đôi
    const val MISSED_MATE = 4      // bỏ lỡ chiếu hết
    const val ALLOWED_MATE = 8     // để bị chiếu hết
    const val MISSED_CAPTURE = 16  // bỏ lỡ ăn quân
    const val LOST_PIECE = 32      // quân bị đe doạ chưa cứu / bỏ quân không ai giữ
    const val PASSIVE = 64         // nước thụ động
    const val CHECK = 128          // nước chiếu
    const val CAPTURE = 256        // nước ăn quân

    private val NAMES: Map<Int, String> get() = linkedMapOf(
        ALLOWED_MATE to t("Để bị chiếu hết", "Allowed mate", "被将死", "メイトされた"), MISSED_MATE to t("Bỏ lỡ chiếu hết", "Missed mate", "错过杀棋", "メイトを見逃した"),
        FORK to t("Bị bắt đôi", "Forked", "被双重攻击", "フォークされた"), HANGING to t("Quân bị treo", "Hanging piece", "子力悬空", "浮き駒"),
        LOST_PIECE to t("Mất quân", "Lost piece", "丢子", "駒損"), MISSED_CAPTURE to t("Bỏ lỡ ăn quân", "Missed capture", "漏吃子", "取り逃がし"),
        PASSIVE to t("Nước thụ động", "Passive move", "消极着法", "消極的な手"),
    )
    private val WEAK: String get() = t("Nước yếu", "Weak move", "软着", "弱い手")

    /** Chủ đề chính (ưu tiên lỗi nặng nhất). */
    fun theme(tags: Int): String = NAMES.entries.firstOrNull { tags and it.key != 0 }?.value ?: WEAK

    fun themes(tags: Int): List<String> = NAMES.entries.filter { tags and it.key != 0 }.map { it.value }

    val ALL_THEMES: List<String> get() = NAMES.values.toList() + WEAK
}

/** Phân tích đầu lượt người chơi: điểm của mọi nước (dùng cho cảnh báo + chấm nhanh). */
class TurnAnalysis(
    val fen: String,
    val best: Int,
    val bestScore: Int,
    val scores: Map<Int, Int>,
) {
    fun lossOf(m: Int): Int? = scores[m]?.let { max(0, bestScore - it) }
}

data class Warning(val to: Int, val loss: Int, val text: String)

data class Hint(val move: Int, val code: String, val pieceText: String, val title: String, val body: String)

/**
 * "Thầy" – thuần Kotlin, chạy trên luồng nền. Dùng một [Search] riêng.
 * Mọi tham số [Position] phải là bản sao riêng (có lịch sử) – thầy đi/lùi trên đó rồi trả lại nguyên trạng.
 */
class Coach(private val search: Search = Search(16), private val random: Random = Random.Default) {

    fun cancel() { search.cancel = true }

    // ---------------- Đầu lượt ----------------

    fun analyzeTurn(pos: Position, depth: Int = 3): TurnAnalysis {
        val legal = pos.legalMoves()
        if (legal.isNotEmpty() && useExternal) external(pos, legal)?.let { return it }
        val sc = search.scoreAll(pos, depth, legal)
        var bi = 0
        for (i in sc.indices) if (sc[i] > sc[bi]) bi = i
        val map = HashMap<Int, Int>(legal.size * 2)
        for (i in legal.indices) map[legal[i]] = sc[i]
        return TurnAnalysis(pos.toFen(), if (legal.isEmpty()) 0 else legal[bi], if (legal.isEmpty()) -Search.MATE else sc[bi], map)
    }

    /** Ô nguy hiểm khi cầm quân ở [from]: đi vào sẽ mất ≥ ~1,5 Tốt so với nước tốt nhất. */
    fun warningsFor(pos: Position, from: Int, ta: TurnAnalysis): Map<Int, Warning> {
        val out = HashMap<Int, Warning>()
        for ((m, _) in ta.scores) {
            if (Moves.from(m) != from) continue
            val loss = ta.lossOf(m) ?: continue
            if (loss < WARN_LOSS) continue
            out[Moves.to(m)] = Warning(Moves.to(m), loss, explainDanger(pos, m, loss))
        }
        return out
    }

    private fun explainDanger(pos: Position, m: Int, loss: Int): String {
        val me = pos.side
        val moved = pos[Moves.from(m)]
        pos.make(m)
        val reply = search.think(pos, 3).let { it.move to it.score }
        val text = if (Search.isMate(reply.second) && reply.second > 0) {
            val n = Search.mateIn(reply.second)
            t("Đi vào đây thì đối phương có đòn chiếu hết sau $n nước.", "Going here lets the opponent mate in $n.", "走这里对方${n}步可以将死你。", "ここに行くと、相手に${n}手でメイトされます。")
        } else if (reply.first != 0) {
            val r = reply.first
            val victim = pos[Moves.to(r)]
            val attacker = pos[Moves.from(r)]
            when {
                victim != 0 && Moves.to(r) == Moves.to(m) ->
                    t("${Notation.pieceName(moved)} vào ô này sẽ bị ${Notation.pieceName(attacker)} đối phương ăn (${Notation.show(pos, r)}).",
                        "Your ${Notation.pieceName(moved)} would be taken here by the opponent's ${Notation.pieceName(attacker)} (${Notation.show(pos, r)}).",
                        "${Notation.pieceName(moved)}走到这里会被对方${Notation.pieceName(attacker)}吃掉（${Notation.show(pos, r)}）。", "${Notation.pieceName(moved)}はここで相手の${Notation.pieceName(attacker)}に取られます（${Notation.show(pos, r)}）。")
                victim != 0 ->
                    t("Nếu đi, ${Notation.pieceName(attacker)} đối phương ăn ${Notation.pieceName(victim)} của bạn (${Notation.show(pos, r)}).",
                        "If you play this, the opponent's ${Notation.pieceName(attacker)} takes your ${Notation.pieceName(victim)} (${Notation.show(pos, r)}).",
                        "这样走，对方${Notation.pieceName(attacker)}会吃掉你的${Notation.pieceName(victim)}（${Notation.show(pos, r)}）。", "この手だと、相手の${Notation.pieceName(attacker)}があなたの${Notation.pieceName(victim)}を取ります（${Notation.show(pos, r)}）。")
                else -> forkText(pos, r, me) ?: t("Nước này cho đối phương đòn mạnh: ${Notation.spoken(pos, r)}.",
                    "This gives the opponent a strong blow: ${Notation.spoken(pos, r)}.", "这步让对方有强手：${Notation.spoken(pos, r)}。", "この手は相手に強い手を与えます：${Notation.spoken(pos, r)}。")
            }
        } else t("Nước này làm thế cờ xấu đi nhiều.", "This move makes your position much worse.", "这步棋会让局面变差很多。", "この手で形勢が大きく悪くなります。")
        pos.unmake()
        return text
    }

    // ---------------- Sau nước đi ----------------

    /**
     * Chấm nước [move] của bên đang đi ở [pos]. [history] = ký hiệu các nước trước đó (để nhận diện khai cuộc).
     * [ta] (nếu có) là phân tích đầu lượt cùng thế để chấm nhất quán và nhanh.
     */
    fun review(pos: Position, move: Int, history: List<String>, ta: TurnAnalysis? = null, depth: Int = 3, useBook: Boolean = true): MoveReview {
        val me = pos.side
        val bookCode = Notation.of(pos, move)   // ký hiệu nội bộ để tra sách khai cuộc
        val code = Notation.show(pos, move)
        val legal = pos.legalMoves()
        val analysis = ta?.takeIf { it.fen == pos.toFen() && it.scores.containsKey(move) } ?: analyzeTurn(pos, depth)
        val best = analysis.best
        val bestScore = analysis.bestScore
        val played = analysis.scores[move] ?: bestScore
        val loss = max(0, bestScore - played)
        val accuracy = (100.0 * exp(-loss / 300.0)).toInt().coerceIn(0, 100)
        val bestCode = if (best != 0) Notation.show(pos, best) else ""
        val bestSpoken = if (best != 0) Notation.spoken(pos, best) else ""
        val captured = pos[Moves.to(move)]
        val movedPiece = pos[Moves.from(move)]
        val bestCaptured = if (best != 0) pos[Moves.to(best)] else 0
        val hangingBefore = hanging(pos, me)

        // --- Thế sau nước đi ---
        pos.make(move)
        val gaveCheck = pos.inCheck()
        val noMoves = pos.legalMoves().isEmpty()
        val mated = noMoves && gaveCheck
        val stalemate = noMoves && !gaveCheck
        val reply = if (!noMoves) search.think(pos, depth).move else 0
        val replyCode = if (reply != 0) Notation.show(pos, reply) else ""
        val replySpoken = if (reply != 0) Notation.spoken(pos, reply) else ""
        val replyVictim = if (reply != 0) pos[Moves.to(reply)] else 0
        val replyAttacker = if (reply != 0) pos[Moves.from(reply)] else 0
        val fork = if (reply != 0) forkText(pos, reply, me) else null
        val hangingAfter = hanging(pos, me)
        pos.unmake()

        val bookMoves = if (useBook) OpeningBook.bookMoves(history) else emptyList()
        val opening = if (useBook) OpeningBook.nameExact(history + bookCode) else null
        var tags = 0
        if (captured != 0) tags = tags or CoachTags.CAPTURE
        if (gaveCheck) tags = tags or CoachTags.CHECK

        // --- Chấm ---
        val grade = when {
            mated -> Grade.BRILLIANT
            bookCode.trimEnd('+', '#') in bookMoves && loss < 120 -> Grade.BOOK
            Search.isMate(played) && played < 0 && !(Search.isMate(bestScore) && bestScore < 0) -> Grade.BLUNDER
            Search.isMate(bestScore) && bestScore > 0 && !(Search.isMate(played) && played > 0) -> if (loss > 600) Grade.BLUNDER else Grade.MISTAKE
            loss <= 25 -> if (move == best && (captured != 0 || gaveCheck || legal.size > 1 && isOnlyGood(analysis, move))) Grade.BRILLIANT else Grade.GOOD
            loss <= 80 -> Grade.GOOD
            loss <= 180 -> Grade.INACCURACY
            loss <= 400 -> Grade.MISTAKE
            else -> Grade.BLUNDER
        }

        // --- Lời giải thích ---
        val mark = when (grade) { Grade.MISTAKE -> "?"; Grade.BLUNDER -> "??"; Grade.BRILLIANT -> "!"; else -> "" }
        val title: String
        val body = StringBuilder()
        var threat = 0
        var better = 0
        when {
            mated -> {
                title = "$code – " + t("Chiếu hết!", "Checkmate!", "将死！", "チェックメイト！")
                body.append(pick(
                    t("Vua đối phương hết đường chạy. Bạn thắng ván này!", "The enemy King has nowhere to go. You win!", "对方的王无路可走，你赢了！", "相手のキングに逃げ場はありません。あなたの勝ちです！"),
                    t("Đòn kết thúc đẹp – Vua đối phương bị chiếu mà không còn nước đi.", "A beautiful finish – the enemy King is in check with no moves left.", "漂亮的收官——对方的王被将军且无棋可走。", "見事なフィニッシュ – 相手のキングはチェックされて動ける手がありません。"),
                ))
            }
            stalemate -> {
                title = "$code – " + t("Hết nước – hoà!", "Stalemate – draw!", "逼和！", "ステイルメイト – 引き分け！")
                body.append(t("Vua đối phương không bị chiếu nhưng không còn nước đi hợp lệ: ván hoà.",
                    "The enemy King isn't in check but has no legal move: the game is drawn.", "对方的王没有被将军，但已无合法着法：和棋。", "相手のキングはチェックされていませんが、合法手がありません：引き分けです。"))
                if (bestScore > 200) body.append(t(" Bạn đang hơn nhiều – lẽ ra nên để lại cho Vua một ô thở.", " You were well ahead – leave the King a square to breathe next time.", " 你本来大优——下次记得给王留一个空格。", " 大差で優勢でした – 次はキングに逃げ道を1つ残しましょう。"))
            }
            grade == Grade.BOOK -> {
                title = "$code – ${opening?.title ?: t("Đúng bài khai cuộc", "Book opening move", "开局定式", "定跡の手")}"
                body.append(opening?.note ?: pick(
                    t("Nước khai cuộc quen thuộc, đúng bài bản.", "A well-known, textbook opening move.", "常见的开局着法，规范正确。", "よく知られた、教科書通りの序盤の手。"),
                    t("Đi đúng sách: phát triển quân, giữ thế cân bằng.", "By the book: develop your pieces and keep the balance.", "按定式走：出子并保持均势。", "定跡通り：駒を展開してバランスを保ちましょう。"),
                ))
            }
            grade.isBad -> {
                val what = when {
                    Search.isMate(played) && played < 0 -> {
                        tags = tags or CoachTags.ALLOWED_MATE
                        threat = reply
                        val n = Search.mateIn(played)
                        t("Nước này để đối phương có đòn chiếu hết sau $n nước, bắt đầu bằng $replySpoken ($replyCode).",
                            "This lets the opponent mate in $n, starting with $replySpoken ($replyCode).",
                            "这步让对方${n}步杀，先走${replySpoken}。", "この手で、相手に${n}手メイトを許します。始まりは${replySpoken}（${replyCode}）。")
                    }
                    Search.isMate(bestScore) && bestScore > 0 -> {
                        tags = tags or CoachTags.MISSED_MATE
                        val n = Search.mateIn(bestScore)
                        t("Bạn bỏ lỡ đòn chiếu hết sau $n nước, bắt đầu bằng $bestSpoken ($bestCode).",
                            "You missed a mate in $n, starting with $bestSpoken ($bestCode).",
                            "你错过了${n}步杀，应先走${bestSpoken}。", "${n}手メイトを見逃しました。始まりは${bestSpoken}（${bestCode}）。")
                    }
                    replyVictim != 0 && Moves.to(reply) == Moves.to(move) -> {
                        tags = tags or CoachTags.HANGING
                        threat = reply
                        val guarded = defendedBySide(pos, move, me)
                        val mp = Notation.pieceName(movedPiece); val ra = Notation.pieceName(replyAttacker)
                        if (guarded) t("$mp đi vào ô có bảo vệ nhưng vẫn bị đổi lỗ: $ra đối phương ăn ngay ($replyCode).",
                            "Your $mp moved to a defended square but still loses the trade: the opponent's $ra takes it ($replyCode).",
                            "${mp}走到有保护的位置，但兑换吃亏：对方${ra}立即吃掉（${replyCode}）。", "${mp}は守られたマスに行きましたが、交換で損をします：相手の${ra}がすぐ取ります（${replyCode}）。")
                        else t("$mp đi vào ô không có quân bảo vệ: $ra đối phương ăn ngay ($replyCode).",
                            "Your $mp moved to an undefended square: the opponent's $ra takes it at once ($replyCode).",
                            "${mp}走到无保护的位置：对方${ra}立即吃掉（${replyCode}）。", "${mp}は守りのないマスに行きました：相手の${ra}がすぐ取ります（${replyCode}）。")
                    }
                    replyVictim != 0 -> {
                        tags = tags or CoachTags.LOST_PIECE
                        threat = reply
                        val newly = hangingAfter.any { it == Moves.to(reply) } && hangingBefore.none { it == Moves.to(reply) }
                        val rv = Notation.pieceName(replyVictim)
                        if (newly) t("Nước này bỏ $rv không ai giữ: đối phương $replySpoken ($replyCode) ăn $rv.",
                            "This leaves your $rv undefended: the opponent plays $replySpoken ($replyCode) and takes it.",
                            "这步让你的${rv}失去保护：对方走${replySpoken}吃掉${rv}。", "この手で${rv}の守りがなくなります：相手が${replySpoken}（${replyCode}）で${rv}を取ります。")
                        else t("$rv của bạn đang bị đe doạ mà chưa được cứu: đối phương $replySpoken ($replyCode).",
                            "Your $rv was under attack and wasn't saved: the opponent plays $replySpoken ($replyCode).",
                            "你的${rv}受到威胁却没有解救：对方走${replySpoken}。", "${rv}が狙われていたのに助けていません：相手は${replySpoken}（${replyCode}）。")
                    }
                    fork != null -> { tags = tags or CoachTags.FORK; threat = reply; fork }
                    bestCaptured != 0 && captured == 0 -> {
                        tags = tags or CoachTags.MISSED_CAPTURE
                        val bc = Notation.pieceName(bestCaptured)
                        t("Bạn bỏ lỡ cơ hội ăn $bc bằng $bestSpoken ($bestCode).", "You missed the chance to take a $bc with $bestSpoken ($bestCode).",
                            "你错过了用${bestSpoken}吃${bc}的机会。", "${bestSpoken}（${bestCode}）で${bc}を取るチャンスを逃しました。")
                    }
                    else -> { tags = tags or CoachTags.PASSIVE; pick(
                        t("Nước này làm thế cờ kém đi – đối phương được chủ động.", "This weakens your position – the opponent takes the initiative.", "这步让局面变差——对方取得主动。", "この手で形勢が悪くなり、相手に主導権を渡します。"),
                        t("Nước đi thụ động, để mất thời gian phát triển quân.", "A passive move that wastes time developing.", "着法消极，浪费出子时间。", "消極的な手で、駒の展開が遅れます。"),
                        t("Còn nước đi hiệu quả hơn ở thế này.", "There is a more effective move here.", "这里有更有效的着法。", "この局面にはもっと効果的な手があります。"),
                    ) }
                }
                title = "$code$mark – ${shortBad(grade, replyVictim, movedPiece, played, bestScore)}"
                body.append(what)
                if (best != 0 && best != move) better = best
            }
            else -> {
                val why = goodReason(pos, move)
                    ?: if (hangingBefore.size > hangingAfter.size) t("giữ quân khỏi bị ăn", "saves a piece from capture", "保住棋子不被吃", "駒が取られるのを防ぐ") else null
                title = "$code$mark – ${why?.replaceFirstChar { it.uppercase() } ?: if (grade == Grade.BRILLIANT) t("Nước tốt nhất", "Best move", "最佳着法", "最善手") else t("Nước chắc chắn", "Solid move", "稳健的一步", "堅実な手")}"
                body.append(
                    when {
                        grade == Grade.BRILLIANT && move == best -> pick(
                            t("Thầy cũng sẽ đi nước này.", "That's exactly what I would play.", "老师也会这么走。", "先生もここに指します。"),
                            t("Chính xác – đây là nước tốt nhất.", "Precise – this is the best move.", "准确——这是最佳着法。", "正確 – これが最善手です。"),
                            t("Rất hay, đúng nước thầy chọn.", "Very good – just what I'd choose.", "很好，正是老师选的那步。", "とても良い – 先生が選ぶ手と同じです。"),
                        )
                        grade == Grade.BRILLIANT -> t("Nước tốt ngang nước thầy chọn.", "As good as my choice.", "和老师的选择一样好。", "先生の選んだ手と同じくらい良い手です。")
                        else -> pick(
                            t("Nước chắc chắn, không để lộ sơ hở.", "Solid – no weaknesses left behind.", "稳健，没有留下破绽。", "堅実 – 弱点を残していません。"),
                            t("Ổn – thế cờ vẫn giữ được.", "Fine – the position holds.", "不错，局面稳住了。", "大丈夫 – 形勢は保てています。"),
                            t("Nước tốt, giữ thế cân bằng.", "Good move, keeps the balance.", "好棋，保持均势。", "良い手、バランスを保っています。"),
                        )
                    },
                )
                if (opening != null) body.append(t(" Thế cờ: ", " Opening: ", " 布局：", " 定跡：")).append(opening.title).append(t(".", ".", "。", "。"))
            }
        }
        // --- Nhận xét chi tiết ---
        val details = ArrayList<String>()
        if (!noMoves) {
            details += t("Nước đi: ", "Move: ", "着法：", "着手：") + describeMove(pos, move).joinToString(t("; ", "; ", "；", "、")) + t(".", ".", "。", "。")
            if (reply != 0) {
                pos.make(move)
                val rr = goodReason(pos, reply)
                pos.unmake()
                details += t("Đối phương nhiều khả năng đáp $replySpoken ($replyCode)", "The opponent will likely reply $replySpoken ($replyCode)", "对方很可能应$replySpoken", "相手はおそらく${replySpoken}（${replyCode}）と応じます") +
                    (rr?.let { " – $it" } ?: "") + t(".", ".", "。", "。")
            }
            details += t("Thế cờ sau nước này: ", "Position after this move: ", "此步之后局面：", "この手の後の形勢：") + assess(played) + t(".", ".", "。", "。")
        }
        val betterDetails = ArrayList<String>()
        if (best != 0 && best != move && loss >= 30 && !noMoves) {
            if (better == 0) better = best
            betterDetails += "$bestSpoken ($bestCode): " + describeMove(pos, best).drop(1).joinToString(t("; ", "; ", "；", "、")) + t(".", ".", "。", "。")
            val pv = line(pos, best, 4)
            if (pv.size >= 2) betterDetails += t("Diễn biến dự kiến: ", "Expected line: ", "预计变化：", "予想される進行：") + pvText(pv) + t(".", ".", "。", "。")
            betterDetails += t("So với nước bạn đi: tốt hơn ", "Compared with your move: better by ", "与你的着法相比：好", "あなたの手と比べて：") + lossPhrase(loss) +
                t(". Sau nước này thế cờ: ", ". After it the position is: ", "。之后局面：", "良い。この手の後の形勢：") + assess(bestScore) + t(".", ".", "。", "。")
        }
        return MoveReview(
            move, code, grade, title, body.toString(), better, if (better != 0) bestCode else "", threat, loss, accuracy,
            details, betterDetails, tags,
            okMoves = analysis.scores.filter { bestScore - it.value <= OK_LOSS }.keys.toList(),
            bestScore = bestScore, playedScore = played,
        )
    }

    /** Các ý của nước [m] (thế [pos] trước khi đi), viết thường, nối bằng "; ". */
    fun describeMove(pos: Position, m: Int): List<String> {
        val from = Moves.from(m)
        val to = Moves.to(m)
        val p = pos[from]
        val s = if (p > 0) 1 else -1
        val ty = abs(p)
        val name = Notation.pieceName(p)
        val victim = pos[to]
        val wasCheck = pos.inCheck()
        val hangBefore = hanging(pos, s).filter { it != from }.toSet()
        pos.make(m)
        val check = pos.inCheck()
        val mate = check && pos.legalMoves().isEmpty()
        val threats = threatenedBy(pos, to, s)
        val passedNow = ty == Position.PAWN && isPassedPawn(pos, to, s)
        val hangAfter = hanging(pos, s)
        val movedHanging = to in hangAfter
        val saved = hangBefore.filter { it !in hangAfter }.map { pos[it] to it }
        val exposed = hangAfter.filter { it != to && it !in hangBefore }.map { pos[it] to it }
        pos.unmake()
        val out = ArrayList<String>()
        out += Notation.spoken(pos, m)
        val vn = if (victim != 0) Notation.pieceName(victim) else ""
        val promo = Moves.promo(m)
        val epCapture = ty == Position.PAWN && victim == 0 && (from and 7) != (to and 7)
        if (wasCheck) out += t("thoát chiếu", "escapes check", "解除将军", "チェックを逃れる")
        if (mate) out += t("chiếu hết, đối phương hết nước đi", "checkmate – the opponent has no moves", "将死，对方无棋可走", "チェックメイト、相手は指す手がない")
        else if (victim != 0 && check) out += t("ăn $vn và chiếu Vua", "takes a $vn with check", "吃${vn}并将军", "${vn}を取ってチェック")
        else if (victim != 0) { val pts = Eval.pieceValue(victim, to) / 100; out += t("ăn $vn (~$pts điểm quân)", "takes a $vn (~$pts points)", "吃${vn}（约${pts}分）", "${vn}を取る（約${pts}点）") }
        else if (epCapture) out += t("bắt Tốt qua đường", "captures en passant", "吃过路兵", "アンパッサンで取る")
        else if (check) out += t("chiếu Vua, buộc đối phương phải đỡ", "gives check, forcing a defence", "将军，迫使对方应将", "チェック、相手に受けを強いる")
        if (promo != 0) Notation.pieceName(promo).let { out += t("phong cấp thành $it", "promotes to a $it", "升变为$it", "${it}に昇格") }
        if (!mate && threats.isNotEmpty()) {
            out += if (threats.size >= 2) {
                val a = Notation.pieceName(threats[0]); val b = Notation.pieceName(threats[1])
                t("bắt đôi $a và $b", "forks the $a and $b", "捉双：${a}和${b}", "${a}と${b}のフォーク")
            } else { val a = Notation.pieceName(threats[0]); t("đe ăn $a", "threatens the $a", "威胁吃${a}", "${a}を狙う") }
        }
        for ((v, sq) in saved.take(2)) { val a = Notation.pieceName(v); val f = Notation.fileName(sq, s); out += t("giữ $a ($f) khỏi bị ăn", "saves the $a ($f) from capture", "保住${a}（${f}）", "${a}（${f}）を取られないように守る") }
        positional(pos, m, wasCheck, passedNow)?.let { out += it }
        if (movedHanging && victim == 0) out += t("nhưng $name đứng ở ô bị tấn công mà không đủ quân bảo vệ", "but the $name stands on an attacked square without enough defenders", "但${name}站在受攻击的位置，保护不够", "しかし${name}は攻撃されているマスにいて、守りが足りない")
        else if (movedHanging) out += t("nhưng $name có thể bị ăn lại", "but the $name can be taken back", "但${name}可能被吃回", "しかし${name}は取り返される可能性がある")
        for ((v, sq) in exposed.take(2)) { val a = Notation.pieceName(v); val f = Notation.fileName(sq, s); out += t("để $a ($f) mất bảo vệ", "leaves the $a ($f) undefended", "使${a}（${f}）失去保护", "${a}（${f}）の守りがなくなる") }
        if (out.size == 1) out += t("cải thiện vị trí $name, chờ cơ hội", "improves the $name's position and waits for a chance", "改善${name}的位置，等待机会", "${name}の位置を改善し、チャンスを待つ")
        return out
    }

    /** Đánh giá thế cờ từ điểm (góc nhìn người vừa đi trước khi đi = người chơi). */
    private fun assess(score: Int): String = when {
        Search.isMate(score) && score > 0 -> Search.mateIn(score).let { t("bạn có đòn chiếu hết trong $it nước", "you have mate in $it", "你有${it}步杀", "あなたに${it}手メイトがある") }
        Search.isMate(score) -> Search.mateIn(score).let { t("đối phương có đòn chiếu hết trong $it nước", "the opponent has mate in $it", "对方有${it}步杀", "相手に${it}手メイトがある") }
        score >= 600 -> (score / 100).let { t("bạn đang thắng thế rõ (hơn khoảng $it điểm quân)", "you are clearly winning (about $it points ahead)", "你明显占优（约多${it}分）", "あなたがはっきり勝勢（約${it}点リード）") }
        score >= 250 -> (score / 100).let { t("bạn đang ưu thế (hơn khoảng $it điểm quân)", "you are better (about $it points ahead)", "你占优势（约多${it}分）", "あなたが優勢（約${it}点リード）") }
        score >= 80 -> t("bạn nhỉnh hơn một chút", "you are slightly better", "你稍好", "あなたがやや有利")
        score > -80 -> t("hai bên cân bằng", "the position is equal", "双方均势", "形勢は互角")
        score > -250 -> t("đối phương nhỉnh hơn một chút", "the opponent is slightly better", "对方稍好", "相手がやや有利")
        score > -600 -> (-score / 100).let { t("đối phương đang ưu thế (hơn khoảng $it điểm quân)", "the opponent is better (about $it points ahead)", "对方占优势（约多${it}分）", "相手が優勢（約${it}点リード）") }
        else -> (-score / 100).let { t("đối phương thắng thế rõ (hơn khoảng $it điểm quân)", "the opponent is clearly winning (about $it points ahead)", "对方明显占优（约多${it}分）", "相手がはっきり勝勢（約${it}点リード）") }
    }

    private fun lossPhrase(loss: Int): String = when {
        loss >= 5000 -> t("rất nhiều (quyết định thắng thua)", "a lot (decides the game)", "很多（决定胜负）", "かなり（勝敗を決める）")
        loss >= 800 -> t("khoảng một Hậu", "about a Queen", "约一个后", "クイーン1枚ほど")
        loss >= 430 -> t("khoảng một Xe", "about a Rook", "约一个车", "ルーク1枚ほど")
        loss >= 260 -> t("khoảng một Mã hoặc Tượng", "about a Knight or Bishop", "约一个马或象", "ナイトかビショップ1枚ほど")
        loss >= 170 -> t("khoảng hai Tốt", "about two Pawns", "约两个兵", "ポーン2枚ほど")
        loss >= 80 -> t("khoảng một Tốt", "about a Pawn", "约一兵", "ポーン1枚ほど")
        else -> t("một chút về vị trí", "a little, positionally", "局面上略好", "位置的に少し")
    }

    /** "bạn Nf3, đối phương Nc6, bạn Bb5…" */
    private fun pvText(pv: List<String>): String =
        pv.mapIndexed { i, c -> (if (i % 2 == 0) t("bạn ", "you ", "你", "あなた ") else t("đối phương ", "opponent ", "对方", "相手 ")) + c }.joinToString(t(", ", ", ", "，", "、"))

    /** Diễn biến chính bắt đầu bằng [first]: ký hiệu các nước (cả hai bên). */
    private fun line(pos: Position, first: Int, plies: Int): List<String> {
        val out = ArrayList<String>()
        var made = 0
        var m = first
        while (m != 0 && out.size < plies) {
            out += Notation.show(pos, m)
            pos.make(m); made++
            if (pos.legalMoves().isEmpty()) break
            m = search.think(pos, (3 - out.size / 2).coerceAtLeast(1)).move
        }
        repeat(made) { pos.unmake() }
        return out
    }

    private fun isOnlyGood(a: TurnAnalysis, m: Int): Boolean {
        val sc = a.scores[m] ?: return false
        val second = a.scores.filterKeys { it != m }.values.maxOrNull() ?: return true
        return sc - second >= 150
    }

    private fun shortBad(g: Grade, replyVictim: Int, moved: Int, played: Int, bestScore: Int): String = when {
        Search.isMate(played) && played < 0 -> t("để bị chiếu hết", "allows mate", "被将死", "メイトを許す")
        Search.isMate(bestScore) && bestScore > 0 -> t("bỏ lỡ chiếu hết", "misses mate", "错过杀棋", "メイトを見逃す")
        replyVictim != 0 && abs(replyVictim) != Position.PAWN -> Notation.pieceName(replyVictim).let { t("mất $it", "loses the $it", "丢$it", "${it}を失う") }
        g == Grade.INACCURACY -> t("chưa chính xác", "inaccurate", "欠准确", "不正確")
        else -> t("nước yếu", "weak move", "软着", "弱い手")
    }

    /** Lý do tích cực của nước [m] trên [pos] (trước khi đi). */
    fun goodReason(pos: Position, m: Int): String? {
        val from = Moves.from(m)
        val to = Moves.to(m)
        val p = pos[from]
        val s = if (p > 0) 1 else -1
        val ty = abs(p)
        val victim = pos[to]
        val wasCheck = pos.inCheck()
        pos.make(m)
        val check = pos.inCheck()
        val mate = check && pos.legalMoves().isEmpty()
        val threatened = threatenedBy(pos, to, s)
        val passedNow = ty == Position.PAWN && isPassedPawn(pos, to, s)
        pos.unmake()
        val promo = Moves.promo(m)
        return when {
            mate -> t("chiếu hết", "checkmate", "将死", "チェックメイト")
            promo != 0 -> Notation.pieceName(promo).let { t("phong cấp thành $it", "promotes to a $it", "升变为$it", "${it}に昇格") }
            victim != 0 && check -> Notation.pieceName(victim).let { t("ăn $it và chiếu Vua", "takes the $it with check", "吃${it}并将军", "${it}を取ってチェック") }
            victim != 0 -> Notation.pieceName(victim).let { t("ăn $it", "takes the $it", "吃$it", "${it}を取る") }
            check -> t("chiếu Vua, giành thế chủ động", "check, seizing the initiative", "将军，夺取主动", "チェックして主導権を握る")
            threatened.size >= 2 -> { val a = Notation.pieceName(threatened[0]); val b = Notation.pieceName(threatened[1]); t("bắt đôi $a và $b", "forks the $a and $b", "双重攻击${a}和${b}", "${a}と${b}のフォーク") }
            threatened.isNotEmpty() && abs(threatened[0]) != Position.PAWN ->
                Notation.pieceName(threatened[0]).let { t("đe ăn $it", "threatens the $it", "威胁吃$it", "${it}を狙う") }
            else -> positional(pos, m, wasCheck, passedNow)
        }
    }

    /** Ý đồ thế trận của nước [m] (thế [pos] trước khi đi): ra quân, nhập thành, chiếm trung tâm, cột mở, Tốt thông. */
    private fun positional(pos: Position, m: Int, wasCheck: Boolean, passedNow: Boolean): String? {
        val from = Moves.from(m)
        val to = Moves.to(m)
        val p = pos[from]
        val s = if (p > 0) 1 else -1
        val ty = abs(p)
        val backRank = if (s > 0) 0 else 7
        val center = to in CENTER
        return when {
            ty == Position.KING && abs(to - from) == 2 -> t("nhập thành – đưa Vua vào nơi an toàn và đưa Xe ra trận", "castles – the King goes to safety and the Rook joins the game", "易位——王到安全处，车投入战斗", "キャスリング – キングを安全な場所へ、ルークを戦いに参加させる")
            (ty == Position.KNIGHT || ty == Position.BISHOP) && (from shr 3) == backRank ->
                Notation.pieceName(p).let { if (center || to in WIDE_CENTER) t("ra $it, hướng về trung tâm", "develops the $it toward the centre", "出$it，指向中心", "${it}を中央に向けて展開") else t("ra $it, phát triển quân", "develops the $it", "出$it，发展子力", "${it}を展開") }
            ty == Position.PAWN && center && pos.ply < 30 -> t("chiếm trung tâm bằng Tốt", "takes the centre with a pawn", "用兵占据中心", "ポーンで中央を取る")
            ty == Position.PAWN && passedNow && ((to shr 3) == (if (s > 0) 5 else 2) || (to shr 3) == (if (s > 0) 6 else 1)) -> t("đẩy Tốt thông về gần ô phong cấp", "pushes the passed pawn toward promotion", "推进通路兵接近升变", "パスポーンをプロモーションに向けて進める")
            ty == Position.ROOK && openFile(pos, to and 7) && !openFile(pos, from and 7) -> t("đưa Xe lên cột mở", "puts the Rook on an open file", "车占据开放线", "ルークをオープンファイルへ")
            ty == Position.ROOK && (from shr 3) == backRank && (to shr 3) == backRank -> t("đưa Xe vào trận", "brings the Rook into play", "车投入战斗", "ルークを戦いに参加させる")
            ty == Position.QUEEN && pos.ply < 12 && (from shr 3) == backRank -> t("ra Hậu sớm – cẩn thận Hậu bị quân nhỏ đuổi", "brings the Queen out early – watch out for it being chased by minor pieces", "过早出后——小心被小子追赶", "クイーンの早出し – マイナーピースに追い回されないよう注意")
            ty == Position.KING && !wasCheck && pos.ply > 40 -> t("đưa Vua lên hoạt động trong tàn cuộc", "activates the King in the endgame", "习题中让王参与战斗", "終盤でキングを活用")
            else -> null
        }
    }

    private fun openFile(pos: Position, f: Int): Boolean {
        for (r in 0..7) if (abs(pos[r * 8 + f]) == Position.PAWN) return false
        return true
    }

    private fun isPassedPawn(pos: Position, sq: Int, s: Int): Boolean {
        val f = sq and 7
        var r = (sq shr 3) + s
        while (r in 0..7) {
            for (df in -1..1) { val ff = f + df; if (ff in 0..7 && pos[r * 8 + ff] == -Position.PAWN * s) return false }
            r += s
        }
        return true
    }

    /** Quân đối phương (giá trị ≥ Mã, hoặc không được bảo vệ) mà quân ở [sq] (bên [s]) đang đe ăn. */
    private fun threatenedBy(pos: Position, sq: Int, s: Int): List<Int> {
        val out = ArrayList<Int>()
        for (t in 0 until 64) {
            val v = pos[t]
            if (v == 0 || (v > 0) == (s > 0) || abs(v) == Position.KING) continue
            if (sq !in pos.attackers(t, s)) continue
            val defended = pos.attackers(t, -s, stopAtFirst = true).isNotEmpty()
            if (!defended || Eval.pieceValue(v, t) > Eval.pieceValue(pos[sq], sq)) out += v
        }
        return out.sortedByDescending { Eval.VALUE[abs(it)] }
    }

    /** Sau đòn [reply] (trên thế hiện tại, đối phương đi), quân đó có bắt đôi ≥ 2 quân của bên [victimSide] không. */
    private fun forkText(pos: Position, reply: Int, victimSide: Int): String? {
        val to = Moves.to(reply)
        val attacker = pos[Moves.from(reply)]
        pos.make(reply)
        val targets = threatenedBy(pos, to, -victimSide).filter { abs(it) != Position.PAWN }
        val check = pos.inCheck()
        pos.unmake()
        val n = targets.size + if (check) 1 else 0
        if (n < 2) return null
        val names = (if (check) listOf(Notation.pieceName(Position.KING * victimSide)) else emptyList()) + targets.map { Notation.pieceName(it) }
        val a = Notation.pieceName(attacker); val c = Notation.show(pos, reply); val x = names[0]; val y = names[1]
        return t("Đối phương $a ($c) bắt đôi $x và $y của bạn.", "The opponent's $a ($c) forks your $x and $y.", "对方${a}（${c}）同时攻击你的${x}和${y}。", "相手の${a}（${c}）があなたの${x}と${y}をフォーク。")
    }

    private fun defendedBySide(pos: Position, move: Int, s: Int): Boolean {
        pos.make(move)
        val d = pos.attackers(Moves.to(move), s).isNotEmpty()
        pos.unmake()
        return d
    }

    /** Quân của bên [s] đang bị treo (bị tấn công mà không đủ bảo vệ). Trả về ô. */
    fun hanging(pos: Position, s: Int): List<Int> {
        val out = ArrayList<Int>()
        for (sq in 0 until 64) {
            val v = pos[sq]
            if (v == 0 || (v > 0) != (s > 0) || abs(v) == Position.KING) continue
            val atk = pos.attackers(sq, -s)
            if (atk.isEmpty()) continue
            val def = pos.attackers(sq, s)
            val cheapest = atk.minOf { Eval.pieceValue(pos[it], it) }
            if (def.isEmpty() || cheapest < Eval.pieceValue(v, sq)) out += sq
        }
        return out
    }

    // ---------------- Gợi ý ----------------

    fun hint(pos: Position, ta: TurnAnalysis?, depth: Int = 5, timeMs: Long = 1500): Hint? {
        val r = search.think(pos, depth, timeMs)
        val m = if (r.move != 0) r.move else ta?.best ?: return null
        val code = Notation.show(pos, m)
        val spoken = Notation.spoken(pos, m)
        val piece = Notation.pieceName(pos[Moves.from(m)])
        val reason = goodReason(pos, m)
        val facts = describeMove(pos, m).drop(1)
        val pv = line(pos, m, 4)
        val body = buildString {
            if (Search.isMate(r.score) && r.score > 0) Search.mateIn(r.score).let { append(t("Có đòn chiếu hết sau $it nước! ", "There's a mate in $it! ", "有${it}步杀！", "${it}手メイトがあります！")) }
            append(spoken).append(t(": ", ": ", "：", "：")).append(facts.joinToString(t("; ", "; ", "；", "、"))).append(t(".", ".", "。", "。"))
            if (pv.size >= 2) append(t(" Diễn biến dự kiến: ", " Expected line: ", "预计变化：", " 予想される進行：")).append(pvText(pv)).append(t(".", ".", "。", "。"))
            append(t(" Thế cờ sau đó: ", " Then the position: ", "之后局面：", " その後の形勢：")).append(assess(r.score)).append(t(".", ".", "。", "。"))
        }
        return Hint(m, code, t("Nên đi quân $piece", "Move your $piece", "应该走$piece", "${piece}を動かしましょう"), "$code – ${reason?.replaceFirstChar { it.uppercase() } ?: t("Nước tốt nhất", "Best move", "最佳着法", "最善手")}", body)
    }

    // ---------------- Nước của máy ----------------

    /** Một câu ngắn nói ý đồ nước của máy (trên thế trước khi máy đi). */
    fun describeOpponent(pos: Position, m: Int): String {
        val spoken = Notation.spoken(pos, m)
        val reason = goodReason(pos, m)
        return if (reason != null) t("Máy đi $spoken – $reason.", "Computer plays $spoken – $reason.", "电脑走${spoken}——${reason}。", "コンピュータは${spoken} – ${reason}。") else t("Máy đi $spoken.", "Computer plays $spoken.", "电脑走${spoken}。", "コンピュータは${spoken}。")
    }

    /** Điểm thế (góc nhìn Trắng) để vẽ biểu đồ lợi thế. */
    fun evalRed(pos: Position, depth: Int = 2): Int {
        val s = search.evaluateDeep(pos, depth)
        val red = s * pos.side
        return red.coerceIn(-1500, 1500)
    }

    private fun pick(vararg options: String) = options[random.nextInt(options.size)]

    /** Dùng Stockfish (nếu app đã gắn) cho phân tích đầu lượt. */
    var useExternal: Boolean = true

    /** Điểm mọi nước bằng Stockfish (MultiPV = số nước, độ sâu [EXT_DEPTH]). null nếu không có/lỗi. */
    private fun external(pos: Position, legal: IntArray): TurnAnalysis? {
        val res = Engines.withExternal { e ->
            e.search(pos.toFen(), emptyList(), GoLimit(depth = EXT_DEPTH, movetimeMs = EXT_MS), multiPv = legal.size.coerceAtMost(64))
        } ?: return null
        val map = HashMap<Int, Int>(legal.size * 2)
        for (l in res.lines) {
            val m = Moves.fromUci(pos, l.pv.firstOrNull() ?: continue)
            if (m != 0) map[m] = l.score
        }
        if (map.size < legal.size) {
            // Nước Stockfish không chấm (hiếm) → chấm nhanh bằng engine Kotlin
            val miss = legal.filter { it !in map }.toIntArray()
            val sc = search.scoreAll(pos, 2, miss)
            for (i in miss.indices) map[miss[i]] = sc[i]
        }
        val best = map.maxByOrNull { it.value } ?: return null
        return TurnAnalysis(pos.toFen(), best.key, best.value, map)
    }

    companion object {
        const val EXT_DEPTH = 10
        const val EXT_MS = 1500L
        private val CENTER = setOf(27, 28, 35, 36)
        private val WIDE_CENTER = setOf(18, 19, 20, 21, 26, 29, 34, 37, 42, 43, 44, 45)
        const val WARN_LOSS = 220
        /** "Nước đạt": mất ≤ 50 cp so với nước tốt nhất. */
        const val OK_LOSS = 50
    }
}
