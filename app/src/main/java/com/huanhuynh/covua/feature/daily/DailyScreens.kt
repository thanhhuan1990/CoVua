package com.huanhuynh.covua.feature.daily

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.coach.Puzzle
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.data.db.DailyActivityEntity
import com.huanhuynh.covua.data.db.PuzzleMode
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.feature.review.ReviewSession
import com.huanhuynh.covua.feature.train.PuzzleResult
import com.huanhuynh.covua.feature.train.PuzzleTrainer
import com.huanhuynh.covua.share.ShareContent
import com.huanhuynh.covua.share.Sharer
import com.huanhuynh.covua.training.BandTracker
import com.huanhuynh.covua.training.DailyPlan
import com.huanhuynh.covua.training.DailyPlanner
import com.huanhuynh.covua.training.SessionParts
import com.huanhuynh.covua.training.StreakCalculator
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.TeacherAvatar
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.game.findActivity
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Ghi kết quả bài tập + band + lịch tập (dùng chung cho mọi nơi giải bài). */
suspend fun recordPuzzle(c: AppContainer, p: Puzzle, mode: String, r: PuzzleResult, daily: Boolean = false, source: String = "list"): Int? {
    val tracker = BandTracker(c.rcInt(RcKeys.PUZZLE_BAND_UP_AFTER), c.rcInt(RcKeys.PUZZLE_BAND_DOWN_AFTER))
    val st = runCatching { c.player.recordAttempt(p.id, mode, r.success, r.hintLevel, r.livesLeft, r.timeMs, r.failed, tracker) }.getOrNull()
    c.events.log(Ev.PUZZLE_ATTEMPT, mapOf(
        "id" to p.id.toString(), "band" to (st?.puzzleBand ?: 0).toString(), "theme" to p.theme, "ok" to r.success.toString(),
        "hint_level" to r.hintLevel.toString(), "source" to source,
    ))
    if (r.success) {
        c.settings.markPuzzleSolved(p.id) // giữ DataStore cho bản cũ / dự phòng
        c.events.log("puzzle_solved", mapOf("id" to p.id.toString()))
    }
    return runCatching { c.activity.recordPuzzle(daily, r.livesLeft) }.getOrNull()
}

private enum class Step { LOADING, REVIEW, DAILY, EXTRA, DONE, UNAVAILABLE }

/** Buổi tập hôm nay: ① ôn ≤ 3 thẻ ② bài của thầy (5 lượt thử) ③ 2 bài vừa sức. Xong ② là ngày được tính. */
@Composable
fun DailySessionScreen(container: AppContainer, settings: AppSettings, source: String, onHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<DailyPlan?>(null) }
    var parts by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf(Step.LOADING) }
    var extraIndex by remember { mutableIntStateOf(0) }
    var dailyResult by remember { mutableStateOf<PuzzleResult?>(null) }
    var reviewed by remember { mutableIntStateOf(0) }
    var reviewedOk by remember { mutableIntStateOf(0) }
    var extrasSolved by remember { mutableIntStateOf(0) }
    var milestone by remember { mutableStateOf<Int?>(null) }
    val content = container.content.peek()

    fun nextStep(p: DailyPlan, mask: Int): Step = when {
        mask and SessionParts.REVIEW == 0 && p.hasReview -> Step.REVIEW
        mask and SessionParts.DAILY == 0 -> Step.DAILY
        mask and SessionParts.EXTRA == 0 && p.extraPuzzleIds.isNotEmpty() -> Step.EXTRA
        else -> Step.DONE
    }

    LaunchedEffect(Unit) {
        val p = container.daily.today()
        if (p == null) { step = Step.UNAVAILABLE; return@LaunchedEffect }
        plan = p
        parts = container.daily.partsDone()
        step = nextStep(p, parts)
        container.events.log(Ev.DAILY_SESSION_START, mapOf("source" to source, "parts_done" to parts.toString()))
    }

    fun complete(mask: Int, name: String) {
        val p = plan ?: return
        scope.launch {
            val before = parts
            parts = container.activity.markSessionPart(mask).sessionPartsDone
            container.events.log(Ev.DAILY_STEP_DONE, mapOf("step" to name))
            if (!SessionParts.isComplete(before, p) && SessionParts.isComplete(parts, p)) {
                container.events.log(Ev.DAILY_SESSION_DONE, mapOf("parts" to SessionParts.count(parts, p).toString(), "lives_left" to (dailyResult?.livesLeft ?: -1).toString()))
            }
            step = nextStep(p, parts)
        }
    }

    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Buổi tập hôm nay", "Today's practice", "今日练习", "今日の練習"), onHome)
        val p = plan
        if (p != null && step != Step.DONE) StepHeader(p, parts, step)
        when (step) {
            Step.LOADING -> Text(t("Thầy đang soạn bài…", "Preparing your lesson…", "老师正在备课…", "先生が準備中…"), color = Muted, modifier = Modifier.padding(16.dp))
            // Không để màn cụt: luôn có nút quay về
            Step.UNAVAILABLE -> Column(Modifier.padding(16.dp)) {
                Text(t("Chưa tải được bài hôm nay. Thử lại sau nhé.", "Couldn't load today's lesson. Please try again later.", "今日练习加载失败，请稍后再试。", "今日の練習を読み込めませんでした。後でもう一度お試しください。"), color = Muted)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                PillButton(t("Về trang chủ", "Back to home", "返回首页", "ホームに戻る"), onClick = onHome)
            }
            Step.REVIEW -> ReviewSession(container, settings, p!!.reviewCardIds, onFinished = { n, ok -> reviewed += n; reviewedOk += ok; complete(SessionParts.REVIEW, "review") }, finishLabel = t("Sang bài hôm nay ▶", "On to today's puzzle ▶", "进入今日题 ▶", "今日の問題へ ▶"))
            Step.DAILY -> {
                val puzzle = content?.puzzle(p!!.dailyPuzzleId)
                if (puzzle == null) Text(t("Không tìm thấy bài.", "Puzzle not found.", "找不到题目。", "問題が見つかりません。"), color = Muted, modifier = Modifier.padding(16.dp)) else {
                    Text(t("Bài ${DailyPlanner.weekdayName(p.day).lowercase().replaceFirstChar { it.uppercase() }} · ${puzzle.title} · chủ đề ${puzzle.themeName}", "${DailyPlanner.weekdayName(p.day)} puzzle · ${puzzle.title} · ${puzzle.themeName}", "${DailyPlanner.weekdayName(p.day)}题 · ${puzzle.title} · ${puzzle.themeName}", "${DailyPlanner.weekdayName(p.day)}の問題 · ${puzzle.title} · ${puzzle.themeName}"), fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 16.dp))
                    PuzzleTrainer(container, settings, puzzle, lives = container.rcInt(RcKeys.DAILY_LIVES).coerceIn(1, 9), onResult = { r ->
                        dailyResult = r
                        scope.launch { milestone = recordPuzzle(container, puzzle, PuzzleMode.DAILY, r, daily = true, source = "daily") ?: milestone }
                    }) { _ -> PillButton(t("Tiếp ▶", "Next ▶", "继续 ▶", "次へ ▶")) { complete(SessionParts.DAILY, "daily") } }
                }
            }
            Step.EXTRA -> {
                val id = p!!.extraPuzzleIds.getOrNull(extraIndex)
                val puzzle = id?.let { content?.puzzle(it) }
                if (puzzle == null) LaunchedEffect(Unit) { complete(SessionParts.EXTRA, "extra") } else {
                    Text(t("Bài vừa sức ${extraIndex + 1}/${p.extraPuzzleIds.size} · ${puzzle.title}", "Puzzle for you ${extraIndex + 1}/${p.extraPuzzleIds.size} · ${puzzle.title}", "适合你的题 ${extraIndex + 1}/${p.extraPuzzleIds.size} · ${puzzle.title}", "あなた向けの問題 ${extraIndex + 1}/${p.extraPuzzleIds.size} · ${puzzle.title}"), fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 16.dp))
                    PuzzleTrainer(container, settings, puzzle, onResult = { r ->
                        if (r.success) extrasSolved++
                        scope.launch { recordPuzzle(container, puzzle, PuzzleMode.SESSION, r, source = "session") }
                    }) { _ ->
                        PillButton(if (extraIndex + 1 < p.extraPuzzleIds.size) t("Bài tiếp ▶", "Next puzzle ▶", "下一题 ▶", "次の問題 ▶") else t("Hoàn thành ▶", "Finish ▶", "完成 ▶", "完了 ▶")) {
                            if (extraIndex + 1 < p.extraPuzzleIds.size) extraIndex++ else complete(SessionParts.EXTRA, "extra")
                        }
                    }
                }
            }
            Step.DONE -> DoneView(container, settings, p, parts, dailyResult, reviewed, reviewedOk, extrasSolved, milestone, onHome = {
                // "Về trang chủ" là thao tác rời màn (giống Back) → không hiện quảng cáo.
                onHome()
            })
        }
    }
}

@Composable
private fun StepHeader(p: DailyPlan, parts: Int, step: Step) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple(Step.REVIEW, SessionParts.REVIEW, t("Ôn sổ", "Review", "复习", "復習")), Triple(Step.DAILY, SessionParts.DAILY, t("Bài của thầy", "Today's puzzle", "今日题", "今日の問題")), Triple(Step.EXTRA, SessionParts.EXTRA, t("Bài vừa sức", "Puzzle for you", "适合你的题", "あなた向けの問題")))
            .forEachIndexed { i, (s, mask, label) ->
                val done = parts and mask != 0 || (s == Step.REVIEW && !p.hasReview)
                val now = s == step
                Text(
                    "${i + 1}. $label" + if (done) " ✓" else "", fontSize = 12.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                    color = if (now) Color.White else if (done) Good else Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (now) Red else Color.White)
                        .border(1.dp, if (now) Red else Line, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
                )
            }
    }
}

@Composable
private fun DoneView(
    container: AppContainer, settings: AppSettings, plan: DailyPlan?, parts: Int, daily: PuzzleResult?, reviewed: Int, reviewedOk: Int, extras: Int,
    milestone: Int?, onHome: () -> Unit,
) {
    val context = LocalContext.current
    val full = plan != null && SessionParts.isComplete(parts, plan)
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Ring(if (plan == null) 0 else SessionParts.count(parts, plan), 3, Modifier.size(120.dp))
        val praise = remember { PRAISE.random() }
        Text(if (full) t("Xong buổi tập hôm nay!", "Today's practice done!", "今日练习完成！", "今日の練習完了！") else t("Đã tính ngày tập hôm nay", "Today counts as a practice day", "今天已计入练习", "今日は練習日にカウントされました"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 14.dp))
        Text(
            if (full) praise else t("Thầy ghi vào sổ rồi. Mai mình tập tiếp nhé.", "Noted. See you tomorrow.", "老师记下了，明天继续练。", "記録しました。また明日。"),
            fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(14.dp)) {
            if (reviewed > 0) Text(t("📒 Sổ tay: $reviewedOk/$reviewed thế đúng", "📒 Notebook: $reviewedOk/$reviewed correct", "📒 错题本：答对$reviewedOk/$reviewed", "📒 ミスノート：${reviewed}問中${reviewedOk}問正解"), color = Ink, fontSize = 14.sp)
            daily?.let { Text(if (it.success) t("🧩 Bài hôm nay: giải được, còn ${it.livesLeft ?: 0} mạng", "🧩 Today's puzzle: solved with ${it.livesLeft ?: 0} lives left", "🧩 今日题：已解出，剩${it.livesLeft ?: 0}条命", "🧩 今日の問題：ライフ残り${it.livesLeft ?: 0}で正解") else t("🧩 Bài hôm nay: hết mạng, thầy đã chỉ lời giải", "🧩 Today's puzzle: out of lives, solution shown", "🧩 今日题：机会用完，已显示答案", "🧩 今日の問題：ライフ切れ、正解を表示"), color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text(t("🎯 Bài vừa sức: $extras/${plan?.extraPuzzleIds?.size ?: 2} bài giải được", "🎯 Puzzles for you: $extras/${plan?.extraPuzzleIds?.size ?: 2} solved", "🎯 适合你的题：解出$extras/${plan?.extraPuzzleIds?.size ?: 2}", "🎯 あなた向けの問題：${plan?.extraPuzzleIds?.size ?: 2}問中${extras}問正解"), color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            milestone?.takeIf { !settings.hideStreak }?.let { Text(t("📅 Lịch tập: $it ngày liền!", "📅 Practice streak: $it days!", "📅 连续练习${it}天！", "📅 連続練習：${it}日！"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(t("Về trang chủ", "Back to home", "返回首页", "ホームに戻る"), onClick = onHome)
            val lives = daily?.livesLeft ?: 0
            if (daily?.success == true && lives >= 4) PillButton(t("📤 Khoe", "📤 Share", "📤 分享", "📤 シェア"), primary = false) {
                val pz = plan?.let { container.content.peek()?.puzzle(it.dailyPuzzleId) } ?: return@PillButton
                Sharer.share(
                    context,
                    ShareContent(
                        moment = "daily",
                        title = run { val dd = java.time.LocalDate.ofEpochDay(plan.day).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")); t("Bài ${DailyPlanner.weekdayName(plan.day)} $dd – giải với $lives/5 mạng", "${DailyPlanner.weekdayName(plan.day)} $dd puzzle – solved with $lives/5 lives", "${DailyPlanner.weekdayName(plan.day)} $dd 题——用$lives/5条命解出", "${DailyPlanner.weekdayName(plan.day)} ${dd} の問題 – ライフ${lives}/5で正解") },
                        subtitle = "${pz.title} · ${pz.themeName}",
                        text = t("Bài cờ vua hôm nay (${pz.title}) – mình giải với $lives/5 mạng. Ai cũng cùng một bài trong ngày, bạn thử không?", "Today's chess puzzle (${pz.title}) – I solved it with $lives/5 lives. Everyone gets the same puzzle today, want to try?", "今日国际象棋题（${pz.title}）——我用了$lives/5条命解出。今天大家都是同一道题，你也试试？", "今日のチェス問題（${pz.title}）– ライフ${lives}/5で解きました。今日はみんな同じ問題です。挑戦してみる？"),
                        board = Position.fromFen(pz.fen).board.copyOf(),
                        flipped = pz.side < 0,
                    ),
                    settings.pieceStyle,
                )
                container.events.log(Ev.SHARE, mapOf("moment" to "daily"))
            }
        }
    }
}

private val PRAISE get() = listOf(
    t("Đủ cả ba phần – thầy ghi vào sổ. Đều đặn thế này là tiến bộ nhanh lắm.", "All three parts done – noted. Practising like this, you'll improve fast.", "三部分全部完成——老师记下了。坚持下去进步会很快。", "3つすべて完了、記録しました。この調子なら上達は早いですよ。"),
    t("Buổi tập trọn vẹn! Mai thầy giao bài mới nhé.", "A complete session! New lesson tomorrow.", "练习圆满完成！明天有新题。", "練習コンプリート！明日は新しい課題です。"),
    t("Ôn sổ, giải bài, tập thêm – hôm nay chăm lắm. Thầy ghi vào sổ rồi.", "Reviewed, solved, practised more – great effort today. Noted.", "复习、解题、加练——今天很用功，老师记下了。", "復習、問題、追加練習 – 今日はよく頑張りました。記録しました。"),
)

/** Vòng tròn tiến độ n/total. */
@Composable
fun Ring(n: Int, total: Int, modifier: Modifier = Modifier, stroke: androidx.compose.ui.unit.Dp = 10.dp, textSize: androidx.compose.ui.unit.TextUnit = 26.sp) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize(), color = Color(0xFFEFE7DA), strokeWidth = stroke)
        CircularProgressIndicator(progress = { n.toFloat() / total }, modifier = Modifier.fillMaxSize(), color = if (n >= total) Good else Red, strokeWidth = stroke)
        Text("$n/$total", fontSize = textSize, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

/** Thẻ "Buổi tập hôm nay" ở trang chủ (thay thẻ "Bài của thầy hôm nay"). */
@Composable
fun HomeDailyCard(today: DailyActivityEntity?, dueCards: Int, plan: String?, onOpen: () -> Unit) {
    val hasReview = dueCards > 0
    val mask = today?.sessionPartsDone ?: 0
    val reviewDone = mask and SessionParts.REVIEW != 0
    val dailyDone = mask and SessionParts.DAILY != 0
    val extraDone = mask and SessionParts.EXTRA != 0
    val done = if (mask == 0) 0 else (if (reviewDone || !hasReview) 1 else 0) + (if (dailyDone) 1 else 0) + (if (extraDone) 1 else 0)
    val (title, action) = when {
        mask == 0 -> t("Thầy giao bài · ~8 phút", "Today's lesson · ~8 min", "老师布置 · 约8分钟", "今日の課題 · 約8分") to t("Bắt đầu", "Start", "开始", "始める")
        done < 3 -> t("Tiếp tục – còn ${3 - done} phần", "Continue – ${3 - done} parts left", "继续——还剩${3 - done}部分", "続ける – 残り${3 - done}つ") to t("Tiếp", "Next", "继续", "次へ")
        else -> t("Xong buổi hôm nay ✓", "Today's practice done ✓", "今日练习完成 ✓", "今日の練習完了 ✓") to "Xem"
    }
    Row(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.dp, Color(0xFFF2D9A8), RoundedCornerShape(20.dp)).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(done, 3, Modifier.size(54.dp), stroke = 6.dp, textSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("BUỔI TẬP HÔM NAY", "TODAY'S PRACTICE", "今日练习", "今日の練習"), fontSize = 11.sp, color = Red, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
            Text(if (done >= 3) t("$title – mai thầy giao bài mới", "$title – new lesson tomorrow", "${title}——明天有新题", "${title} – 明日は新しい課題") else title, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (hasReview || reviewDone) StepChip(if (hasReview) t("Ôn ${minOf(dueCards, 3)} thế", "Review ${minOf(dueCards, 3)}", "复习${minOf(dueCards, 3)}个", "復習 ${minOf(dueCards, 3)}問") else t("Ôn sổ", "Review", "复习", "復習"), reviewDone)
                StepChip(plan ?: t("Bài hôm nay", "Today's puzzle", "今日题", "今日の問題"), dailyDone)
                StepChip(t("Vừa sức", "For you", "适合你", "あなた向け"), extraDone)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(action, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (done >= 3) Good else Red).padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun StepChip(text: String, done: Boolean) {
    Text(
        (if (done) "✓ " else "") + text, fontSize = 11.sp, maxLines = 1, color = if (done) Good else Ink,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (done) Color(0xFFE3F1E8) else Color(0xFFF1EADF)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Chip tuần ●◐○ (hiện từ ngày hoạt động thứ 2). */
@Composable
fun WeekChip(dots: List<StreakCalculator.Dot>, onClick: () -> Unit) {
    val names = com.huanhuynh.covua.ui.home.DAY_LETTER
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        dots.forEachIndexed { i, d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (d) { StreakCalculator.Dot.DONE -> "●"; StreakCalculator.Dot.REST -> "◐"; StreakCalculator.Dot.TODAY_PENDING -> "◌"; else -> "○" },
                    color = when (d) { StreakCalculator.Dot.DONE -> Good; StreakCalculator.Dot.TODAY_PENDING -> Red; else -> Muted }, fontSize = 13.sp,
                )
                Text(names[i], fontSize = 9.sp, color = Muted)
            }
        }
        Text("${dots.count { it == StreakCalculator.Dot.DONE }}/7", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Bold)
    }
}

/** Lịch tháng ở màn Tiến bộ: ✓ đã làm bài của thầy, ★ giải với đủ 5 lượt, chấm mờ = ngày nghỉ tự động. */
@Composable
fun MonthCalendar(days: List<DailyActivityEntity>, restDays: Set<Long>, month: YearMonth = YearMonth.now()) {
    val byDay = days.associateBy { it.epochDay }
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val today = LocalDate.now().toEpochDay()
    Column(Modifier.fillMaxWidth()) {
        Text(t("Tháng ${month.monthValue}/${month.year}", "${month.monthValue}/${month.year}", "${month.year}年${month.monthValue}月", "${month.year}年${month.monthValue}月"), fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth()) {
            com.huanhuynh.covua.ui.home.DAY_LETTER.forEach { Text(it, fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        val cells = offset + month.lengthOfMonth()
        for (w in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val idx = w * 7 + d - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 1..month.lengthOfMonth()) {
                            val day = month.atDay(idx).toEpochDay()
                            val a = byDay[day]
                            val mark = when {
                                a?.dailyPuzzleDone == true && (a.dailyLives ?: 0) >= 5 -> "★"
                                a?.dailyPuzzleDone == true -> "✓"
                                a?.counts == true -> "●"
                                day in restDays -> "·"
                                else -> ""
                            }
                            Column(
                                Modifier.fillMaxSize().clip(CircleShape).background(if (day == today) Color(0xFFFFF4E0) else Color.Transparent),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$idx", fontSize = 11.sp, color = if (day > today) Muted.copy(alpha = 0.5f) else Ink)
                                Text(mark, fontSize = 11.sp, color = if (mark == "·") Muted else Good, modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }
        }
        Text(t("✓ làm bài của thầy · ★ giải với đủ 5 lượt · ● có tập · chấm mờ = ngày nghỉ", "✓ daily puzzle · ★ solved with all 5 lives · ● practised · faint dot = rest day", "✓ 完成今日题 · ★ 5条命全保留 · ● 有练习 · 淡点 = 休息日", "✓ 今日の問題 · ★ ライフ5つ全部残して正解 · ● 練習あり · 薄い点 = 休み"), fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
    }
}
