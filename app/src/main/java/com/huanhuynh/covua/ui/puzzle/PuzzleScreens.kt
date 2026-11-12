package com.huanhuynh.covua.ui.puzzle

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.ads.BannerAd
import com.huanhuynh.covua.coach.Puzzle
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.data.content.ContentRepository
import com.huanhuynh.covua.data.db.PuzzleMode
import com.huanhuynh.covua.feature.daily.recordPuzzle
import com.huanhuynh.covua.feature.train.PuzzleTrainer
import com.huanhuynh.covua.share.ShareContent
import com.huanhuynh.covua.share.Sharer
import com.huanhuynh.covua.training.PuzzleSelector
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.game.findActivity
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import kotlinx.coroutines.launch

private val DIFF_NAMES get() = listOf("", t("Rất dễ", "Very easy", "很简单", "とてもやさしい"), t("Dễ", "Easy", "简单", "やさしい"), t("Vừa", "Medium", "中等", "ふつう"), t("Khó", "Hard", "难", "難しい"), t("Rất khó", "Very hard", "很难", "とても難しい"))

fun stars(d: Int) = "★".repeat(d.coerceIn(0, 5)) + "☆".repeat((5 - d).coerceIn(0, 5))

@Composable
private fun rememberContent(container: AppContainer): ContentRepository.Content? {
    var c by remember { mutableStateOf(container.content.peek()) }
    LaunchedEffect(Unit) { if (c == null) c = container.content.get() }
    return c
}

/** Danh sách bài tập: nút lớn "Bài vừa sức" + nhóm gập theo chủ đề / độ khó. */
@Composable
fun PuzzleListScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit, onOpen: (Int) -> Unit, onFit: () -> Unit) {
    val content = rememberContent(container)
    val solved by remember { container.player.solvedFlow() }.collectAsStateWithLifecycle(initialValue = settings.puzzlesSolved.mapNotNull { it.toIntOrNull() }.toSet())
    var byTheme by remember { mutableStateOf(true) }
    var open by remember { mutableStateOf(setOf<String>()) }
    val list = content?.puzzles.orEmpty()
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Bài tập · ${solved.size}/${list.size}", "Puzzles · ${solved.size}/${list.size}", "习题 · ${solved.size}/${list.size}", "問題集 · ${solved.size}/${list.size}"), onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Red).clickable(onClick = onFit).padding(16.dp),
                ) {
                    Text(t("🎯 Bài vừa sức", "🎯 Puzzle for you", "🎯 适合你的题", "🎯 あなた向けの問題"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(t("Thầy chọn bài hợp trình độ – giải tốt thì bài khó dần", "Picked for your level – solve well and they get harder", "老师按你的水平选题——解得好就逐渐加难", "レベルに合わせて先生が選びます – 正解が続くと難しくなります"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf(true to t("Theo chủ đề", "By theme", "按主题", "テーマ別"), false to t("Theo độ khó", "By difficulty", "按难度", "難易度別")).forEach { (v, label) ->
                        Text(
                            label, fontSize = 13.sp, color = if (byTheme == v) Color.White else Ink,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (byTheme == v) Ink else Color.White)
                                .border(1.dp, Line, RoundedCornerShape(16.dp)).clickable { byTheme = v; open = emptySet() }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            val groups: List<Pair<String, List<Puzzle>>> = if (byTheme) {
                list.groupBy { it.theme }.entries.sortedByDescending { it.value.size }.map { com.huanhuynh.covua.coach.themeLabel(it.key) to it.value }
            } else {
                list.groupBy { it.difficulty.coerceIn(1, 5) }.toSortedMap().map { (d, l) -> "${DIFF_NAMES[d]} ${stars(d)}" to l }
            }
            groups.forEach { (name, ps) ->
                val expanded = name in open
                item(key = "h_$name") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(14.dp))
                            .clickable { open = if (expanded) open - name else open + name }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        Text("${ps.count { it.id in solved }}/${ps.size}", color = Muted, fontSize = 13.sp)
                        Text(if (expanded) "  ▲" else "  ▼", color = Muted, fontSize = 12.sp)
                    }
                }
                if (expanded) items(ps, key = { "p_${name}_${it.id}" }) { p -> PuzzleRow(p, p.id in solved) { onOpen(p.id) } }
            }
        }
        if (!settings.adsRemoved) BannerAd(container.adsManager, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PuzzleRow(p: Puzzle, solved: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (solved) Good else Color(0xFFF1EADF)), contentAlignment = Alignment.Center) {
            Text(if (solved) "✓" else "${p.id}", color = if (solved) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 13.5.sp)
            Text(p.themeName, fontSize = 12.sp, color = Muted)
        }
        Text(stars(p.difficulty), color = Color(0xFFE0A100), fontSize = 11.sp)
    }
}

/** Quảng cáo xen kẽ chỉ ở chế độ danh sách: cứ N bài một lần, tại điểm chuyển "Bài tiếp". */
private fun maybeInterstitial(container: AppContainer, activity: android.app.Activity?) {
    val every = container.rcInt(RcKeys.PUZZLE_INTERSTITIAL_EVERY)
    if (activity != null && every > 0 && container.puzzlesSolvedSession > 0 && container.puzzlesSolvedSession % every == 0) {
        container.interstitial.onTransition(activity)
    }
}

/** Một bài theo id (từ danh sách). */
@Composable
fun PuzzleScreen(
    container: AppContainer, settings: AppSettings, puzzleId: Int, onBack: () -> Unit, onNext: (Int) -> Unit,
    onPlayFrom: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val content = rememberContent(container) ?: return
    val list = content.puzzles
    val puzzle = content.puzzle(puzzleId) ?: list.firstOrNull() ?: return
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Bài tập ${puzzle.id}", "Puzzle ${puzzle.id}", "习题${puzzle.id}", "問題${puzzle.id}"), onBack)
        PuzzleHeader(puzzle)
        PuzzleTrainer(container, settings, puzzle, onResult = { r -> scope.launch { recordPuzzle(container, puzzle, PuzzleMode.BROWSE, r, source = "list") } }) { r ->
            val next = list.indexOfFirst { it.id == puzzle.id } + 1
            if (next in list.indices) PillButton(t("Bài tiếp ▶", "Next puzzle ▶", "下一题 ▶", "次の問題 ▶")) { maybeInterstitial(container, context.findActivity()); onNext(list[next].id) }
            if (r.success) PillButton(t("📤 Thách bạn bè", "📤 Challenge friends", "📤 挑战好友", "📤 友達に挑戦状"), primary = false) { sharePuzzle(context, puzzle, settings) }
            if (onPlayFrom != null && container.flag(RcKeys.PLAY_FROM_POSITION_ENABLED)) PillButton(t("♟ Chơi tiếp với máy", "♟ Play on vs computer", "♟ 继续和电脑下", "♟ コンピュータと続ける"), primary = false) { onPlayFrom(puzzle.fen) }
            PillButton(t("Danh sách", "List", "列表", "一覧"), primary = false, onClick = onBack)
        }
    }
    LaunchedEffect(Unit) { container.rewarded.preload() }
}

/** "Bài vừa sức": thầy chọn bài chưa giải ở bậc hiện tại, ưu tiên chủ đề ít gặp gần đây. */
@Composable
fun FitPuzzleScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit, onPlayFrom: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val content = rememberContent(container) ?: return
    var current by remember { mutableStateOf<Puzzle?>(null) }
    var band by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) {
        val ps = container.player.get()
        val progress = container.player.progress()
        val solved = progress.filter { it.solved }.map { it.puzzleId }.toSet()
        val recent = container.player.recentAttempts(5).mapNotNull { a -> content.puzzle(a.puzzleId)?.theme }.reversed()
        band = ps.puzzleBand
        val meta = PuzzleSelector(content.metas).next(ps.puzzleBand, solved, recent, exclude = setOfNotNull(current?.id),
            lastSolvedAt = progress.associate { it.puzzleId to (it.lastAttemptAt ?: 0L) })
        current = meta?.let { content.puzzle(it.id) }
    }
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Bài vừa sức", "Puzzle for you", "适合你的题", "あなた向けの問題") + if (band > 0) t(" · bậc $band/5", " · level $band/5", " · 等级$band/5", " · 段階${band}/5") else "", onBack)
        val p = current
        if (p == null) Text(t("Thầy đang chọn bài…", "Choosing a puzzle…", "正在选题…", "問題を選んでいます…"), color = Muted, modifier = Modifier.padding(16.dp)) else {
            PuzzleHeader(p)
            PuzzleTrainer(container, settings, p, onResult = { r -> scope.launch { recordPuzzle(container, p, PuzzleMode.BROWSE, r, source = "fit") } }) { r ->
                PillButton(t("Bài tiếp ▶", "Next puzzle ▶", "下一题 ▶", "次の問題 ▶")) { maybeInterstitial(container, context.findActivity()); round++ }
                if (r.success) PillButton(t("📤 Thách bạn bè", "📤 Challenge friends", "📤 挑战好友", "📤 友達に挑戦状"), primary = false) { sharePuzzle(context, p, settings) }
                if (onPlayFrom != null && container.flag(RcKeys.PLAY_FROM_POSITION_ENABLED)) PillButton(t("♟ Chơi tiếp với máy", "♟ Play on vs computer", "♟ 继续和电脑下", "♟ コンピュータと続ける"), primary = false) { onPlayFrom(p.fen) }
            }
        }
    }
    LaunchedEffect(Unit) { container.rewarded.preload() }
}

@Composable
private fun PuzzleHeader(p: Puzzle) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
        Text(t("Chủ đề: ", "Theme: ", "主题：", "テーマ：") + "${p.themeName} · " + stars(p.difficulty), fontSize = 12.sp, color = Muted)
    }
}

private fun sharePuzzle(context: android.content.Context, puzzle: Puzzle, settings: AppSettings) {
    val start = com.huanhuynh.covua.engine.Position.fromFen(puzzle.fen)
    Sharer.share(
        context,
        ShareContent(
            moment = "puzzle",
            title = puzzle.title + t(". Bạn giải được không?", ". Can you solve it?", "。你能解出来吗？", "。解けますか？"),
            subtitle = t("Bài tập", "Puzzle", "习题", "問題") + " ${puzzle.id} · ${puzzle.themeName} · " + stars(puzzle.difficulty),
            text = t("${puzzle.title} – bạn tìm ra nước đầu không? Mình vừa giải xong trong Cờ Vua Có Thầy.", "${puzzle.title} – can you find the first move? I just solved it in Chess Coach.", "${puzzle.title}——你能找到第一步吗？我刚在「国际象棋老师」里解出来了。", "${puzzle.title} – 初手がわかりますか？「チェス先生」で解きました。"),
            board = start.board.copyOf(),
            flipped = puzzle.side < 0,
        ),
        settings.pieceStyle,
    )
}
