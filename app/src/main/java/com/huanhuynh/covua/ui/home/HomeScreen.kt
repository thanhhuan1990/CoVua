package com.huanhuynh.covua.ui.home

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.huanhuynh.covua.training.StreakCalculator
import com.huanhuynh.covua.ui.theme.Gold
import com.huanhuynh.covua.ui.theme.RedDark
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.engine.Handicap
import com.huanhuynh.covua.feature.daily.HomeDailyCard
import com.huanhuynh.covua.feature.daily.WeekChip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.ads.BannerAd
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.engine.Game
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.TeacherAvatar
import com.huanhuynh.covua.ui.board.BoardView
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import java.util.Calendar

@Composable
fun HomeScreen(
    container: AppContainer,
    settings: AppSettings,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onPuzzles: () -> Unit,
    onDailyPuzzle: () -> Unit,
    onDailySession: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onOpenings: () -> Unit,
) {
    // Đợi chuyển dữ liệu 1.0 → Room xong rồi mới đọc (Room ưu tiên, DataStore dự phòng)
    var ready by remember { mutableStateOf(container.migrated.isCompleted) }
    LaunchedEffect(Unit) { container.migrated.await(); ready = true }
    var roomGame by remember { mutableStateOf<GameEntity?>(null) }
    LaunchedEffect(ready, settings.savedGame) { if (ready) roomGame = runCatching { container.archive.inProgress() }.getOrNull() }
    val saved = remember(settings.savedGame, roomGame) {
        roomGame?.let { g -> runCatching { Game(g.startFen).also { gm -> g.moveList().forEach { gm.play(it) } } }.getOrNull() }
            ?: if (settings.hasSavedGame) Game.deserialize(settings.savedGame) else null
    }
    val savedLevel = roomGame?.level ?: settings.savedLevel
    val savedSide = roomGame?.playerSide ?: settings.savedHumanSide
    val content = container.content.peek()
    var puzzleCount by remember { mutableIntStateOf(content?.puzzles?.size ?: 0) }
    LaunchedEffect(Unit) { puzzleCount = container.content.get().puzzles.size }
    val solved by remember { container.player.solvedFlow() }.collectAsStateWithLifecycle(initialValue = settings.puzzlesSolved.mapNotNull { it.toIntOrNull() }.toSet())
    // "Hôm nay" tính lại mỗi lần vẽ: qua 0 giờ khi đang mở app thì quay lại Trang chủ là thấy buổi tập mới
    val today = container.clock.today()
    val rawSnap by remember { container.activity.flow() }.collectAsStateWithLifecycle(initialValue = null)
    val snap = rawSnap?.let { remember(it, today) { container.activity.snapshot(it.days, today) } }
    val dueCount by remember(today) { container.reviews.dueCountFlow() }.collectAsStateWithLifecycle(initialValue = 0)
    var planText by remember(today) { mutableStateOf<String?>(null) }
    LaunchedEffect(today) {
        planText = container.daily.dailyPuzzleId()?.let { (_, n) -> run { val d = SHORT_DAY[java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1]; t("Bài $d · $n nước", "$d · mate in $n", "$d · ${n}步杀", "${d} · ${n}手メイト") } }
    }
    // Mốc Lịch tập: thầy chúc 1 câu khi mở Trang chủ (mỗi mốc một lần)
    var milestone by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(snap?.state?.current) {
        val cur = snap?.state?.current ?: return@LaunchedEffect
        val seen = container.settings.getInt("streak_celebrated")
        val m = com.huanhuynh.covua.training.StreakCalculator.MILESTONES.lastOrNull { it <= cur }
        if (m != null && m > seen) milestone = m
    }
    val dailyOn = container.v11(RcKeys.DAILY_SESSION_ENABLED)
    val streakOn = container.v11(RcKeys.STREAK_ENABLED) && !settings.hideStreak
    val context = androidx.compose.ui.platform.LocalContext.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greet = when (hour) { in 4..10 -> t("Chào buổi sáng", "Good morning", "早上好", "おはようございます"); in 11..13 -> t("Chào buổi trưa", "Good afternoon", "中午好", "こんにちは"); in 14..17 -> t("Chào buổi chiều", "Good afternoon", "下午好", "こんにちは"); else -> t("Chào buổi tối", "Good evening", "晚上好", "こんばんは") }

    // Thanh trạng thái chữ trắng trên khối đỏ; rời Trang chủ thì trả lại như cũ
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val win = (view.context as? android.app.Activity)?.window
        val ctl = win?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val old = ctl?.isAppearanceLightStatusBars
        ctl?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) ctl.isAppearanceLightStatusBars = old }
    }
    val player by remember { container.player.flow() }.collectAsStateWithLifecycle(initialValue = null)
    val openings = remember { com.huanhuynh.covua.engine.OpeningBook.catalog().size }
    val acc = settings.accuracy7d()
    val titleName = com.huanhuynh.covua.training.Titles.of(maxOf(player?.titleId ?: 0, settings.wonLevels.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0))

    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ---------- Khối xanh mực: lời chào, lịch tuần, ván dở / ván mới ----------
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3B6AA8), RedDark))),
            ) {
                com.huanhuynh.covua.ui.board.PieceIcon(
                    1, Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = 30.dp).size(200.dp), tint = Color.White.copy(alpha = 0.07f),
                )
                Column(Modifier.statusBarsPadding().padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(greet, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Text(t("Cờ Vua Có Thầy", "Chess Coach", "国际象棋老师", "チェス先生"), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onSettings),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Settings, t("Cài đặt", "Settings", "设置", "設定"), tint = Color.White) }
                    }
                    // Lịch tuần: hiện từ ngày hoạt động thứ 2; chạm → Lịch tháng trong Tiến bộ
                    snap?.takeIf { streakOn && it.state.activeDayCount >= 2 }?.let { sn ->
                        HeroWeek(sn.week, sn.weekDone, sn.state.current, Modifier.padding(top = 12.dp, end = 6.dp).clickable(onClick = onProgress))
                    }
                    Row(Modifier.padding(top = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val board = saved?.pos?.board ?: remember { com.huanhuynh.covua.engine.Position.start().board }
                        Box(Modifier.width(128.dp).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).clickable(onClick = if (saved != null) onContinue else onNewGame)) {
                            BoardView(board = board, flipped = saved != null && settings.flipWhenBlack && savedSide < 0, style = settings.pieceStyle, lastMove = saved?.moves?.lastOrNull() ?: 0, showCoords = false)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            if (saved != null) {
                                val lv = Level.of(savedLevel)
                                Text(t("VÁN ĐANG DỞ", "UNFINISHED GAME", "未完成的棋局", "指しかけの対局"), fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Text(t("Cấp ${lv.id} · ${lv.title}", "Level ${lv.id} · ${lv.title}", "${lv.id}级 · ${lv.title}", "レベル${lv.id} · ${lv.title}"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 3.dp))
                                val label = roomGame?.let { g -> Handicap.of(g.handicap).gameLabel.takeIf { it.isNotEmpty() } }
                                Text(
                                    listOfNotNull(t("Nước ${saved.moves.size / 2 + 1}", "Move ${saved.moves.size / 2 + 1}", "第${saved.moves.size / 2 + 1}步", "${saved.moves.size / 2 + 1}手目"), settings.savedAccuracy.takeIf { it >= 0 }?.let { t("chính xác $it%", "$it% accuracy", "准确率$it%", "正確さ${it}%") }, label).joinToString(" · "),
                                    fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f),
                                )
                                GoldButton(t("▶ Chơi tiếp", "▶ Continue", "▶ 继续", "▶ 続ける"), Modifier.padding(top = 12.dp), onContinue)
                                Text(t("Ván mới ›", "New game ›", "新棋局 ›", "新しい対局 ›"), color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    modifier = Modifier.clickable(onClick = onNewGame).padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
                            } else {
                                val lv = Level.of(settings.level)
                                Text(t("VÁN MỚI VỚI MÁY", "NEW GAME VS COMPUTER", "新的人机对局", "コンピュータと新しい対局"), fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Text(t("Cấp ${lv.id} · ${lv.title}", "Level ${lv.id} · ${lv.title}", "${lv.id}级 · ${lv.title}", "レベル${lv.id} · ${lv.title}"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 3.dp))
                                Text(t("Thầy kèm từng nước, cảnh báo trước khi đi", "Coached every move, warned before blunders", "老师逐步点评，走错前提醒", "一手ごとに先生が解説、悪手の前に警告"), fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
                                GoldButton(t("▶ Chơi ngay", "▶ Play now", "▶ 马上下", "▶ 今すぐ指す"), Modifier.padding(top = 12.dp), onNewGame)
                            }
                        }
                    }
                }
            }

            milestone?.takeIf { streakOn }?.let { m ->
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF5EE)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeacherAvatar(28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(t("$m ngày tập liền – thầy ghi vào sổ. Giữ nhịp này nhé!", "$m days in a row – noted! Keep it up!", "连续练习${m}天——老师记下了，保持节奏！", "${m}日連続 – 記録しました！この調子で！"), fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                    Text(t("Khoe", "Share", "分享", "シェア"), color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable {
                        com.huanhuynh.covua.share.Sharer.shareText(context, t("Mình đã tập cờ vua $m ngày liền với \"Cờ Vua Có Thầy\". Tập cùng không?", "I've practised chess $m days in a row with Chess Coach. Join me?", "我已经用「国际象棋老师」连续练习国际象棋${m}天了，一起来吗？", "「チェス先生」で${m}日連続でチェスを練習しています。一緒にどう？"), "streak")
                        container.events.log(com.huanhuynh.covua.domain.Ev.SHARE, mapOf("moment" to "streak"))
                    }.padding(horizontal = 6.dp))
                    Text("✕", color = Muted, modifier = Modifier.clickable {
                        milestone = null
                        container.appScope.launch { container.settings.setInt("streak_celebrated", m) }
                    }.padding(6.dp))
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dailyOn) HomeDailyCard(snap?.todayActivity, dueCount, planText, onDailySession) else Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .border(1.dp, Color(0xFFF2D9A8), RoundedCornerShape(20.dp)).clickable(onClick = onDailyPuzzle).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeacherAvatar(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("BÀI CỦA THẦY HÔM NAY", "TODAY'S PUZZLE", "今日老师出题", "今日の先生の問題"), fontSize = 11.sp, color = Red, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                        Text(t("Một bài chiếu hết mới mỗi ngày · 2 phút", "A new mate puzzle every day · 2 minutes", "每天一道新杀棋题 · 2分钟", "毎日新しいメイト問題 · 2分"), fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                    }
                    Text(t("Giải", "Solve", "解题", "解く"), color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Red).padding(horizontal = 14.dp, vertical = 9.dp))
                }
                // ---------- 4 thẻ màu ----------
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lv = Level.of(settings.level)
                    ColorCard(
                        1, "♟", t("Chơi với máy", "Play vs computer", "人机对弈", "コンピュータと対局"), t("Đang ở cấp ${lv.id} · ${lv.title}", "At level ${lv.id} · ${lv.title}", "当前${lv.id}级 · ${lv.title}", "レベル${lv.id} · ${lv.title}"), HomeRed, Modifier.weight(1f), onNewGame,
                    ) { LevelBar(lv.id) }
                    ColorCard(
                        5, "🧩", t("Bài tập", "Puzzles", "习题", "問題集"), t("${solved.size}/$puzzleCount · Bài vừa sức", "${solved.size}/$puzzleCount · For you", "${solved.size}/$puzzleCount · 适合你", "${solved.size}/${puzzleCount} · あなた向け"), HomeAmber, Modifier.weight(1f), onPuzzles,
                        badge = player?.let { t("Bậc ${it.puzzleBand}/5", "Level ${it.puzzleBand}/5", "等级${it.puzzleBand}/5", "段階${it.puzzleBand}/5") },
                    ) { ProgressBar(if (puzzleCount > 0) solved.size.toFloat() / puzzleCount else 0f) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorCard(
                        3, "📖", t("Khai cuộc", "Openings", "开局", "定跡"), t("Ý, Tây Ban Nha, Sicilia…", "Italian, Ruy Lopez, Sicilian…", "意大利、西班牙、西西里…", "イタリアン、ルイ・ロペス…"), HomeGreen, Modifier.weight(1f), onOpenings,
                    ) { Text(t("$openings thế có tên ›", "$openings named lines ›", "${openings}种布局 ›", "名前のある定跡${openings}種 ›"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    ColorCard(
                        2, "📈", t("Tiến bộ", "Progress", "进步", "上達"), t("$titleName · Sổ tay", "$titleName · Notebook", "$titleName · 错题本", "${titleName} · ミスノート"), HomeBlue, Modifier.weight(1f), onProgress,
                        badge = dueCount.takeIf { it > 0 }?.let { t("$it thế cần ôn", "$it to review", "${it}个待复习", "復習${it}件") },
                    ) {
                        val pts = remember(settings.accuracyLog) { settings.accuracyEntries().takeLast(8).map { it.second } }
                        if (pts.size >= 2) Sparkline(pts) else Text(acc?.let { t("Chính xác 7 ngày $it%", "7-day accuracy $it%", "7日准确率$it%", "7日間の正確さ${it}%") } ?: t("${settings.games} ván đã chơi", "${settings.games} games played", "已下${settings.games}盘", "${settings.games}局対局済み"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (!settings.adsRemoved) BannerAd(container.adsManager, Modifier.fillMaxWidth())
    }
}

private val HomeRed = listOf(Color(0xFFD2382B), Color(0xFF8E1D15))
private val HomeAmber = listOf(Color(0xFFE9A23B), Color(0xFFB86A12))
private val HomeGreen = listOf(Color(0xFF2F8F68), Color(0xFF1C5E43))
private val HomeBlue = listOf(Color(0xFF3C5BA8), Color(0xFF253B78))

@Composable
private fun GoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, color = RedDark, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(Gold).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
    )
}

/** Lịch tuần trong khối đỏ: chấm vàng = đã tập, nửa = ngày nghỉ, viền đứt = hôm nay chưa tập. */
@Composable
private fun HeroWeek(dots: List<StreakCalculator.Dot>, done: Int, streak: Int, modifier: Modifier = Modifier) {
    val names = DAY_LETTER
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        dots.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(18.dp)) {
                    val r = size.minDimension / 2
                    val dim = Color.White.copy(alpha = 0.22f)
                    when (d) {
                        StreakCalculator.Dot.DONE -> drawCircle(Gold, r)
                        StreakCalculator.Dot.REST -> { drawCircle(dim, r); drawArc(Gold, 90f, 180f, true) }
                        StreakCalculator.Dot.TODAY_PENDING -> drawCircle(Gold, r - 1.5.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
                        else -> drawCircle(dim, r)
                    }
                }
                Text(names[i], fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "$done/7" + if (streak >= 2) t(" · $streak ngày liền", " · $streak days in a row", " · 连续${streak}天", " · ${streak}日連続") else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Thẻ màu ở Trang chủ: chữ Hán chìm, icon, tiêu đề, nhãn góc, chỉ báo ở đáy. */
@Composable
private fun ColorCard(
    han: Int, icon: String, title: String, sub: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit,
    badge: String? = null, foot: @Composable () -> Unit,
) {
    // Chữ to (+20%) thì thẻ cao theo để không bị cắt
    val h = (140 * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    Box(
        modifier.height(h).shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors)).clickable(onClick = onClick),
    ) {
        // Hình chìm quân cờ ở góc thẻ
        com.huanhuynh.covua.ui.board.PieceIcon(
            han, Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 14.dp).size(100.dp), tint = Color.White.copy(alpha = 0.16f),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 17.sp)
            }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(top = 6.dp))
            Text(sub, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            foot()
        }
        badge?.let {
            Text(
                it, color = Red, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** 8 nấc cấp độ máy. */
@Composable
private fun LevelBar(level: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(8) { i ->
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < level) Gold else Color.White.copy(alpha = 0.28f)))
        }
    }
}

@Composable
private fun ProgressBar(f: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.03f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
    }
}

/** Đường độ chính xác các ván gần đây. */
@Composable
private fun Sparkline(values: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val lo = (values.min() - 5).toFloat(); val hi = (values.max() + 5).toFloat()
        val pts = values.mapIndexed { i, v -> Offset(i * size.width / (values.size - 1), size.height - (v - lo) / (hi - lo) * size.height) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Gold, 3.5.dp.toPx(), pts.last())
    }
}
internal val SHORT_DAY get() = when (com.huanhuynh.covua.i18n.I18n.lang) {
    com.huanhuynh.covua.i18n.Lang.EN -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    com.huanhuynh.covua.i18n.Lang.ZH -> listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    com.huanhuynh.covua.i18n.Lang.JA -> listOf("月", "火", "水", "木", "金", "土", "日")
    else -> listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
}

/** Tên thứ rất ngắn cho ô lịch (T2…CN / M…S / 一…日). */
internal val DAY_LETTER get() = when (com.huanhuynh.covua.i18n.I18n.lang) {
    com.huanhuynh.covua.i18n.Lang.EN -> listOf("M", "T", "W", "T", "F", "S", "S")
    com.huanhuynh.covua.i18n.Lang.ZH -> listOf("一", "二", "三", "四", "五", "六", "日")
    com.huanhuynh.covua.i18n.Lang.JA -> listOf("月", "火", "水", "木", "金", "土", "日")
    else -> listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
}
