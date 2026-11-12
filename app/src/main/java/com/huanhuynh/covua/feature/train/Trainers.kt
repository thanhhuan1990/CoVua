package com.huanhuynh.covua.feature.train

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.coach.Puzzle
import com.huanhuynh.covua.coach.PuzzleSession
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.data.db.ReviewCardEntity
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.feature.review.ReviewRepository
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.Tag
import com.huanhuynh.covua.ui.TeacherAvatar
import com.huanhuynh.covua.ui.board.BoardView
import com.huanhuynh.covua.ui.game.Arrow
import com.huanhuynh.covua.ui.game.ArrowKind
import com.huanhuynh.covua.ui.game.findActivity
import com.huanhuynh.covua.ui.theme.Bad
import com.huanhuynh.covua.ui.theme.Best
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Red
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Kết quả một bài tập. [failed] = bấm Làm lại hoặc phải xem lời giải (gợi ý mức 3). */
data class PuzzleResult(val success: Boolean, val hintLevel: Int, val livesLeft: Int?, val failed: Boolean, val timeMs: Long)

/**
 * Cổng quảng cáo có thưởng cho gợi ý (luôn tự nguyện, có nút "Không, cảm ơn").
 * Dùng chung bộ đếm "hint_free_between_ads" với ván cờ.
 */
@Composable
fun rememberHintGate(container: AppContainer, settings: AppSettings): HintGate {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val gate = remember { HintGate() }
    gate.request = { action ->
        val free = FirebaseHelper.getLong(context, RcKeys.HINT_FREE_BETWEEN_ADS).toInt()
        val used = container.game.hintsSinceAd
        if (settings.adsRemoved || free <= 0 || used < free || !container.rewarded.isReady) {
            if (!container.rewarded.isReady) container.rewarded.preload()
            container.game.countSharedHint(); action()
        } else pending = action
    }
    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(t("Mở gợi ý bằng quảng cáo ngắn", "Unlock a hint with a short ad", "看短广告解锁提示", "短い広告でヒントを開く")) },
            text = { Text(t("Bạn đã dùng hết lượt gợi ý miễn phí. Xem một quảng cáo ngắn để mở gợi ý này. Lời giải thích của thầy luôn miễn phí.", "You've used your free hints. Watch a short ad to unlock this one. Your coach's explanations are always free.", "免费提示已用完。看一段短广告解锁这个提示。老师的讲解永远免费。", "無料ヒントを使い切りました。短い広告を見るとこのヒントが開きます。先生の解説はいつでも無料です。")) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    val a = context.findActivity() ?: return@TextButton
                    if (!container.rewarded.show(a, onReward = { container.game.resetHintAdCounter(); action() })) action()
                }) { Text(t("Xem quảng cáo", "Watch ad", "看广告", "広告を見る"), color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(t("Không, cảm ơn", "No, thanks", "不用了，谢谢", "いいえ")) } },
        )
    }
    return gate
}

class HintGate { var request: (() -> Unit) -> Unit = { it() } }

/** Khung lời thầy dưới bàn cờ. */
@Composable
fun CoachBox(title: String, body: String, color: Color? = null, buttons: @Composable () -> Unit) {
    Column(
        Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(20.dp)).padding(14.dp).heightIn(min = 110.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            TeacherAvatar()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (color != null) Tag(title, color) else Text(title, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text(body, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    }
}

/**
 * Bàn giải bài tập dùng chung: danh sách, Bài vừa sức, Buổi tập (có "mạng").
 * [lives] = null → không giới hạn; hết mạng → hiện lời giải, vẫn tính là đã làm.
 */
@Composable
fun ColumnScope.PuzzleTrainer(
    container: AppContainer,
    settings: AppSettings,
    puzzle: Puzzle,
    lives: Int? = null,
    onResult: (PuzzleResult) -> Unit,
    doneButtons: @Composable (PuzzleResult) -> Unit,
) {
    var resetKey by remember(puzzle.id) { mutableIntStateOf(0) }
    val session = remember(puzzle.id, resetKey) { PuzzleSession(puzzle) }
    val scope = rememberCoroutineScope()
    val gate = rememberHintGate(container, settings)
    var version by remember(puzzle.id, resetKey) { mutableIntStateOf(0) }
    var selected by remember(puzzle.id, resetKey) { mutableIntStateOf(-1) }
    var message by remember(puzzle.id, resetKey) { mutableStateOf<Pair<String, String>?>(null) }
    var msgColor by remember(puzzle.id, resetKey) { mutableStateOf(Best) }
    var hintLevel by remember(puzzle.id) { mutableIntStateOf(0) }
    var hintArrow by remember(puzzle.id, resetKey) { mutableStateOf<Arrow?>(null) }
    var busy by remember(puzzle.id, resetKey) { mutableStateOf(false) }
    var lastMove by remember(puzzle.id, resetKey) { mutableIntStateOf(0) }
    var livesLeft by remember(puzzle.id) { mutableStateOf(lives) }
    var failed by remember(puzzle.id) { mutableStateOf(false) }
    var result by remember(puzzle.id) { mutableStateOf<PuzzleResult?>(null) }
    val startedAt by remember(puzzle.id) { mutableLongStateOf(System.currentTimeMillis()) }

    val legal = remember(version, resetKey) { session.pos.legalMoves() }
    val targets = if (selected >= 0) legal.filter { Moves.from(it) == selected }.map { Moves.to(it) }.toSet() else emptySet()

    fun finish(success: Boolean) {
        val r = PuzzleResult(success, hintLevel, livesLeft, failed || hintLevel >= 3 || !success, System.currentTimeMillis() - startedAt)
        result = r
        onResult(r)
    }

    fun showHint(level: Int) {
        scope.launch {
            busy = true
            val (text, m) = withContext(Dispatchers.Default) { session.hintText(level) to session.nextSolutionMove() }
            busy = false
            hintLevel = maxOf(hintLevel, level)
            msgColor = Best
            message = t("Gợi ý $level/3", "Hint $level/3", "提示$level/3", "ヒント ${level}/3") to text
            if (level >= 3 && m != 0) hintArrow = Arrow(Moves.from(m), Moves.to(m), ArrowKind.HINT)
            if (level == 1 && m != 0) selected = Moves.from(m)
        }
    }

    fun tap(sq: Int) {
        if (busy || session.solved || result != null) return
        val p = session.pos[sq]
        if (selected >= 0 && sq in targets) {
            // Phong cấp: thử lần lượt Hậu, Mã, Xe, Tượng – lấy quân nào giữ được chiếu hết
            val cands = legal.filter { Moves.from(it) == selected && Moves.to(it) == sq }
                .sortedBy { listOf(Position.QUEEN, Position.KNIGHT, Position.ROOK, Position.BISHOP, 0).indexOf(Moves.promo(it)).let { i -> if (i < 0) 9 else i } }
            selected = -1
            busy = true
            scope.launch {
                val capturedNow = session.pos[sq] != 0
                var m = cands.firstOrNull() ?: 0
                val ok = withContext(Dispatchers.Default) {
                    var good = false
                    for (c in cands) { if (session.tryMove(c)) { m = c; good = true; break } }
                    good
                }
                if (!ok) {
                    busy = false
                    container.sounds.play(com.huanhuynh.covua.Sfx.WRONG)
                    msgColor = Bad
                    val l = livesLeft
                    if (l != null) {
                        livesLeft = l - 1
                        if (l - 1 <= 0) {
                            // Hết mạng: hiện lời giải, vẫn tính là đã làm bài
                            val sol = withContext(Dispatchers.Default) { session.nextSolutionMove() }
                            if (sol != 0) hintArrow = Arrow(Moves.from(sol), Moves.to(sol), ArrowKind.HINT)
                            message = t("Hết lượt thử", "Out of tries", "机会用完了", "挑戦回数切れ") to run { val sol = puzzle.solutionShown(); t("Không sao – thầy chỉ đường giải: $sol. Mai mình làm bài mới nhé.", "No worries – here's the solution: $sol. New puzzle tomorrow.", "没关系——答案是：${sol}。明天做新题。", "大丈夫 – 正解はこちら：${sol}。明日また新しい問題をやりましょう。") }
                            finish(false)
                            return@launch
                        }
                    }
                    message = t("Chưa đúng", "Not quite", "不对", "惜しい") to (t("Với nước này đối phương vẫn thoát được.", "After this move the opponent can still escape.", "这步之后对方仍能逃脱。", "この手では相手がまだ逃げられます。") + (livesLeft?.let { t(" Còn $it lượt thử.", " $it tries left.", "还剩${it}次机会。", " 残り${it}回。") } ?: t(" Thử lại nhé – nước nào khiến Vua đối phương hết đường?", " Try again – which move leaves the enemy King no escape?", "再试试——哪一步能让对方的王无路可走？", " もう一度 – どの手で相手のキングの逃げ道がなくなる？")))
                    return@launch
                }
                lastMove = m; hintArrow = null; version++
                if (session.solved) {
                    container.sounds.play(com.huanhuynh.covua.Sfx.CORRECT)
                    busy = false
                    msgColor = Good
                    message = t("Chính xác! Chiếu hết.", "Correct! Checkmate.", "正确！将死。", "正解！チェックメイト。") to t("Bạn đã giải xong bài ${puzzle.id} (${puzzle.themeName}).", "You solved puzzle ${puzzle.id} (${puzzle.themeName}).", "你解出了第${puzzle.id}题（${puzzle.themeName}）。", "問題${puzzle.id}（${puzzle.themeName}）を解きました。")
                    container.puzzlesSolvedSession++
                    finish(true)
                    return@launch
                }
                container.sounds.move(captured = capturedNow, check = session.pos.inCheck())
                msgColor = Good
                message = t("Đúng hướng!", "On the right track!", "方向正确！", "いい方向です！") to t("Đối phương đang chống đỡ…", "The opponent is defending…", "对方正在防守…", "相手が受けています…")
                delay(500)
                val before = session.pos.board.copyOf()
                val r = withContext(Dispatchers.Default) { session.opponentReply() }
                container.sounds.move(captured = r != 0 && before[Moves.to(r)] != 0, check = session.pos.inCheck())
                lastMove = r; version++
                busy = false
                message = t("Tiếp tục", "Continue", "继续", "続ける") to t("Đối phương đã đi. Còn ${session.movesLeft} nước để chiếu hết.", "The opponent has moved. ${session.movesLeft} moves left to mate.", "对方已走。还剩${session.movesLeft}步将死。", "相手が指しました。メイトまであと${session.movesLeft}手。")
            }
            return
        }
        selected = if (p != 0 && (p > 0) == (puzzle.side > 0)) (if (selected == sq) -1 else sq) else -1
    }

    Box(Modifier.padding(8.dp)) {
        BoardView(
            board = session.pos.board.copyOf(), flipped = puzzle.side < 0, style = settings.pieceStyle, selected = selected, targets = targets,
            lastMove = lastMove, version = version, arrows = listOfNotNull(hintArrow),
            checkSquare = if (session.pos.inCheck()) session.pos.kingSquare(session.pos.side) else -1,
            onTap = ::tap,
        )
    }
    livesLeft?.let { l -> Text(t("Lượt thử: ", "Tries: ", "机会：", "挑戦回数：") + "♥".repeat(l.coerceAtLeast(0)) + "♡".repeat(((lives ?: 0) - l).coerceAtLeast(0)), color = Red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    Spacer(Modifier.weight(1f))
    val (t, b) = message ?: (puzzle.title to if (puzzle.side > 0) t("Tìm nước chiếu hết. Chạm quân Trắng rồi chạm ô đích.", "Find the mate. Tap a White piece, then the target square.", "找出杀棋。先点白棋，再点目标格。", "メイトを見つけよう。白の駒をタップしてから行き先をタップ。")
        else t("Tìm nước chiếu hết. Chạm quân Đen rồi chạm ô đích.", "Find the mate. Tap a Black piece, then the target square.", "找出杀棋。先点黑棋，再点目标格。", "メイトを見つけよう。黒の駒をタップしてから行き先をタップ。"))
    CoachBox(t, b, if (message != null) msgColor else null) {
        val r = result
        if (r != null) doneButtons(r) else {
            when (hintLevel) {
                0 -> PillButton(t("💡 Gợi ý", "💡 Hint", "💡 提示", "💡 ヒント"), primary = false, enabled = !busy) { showHint(1) }
                1, 2 -> PillButton(t("Gợi ý tiếp", "Next hint", "下一个提示", "次のヒント"), primary = false, enabled = !busy) { gate.request { showHint(hintLevel + 1) } }
                else -> {}
            }
            if (session.played.isNotEmpty()) PillButton(t("Làm lại", "Retry", "重来", "やり直す"), primary = false, enabled = !busy) { failed = true; resetKey++ }
        }
    }
}

/**
 * Ôn một thẻ Sổ tay: hiện thế trước nước sai, hỏi "Lần này đi gì?". Chỉ một nước; chấm bằng best_moves hoặc kiểm nhanh.
 */
@Composable
fun ColumnScope.ReviewTrainer(
    container: AppContainer,
    settings: AppSettings,
    card: ReviewCardEntity,
    onAnswered: (ReviewCardEntity, Boolean) -> Unit,
    doneButtons: @Composable (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gate = rememberHintGate(container, settings)
    val pos = remember(card.id) { Position.fromFen(card.fen) }
    val side = pos.side
    val flipped = settings.flipWhenBlack && side < 0
    var selected by remember(card.id) { mutableIntStateOf(-1) }
    var answered by remember(card.id) { mutableStateOf<Boolean?>(null) }
    var hintLevel by remember(card.id) { mutableIntStateOf(0) }
    var arrows by remember(card.id) { mutableStateOf<List<Arrow>>(emptyList()) }
    var board by remember(card.id) { mutableStateOf(pos.board.copyOf()) }
    var lastMove by remember(card.id) { mutableIntStateOf(0) }
    var busy by remember(card.id) { mutableStateOf(false) }
    var msg by remember(card.id) { mutableStateOf<Triple<String, String, Color?>?>(null) }
    val legal = remember(card.id) { pos.legalMoves() }
    val targets = if (selected >= 0) legal.filter { Moves.from(it) == selected }.map { Moves.to(it) }.toSet() else emptySet()
    val best = card.bestMoveList().firstOrNull() ?: 0

    val date = remember(card.id) {
        java.time.Instant.ofEpochMilli(card.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))
    }
    val playedCode = remember(card.id) { runCatching { Notation.show(pos, card.playedMove) }.getOrDefault("?") }
    val prompt = t("Ván ngày $date" + (if (card.level > 0) " (cấp ${card.level})" else "") + " bạn đi $playedCode ở đây. Lần này đi gì?", "In your game on $date" + (if (card.level > 0) " (level ${card.level})" else "") + " you played $playedCode here. What now?", "$date 的对局" + (if (card.level > 0) "（${card.level}级）" else "") + "你在这里走了${playedCode}。这次走什么？", "${date}の対局" + (if (card.level > 0) "（レベル${card.level}）" else "") + "で、あなたはここで${playedCode}と指しました。今回はどうしますか？")

    fun answer(m: Int) {
        busy = true
        scope.launch {
            val g = container.reviews.grade(card, m)
            val ok = g is ReviewRepository.Grade.Correct
            container.sounds.play(if (ok) com.huanhuynh.covua.Sfx.CORRECT else com.huanhuynh.covua.Sfx.WRONG)
            val code = Notation.show(pos, m)
            board = Position.fromFen(card.fen).let { p2 -> p2.make(m); p2.board.copyOf() }
            lastMove = m
            val bestCode = if (best != 0) Notation.show(pos, best) else ""
            if (ok) {
                val same = m == best || bestCode.isEmpty()
                msg = Triple(t("Đúng rồi!", "Correct!", "答对了！", "正解！"), (if (same) "" else t("(nước của thầy cũng là $bestCode) ", "(the coach's move is also $bestCode) ", "（老师的着法也是${bestCode}）", "（先生の手も${bestCode}です）")) + t("Lần trước bạn đi $playedCode – giờ đã sửa được. Thầy ghi vào sổ.", "Last time you played $playedCode – now you've fixed it. Noted.", "上次你走了${playedCode}——现在改对了，老师记下了。", "前回は${playedCode}でしたが、今回は直せました。記録しました。"), Good)
            } else {
                // Mũi tên đòn đáp trả + lời thầy cũ (hết thì sinh lại bằng Coach)
                val reply = container.reviews.refutation(card, m)
                arrows = listOfNotNull(
                    reply.takeIf { it != 0 }?.let { Arrow(Moves.from(it), Moves.to(it), ArrowKind.THREAT) },
                    best.takeIf { it != 0 }?.let { Arrow(Moves.from(it), Moves.to(it), ArrowKind.BETTER) },
                )
                val old = if (m == card.playedMove) (container.reviews.oldCoachText(card) ?: container.reviews.regenerateCoachText(card)) else null
                msg = Triple(t("Chưa đúng", "Not quite", "不对", "惜しい"), (old?.let { "$it " } ?: "") + t("Nước nên đi là $bestCode (mũi tên xanh). Thầy để thế này ôn lại sớm nhé.", "The move to play is $bestCode (green arrow). This one will come back soon.", "应走${bestCode}（绿色箭头）。这个局面会很快再复习。", "指すべき手は${bestCode}（緑の矢印）。この局面はまた近いうちに復習しましょう。"), Bad)
            }
            val next = container.reviews.answer(card, ok, hintLevel)
            answered = ok
            busy = false
            onAnswered(next, ok)
        }
    }

    fun tap(sq: Int) {
        if (busy || answered != null) return
        val p = pos[sq]
        if (selected >= 0 && sq in targets) {
            // Phong cấp trong Sổ tay: mặc định phong Hậu
            val cands = legal.filter { Moves.from(it) == selected && Moves.to(it) == sq }
            val m = cands.firstOrNull { Moves.promo(it) == Position.QUEEN } ?: cands.firstOrNull() ?: Moves.encode(selected, sq)
            selected = -1; answer(m); return
        }
        selected = if (p != 0 && (p > 0) == (side > 0)) (if (selected == sq) -1 else sq) else -1
    }

    Text(prompt, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Box(Modifier.padding(8.dp)) {
        BoardView(
            board = board, flipped = flipped, style = settings.pieceStyle, selected = selected, targets = targets,
            lastMove = lastMove, arrows = arrows, version = if (answered == null) 0 else 1, onTap = ::tap,
        )
    }
    Spacer(Modifier.weight(1f))
    val m = msg
    CoachBox(m?.first ?: t("Sổ tay của thầy", "Coach's notebook", "老师的错题本", "先生のミスノート"), m?.second ?: if (side > 0) t("Chạm quân Trắng rồi chạm ô đích.", "Tap a White piece, then the target square.", "先点白棋，再点目标格。", "白の駒をタップしてから行き先をタップ。") else t("Chạm quân Đen rồi chạm ô đích.", "Tap a Black piece, then the target square.", "先点黑棋，再点目标格。", "黒の駒をタップしてから行き先をタップ。"), m?.third) {
        val a = answered
        if (a != null) doneButtons(a) else {
            when (hintLevel) {
                0 -> PillButton(t("💡 Gợi ý", "💡 Hint", "💡 提示", "💡 ヒント"), primary = false, enabled = !busy && best != 0) {
                    hintLevel = 1
                    selected = Moves.from(best)
                    msg = Triple(t("Gợi ý 1/2", "Hint 1/2", "提示1/2", "ヒント 1/2"), t("Quân nên đi là ${Notation.pieceName(pos[Moves.from(best)])}.", "The piece to move is the ${Notation.pieceName(pos[Moves.from(best)])}.", "应该走的棋子是${Notation.pieceName(pos[Moves.from(best)])}。", "動かす駒は${Notation.pieceName(pos[Moves.from(best)])}です。"), Best)
                }
                1 -> PillButton(t("Gợi ý tiếp", "Next hint", "下一个提示", "次のヒント"), primary = false, enabled = !busy) {
                    gate.request {
                        hintLevel = 2
                        arrows = listOf(Arrow(Moves.from(best), Moves.to(best), ArrowKind.HINT))
                        msg = Triple(t("Gợi ý 2/2", "Hint 2/2", "提示2/2", "ヒント 2/2"), t("Nước nên đi: ${Notation.spoken(pos, best)}. Đi theo để ghi nhớ nhé.", "The move: ${Notation.spoken(pos, best)}. Play it to remember.", "应走：${Notation.spoken(pos, best)}。走一遍加深记忆。", "指す手：${Notation.spoken(pos, best)}。覚えるために指してみましょう。"), Best)
                    }
                }
                else -> {}
            }
        }
    }
}

fun todayLabel(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"))
