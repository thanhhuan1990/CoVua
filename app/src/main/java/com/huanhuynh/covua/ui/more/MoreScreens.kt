package com.huanhuynh.covua.ui.more

import com.huanhuynh.covua.i18n.t

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.PRIVACY_POLICY_URL
import com.huanhuynh.covua.ads.BannerAd
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.data.PieceStyle
import com.huanhuynh.covua.engine.Game
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.OpeningBook
import com.huanhuynh.covua.ui.CardBox
import com.huanhuynh.covua.ui.NavRow
import com.huanhuynh.covua.ui.SectionTitle
import com.huanhuynh.covua.ui.Segmented
import com.huanhuynh.covua.ui.SwitchRow
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.board.BoardView
import com.huanhuynh.covua.ui.game.EvalChart
import com.huanhuynh.covua.ui.game.findActivity
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import com.huanhuynh.xpromo.MoreAppsSection
import com.huanhuynh.xpromo.XPromo
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.feature.daily.MonthCalendar
import com.huanhuynh.covua.feature.onboarding.Onboarding
import com.huanhuynh.covua.feature.onboarding.SkillAnswer
import com.huanhuynh.covua.feature.onboarding.SkillQuestionDialog
import com.huanhuynh.covua.feature.review.NotebookSection
import com.huanhuynh.covua.training.StreakCalculator
import com.huanhuynh.covua.training.Titles

@Composable
fun SettingsScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = container.settings
    var askSkill by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }
    if (showLicences) LicencesDialog { showLicences = false }
    val player by remember { container.player.flow() }.collectAsStateWithLifecycle(initialValue = null)
    if (askSkill) {
        SkillQuestionDialog(settings.bigText, SkillAnswer.of(player?.skillAnswer), onAnswer = { a, big ->
            askSkill = false
            scope.launch { Onboarding.apply(container, a, big) }
        }, onSkip = { big -> askSkill = false; scope.launch { repo.setBigText(big) } })
    }
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Cài đặt", "Settings", "设置", "設定"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(t("Ngôn ngữ", "Language", "语言", "言語"))
            CardBox {
                com.huanhuynh.covua.feature.onboarding.LanguageRow(com.huanhuynh.covua.i18n.AppLang.current(context)) { tag ->
                    container.events.log(com.huanhuynh.covua.domain.Ev.LANGUAGE_PICK, mapOf("lang" to tag, "first" to "false"))
                    com.huanhuynh.covua.i18n.AppLang.apply(context, tag)
                    container.reminders.ensureChannels()
                }
            }
            SectionTitle(t("Bàn cờ", "Board", "棋盘", "盤面"))
            CardBox {
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Kiểu quân", "Piece style", "棋子样式", "駒のスタイル"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                        Text(t("Hình quân cờ hoặc chữ (Vua, Hậu, Xe…)", "Classic pieces or letters (K, Q, R…)", "棋子图形或文字（王、后、车…）", "駒の絵または文字（K、Q、R…）"), fontSize = 12.sp, color = Muted)
                    }
                    Segmented(listOf(t("♞ Hình", "♞ Pieces", "♞ 图形", "♞ 駒"), t("Chữ", "Letters", "文字", "文字")), if (settings.pieceStyle == PieceStyle.CLASSIC) 0 else 1, Modifier.width(150.dp)) {
                        scope.launch { repo.setPieceStyle(if (it == 0) PieceStyle.CLASSIC else PieceStyle.LETTERS) }
                    }
                }
                HorizontalDivider(color = Line)
                SwitchRow(t("Hiện ô đi được", "Show legal moves", "显示可走位置", "動けるマスを表示"), t("Chấm xanh các ô quân đang cầm đi tới được", "Green dots on squares the selected piece can reach", "用绿点标出所选棋子可走的格子", "選んだ駒が動けるマスに緑の点"), settings.showTargets) { v -> scope.launch { repo.setShowTargets(v) } }
                SwitchRow(t("Lật bàn khi cầm Đen", "Flip board when playing Black", "执黑时翻转棋盘", "黒番のとき盤を反転"), null, settings.flipWhenBlack) { v -> scope.launch { repo.setFlip(v) } }
                SwitchRow(t("Âm thanh", "Sound", "音效", "効果音"), t("Tiếng quân gỗ, ăn quân, chiếu, thắng/thua, bài tập", "Wooden piece clicks, captures, check, win/lose, puzzles", "木棋落子、吃子、将军、胜负、习题音效", "木の駒音、駒取り、チェック、勝敗、問題"), settings.sound, last = true) { v ->
                    scope.launch { repo.setSound(v) }
                    if (v) { container.sounds.enabled = true; container.sounds.play(com.huanhuynh.covua.Sfx.MOVE) }
                }
            }
            SectionTitle(t("Trình độ & hiển thị", "Level & display", "水平与显示", "レベルと表示"))
            CardBox {
                NavRow(t("Trình độ của tôi", "My level", "我的水平", "自分のレベル"), SkillAnswer.of(player?.skillAnswer)?.label ?: t("Chưa chọn – thầy chọn cấp và bài vừa sức", "Not set – your coach picks a suitable level and puzzles", "未选择——老师会挑选合适的等级和题目", "未設定 – 先生が合うレベルと問題を選びます")) { askSkill = true }
                SwitchRow(t("Chữ to hơn", "Larger text", "大号字体", "大きな文字"), t("Phóng chữ thêm 20% cho dễ đọc", "Text 20% larger for easier reading", "字体放大20%，更易阅读", "文字を20%大きくして読みやすく"), settings.bigText, last = true) { v -> scope.launch { repo.setBigText(v) } }
            }
            if (container.v11(RcKeys.NOTIF_ENABLED)) {
                SectionTitle(t("Thầy nhắc", "Coach reminders", "老师提醒", "先生のリマインド"))
                CardBox {
                    SwitchRow(t("Nhắc tập mỗi ngày", "Daily practice reminder", "每日练习提醒", "毎日の練習リマインド"), t("Tối đa 1 lần/ngày, không nhắc nếu hôm nay bạn đã tập, không nhắc sau 21:00", "At most once a day, not if you've already practised, never after 21:00", "每天最多一次；当天已练习不提醒；21:00后不提醒", "1日最大1回、練習済みの日や21時以降は通知しません"), settings.notifOn) { v ->
                        scope.launch {
                            repo.setNotif(v, settings.notifMinute, settings.notifAuto)
                            if (v) container.reminders.schedule(com.huanhuynh.covua.notify.ReminderWorker.targetMinute(container)) else container.reminders.cancel()
                        }
                    }
                    if (settings.notifOn) {
                        val def = container.rcInt(RcKeys.NOTIF_DEFAULT_HOUR) * 60
                        val cur = if (settings.notifMinute >= 0) settings.notifMinute else def
                        Text(t("Giờ nhắc", "Reminder time", "提醒时间", "通知時刻"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(7 * 60, 12 * 60, 17 * 60, 19 * 60, 20 * 60).forEach { m ->
                                val on = !settings.notifAuto && cur == m
                                Text("%02d:00".format(m / 60), fontSize = 13.sp, color = if (on) Color.White else Ink,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Red else Color.White).border(1.dp, Line, RoundedCornerShape(14.dp))
                                        .clickable { scope.launch { repo.setNotif(true, m, false); container.reminders.schedule(m) } }.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                        SwitchRow(t("Theo giờ tôi hay mở app", "When I usually open the app", "按我常打开应用的时间", "いつもアプリを開く時間"), t("Thầy tự chọn giờ theo thói quen 2 tuần gần đây", "Picked from your habits over the last 2 weeks", "根据最近两周的习惯自动选择", "直近2週間の習慣から自動で選びます"), settings.notifAuto, last = true) { v ->
                            scope.launch {
                                repo.setNotif(true, settings.notifMinute, v)
                                container.reminders.schedule(com.huanhuynh.covua.notify.ReminderWorker.targetMinute(container))
                            }
                        }
                    }
                }
            }
            if (container.v11(RcKeys.STREAK_ENABLED)) {
                SectionTitle(t("Lịch tập", "Practice streak", "练习日历", "練習カレンダー"))
                CardBox {
                    SwitchRow(t("Ẩn lịch tập", "Hide practice streak", "隐藏练习日历", "練習カレンダーを隠す"), t("Không hiện chip tuần và lời chúc mốc (vẫn ghi dữ liệu)", "Hide the weekly chip and milestone messages (still recorded)", "不显示每周标记和里程碑祝贺（仍会记录）", "週のチップと達成メッセージを非表示（記録は続きます）"), settings.hideStreak, last = true) { v ->
                        scope.launch { repo.setHideStreak(v) }
                        if (v) container.events.log(com.huanhuynh.covua.domain.Ev.STREAK_HIDDEN)
                    }
                }
            }
            SectionTitle(t("Thầy kèm", "Coaching", "老师陪练", "先生のサポート"))
            CardBox {
                SwitchRow(t("Chỉ đánh cờ với máy", "Just play the computer", "只和电脑下棋", "コンピュータと対局のみ"), t("Ván mới không có lời thầy, cảnh báo, gợi ý. Vẫn xem được phân tích sau ván.", "New games have no comments, warnings or hints. Analysis is still available afterwards.", "新对局没有点评、警告和提示，赛后仍可查看分析。", "新しい対局では解説・警告・ヒントなし。終局後の分析は見られます。"), settings.playOnly) { v ->
                    scope.launch { repo.setPlayOnly(v) }
                }
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Mức chi tiết", "Detail level", "详细程度", "解説の詳しさ"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                        Text(t("Gọn: chỉ nhãn + tiêu đề cho nước tốt", "Brief: only label + title for good moves", "简洁：好棋只显示标签和标题", "簡潔：良い手はラベルと見出しのみ"), fontSize = 12.sp, color = Muted)
                    }
                    Segmented(listOf(t("Gọn", "Brief", "简洁", "簡潔"), t("Đủ", "Full", "完整", "詳細")), if (settings.coachFull) 1 else 0, Modifier.width(120.dp)) { scope.launch { repo.setCoachFull(it == 1) } }
                }
                HorizontalDivider(color = Line)
                SwitchRow(t("Hộp thoại \"Có nước tốt hơn\"", "\"Better move\" dialog", "\"有更好着法\"对话框", "「より良い手」ダイアログ"), t("Hiện ngay khi bạn đi chưa tối ưu, kèm nút đi lại theo nước thầy gợi ý", "Shown right after a sub-optimal move, with a button to take back and play the suggestion", "走得不够好时立即显示，可按建议悔棋重走", "緩手の直後に表示、待ったしておすすめの手を指すボタン付き"), settings.betterDialog) { v ->
                    scope.launch { repo.setBetterDialog(v) }
                }
                SwitchRow(t("Đọc lời thầy", "Read comments aloud", "朗读老师点评", "解説を読み上げる"), t("Dùng giọng đọc tiếng Việt của máy (cần gói giọng nói tiếng Việt)", "Uses the device's English text-to-speech voice", "使用设备的中文语音朗读", "端末の日本語音声で読み上げます"), settings.tts, last = true) { v ->
                    scope.launch { repo.setTts(v) }
                    if (v) container.tts.speak(t("Xin chào, thầy sẽ đọc lời nhận xét cho bạn.", "Hello, I'll read my comments aloud for you.", "你好，老师会为你朗读点评。", "こんにちは、先生が解説を読み上げます。"))
                }
            }
            SectionTitle(t("Khác", "Other", "其他", "その他"))
            CardBox {
                NavRow(t("Giới thiệu app cho bạn bè", "Recommend to friends", "推荐给好友", "友達におすすめ"), t("Gửi link qua Zalo, Messenger…", "Send a link via chat apps…", "通过微信、QQ等发送链接…", "LINEなどでリンクを送る…")) {
                    com.huanhuynh.covua.share.Sharer.shareText(
                        context, t("Mình đang tập cờ vua với \"Cờ Vua Có Thầy\": chơi với máy offline, thầy chấm và giải thích từng nước. Tải miễn phí:", "I'm practising chess with Chess Coach: play the computer offline, with every move graded and explained. Free download:", "我在用「国际象棋老师」练国际象棋：离线人机对弈，每一步都有点评讲解。免费下载：", "「チェス先生」でチェスを練習中：オフラインでコンピュータと対局、一手ごとに評価と解説つき。無料ダウンロード："), "invite",
                    )
                }
                NavRow(t("Chính sách quyền riêng tư", "Privacy policy", "隐私政策", "プライバシーポリシー")) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
                }
                if (container.adsManager.privacyOptionsRequired.value) {
                    NavRow(t("Tuỳ chọn quyền riêng tư (quảng cáo)", "Privacy options (ads)", "隐私选项（广告）", "プライバシー設定（広告）")) { context.findActivity()?.let { container.adsManager.showPrivacyOptionsForm(it) } }
                }
                NavRow(t("Máy đánh cờ", "Chess engine", "国际象棋引擎", "チェスエンジン"), container.stockfish.name) {}
                NavRow(t("Giấy phép phần mềm", "Open-source licences", "开源许可", "オープンソースライセンス"), "Stockfish (GPL v3)") { showLicences = true }
                NavRow(t("Phiên bản", "Version", "版本", "バージョン"), com.huanhuynh.covua.BuildConfig.VERSION_NAME, last = true) {}
            }
            if (!settings.adsRemoved) {
                Spacer(Modifier.height(16.dp))
                XPromo.get()?.let { MoreAppsSection(it, placement = "settings") }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (!settings.adsRemoved) BannerAd(container.adsManager, Modifier.fillMaxWidth())
    }
}

@Composable
fun ProgressScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit, onReview: () -> Unit) {
    val entries = remember(settings.accuracyLog) { settings.accuracyEntries() }
    val context = LocalContext.current
    val player by remember { container.player.flow() }.collectAsStateWithLifecycle(initialValue = null)
    val solved by remember { container.player.solvedFlow() }.collectAsStateWithLifecycle(initialValue = settings.puzzlesSolved.mapNotNull { it.toIntOrNull() }.toSet())
    val snap by remember { container.activity.flow() }.collectAsStateWithLifecycle(initialValue = null)
    var puzzleCount by remember { mutableIntStateOf(container.content.peek()?.puzzles?.size ?: 0) }
    LaunchedEffect(Unit) { puzzleCount = container.content.get().puzzles.size }
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Tiến bộ", "Progress", "进步", "上達"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Danh hiệu (chỉ lên)
            if (container.flag(RcKeys.TITLES_ENABLED)) {
                val lv = maxOf(player?.titleId ?: 0, settings.wonLevels.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0)
                val title = Titles.of(lv)
                Row(
                    Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF4E0))
                        .border(1.dp, Color(0xFFF2D9A8), RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎖", fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
                        Text(Titles.SUBTITLE + if (lv > 0) t(" · đã thắng máy cấp $lv không chấp", " · beat level $lv without handicap", " · 不让子战胜过${lv}级电脑", " · 互先でレベル${lv}に勝利") else t(" · thắng máy để lên danh hiệu", " · beat the computer to earn titles", " · 战胜电脑来提升称号", " · コンピュータに勝って称号を上げよう"), fontSize = 12.sp, color = Muted)
                    }
                    if (lv > 0) Text(t("Khoe", "Share", "分享", "シェア"), color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                        com.huanhuynh.covua.share.Sharer.shareText(context, t("Danh hiệu cờ vua của mình: $title (thắng máy cấp $lv) trong Cờ Vua Có Thầy. Thử sức không?", "My chess title: $title (beat level $lv) in Chess Coach. Up for the challenge?", "我在「国际象棋老师」的国际象棋称号：${title}（战胜${lv}级电脑）。来挑战吗？", "「チェス先生」での私の称号：${title}（レベル${lv}に勝利）。挑戦してみる？"), "title")
                    })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Stat("${settings.games}", t("Ván", "Games", "对局", "対局数"), Ink, Modifier.weight(1f))
                Stat("${settings.wins}", t("Thắng", "Wins", "胜", "勝ち"), Good, Modifier.weight(1f))
                Stat("${settings.draws}", t("Hoà", "Draws", "和棋", "引き分け"), Muted, Modifier.weight(1f))
                Stat("${settings.losses}", "Thua", Red, Modifier.weight(1f))
            }
            SectionTitle(t("Độ chính xác các ván gần đây", "Accuracy in recent games", "近期对局准确率", "最近の対局の正確さ"))
            CardBox {
                if (entries.size >= 2) {
                    EvalChart(entries.map { (it.second - 50) * 20 }, Modifier.fillMaxWidth().height(110.dp).padding(vertical = 10.dp))
                    Text(
                        run { val a = settings.accuracy7d()?.let { "$it%" } ?: "—"; val l = entries.last().second; t("Trung bình 7 ngày: $a · Ván gần nhất: $l%", "7-day average: $a · Last game: $l%", "7日平均：$a · 最近一局：$l%", "7日平均：${a} · 直近の対局：${l}%") },
                        fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(bottom = 10.dp),
                    )
                } else {
                    Text(t("Chơi thêm vài ván có thầy kèm để thấy biểu đồ tiến bộ.", "Play a few coached games to see your progress chart.", "多下几盘有老师点评的棋，就能看到进步曲线。", "先生つきで何局か指すと、上達グラフが表示されます。"), fontSize = 13.sp, color = Muted, modifier = Modifier.padding(vertical = 14.dp))
                }
            }
            // Lịch tập
            val sn = snap
            if (sn != null && container.v11(RcKeys.STREAK_ENABLED) && !settings.hideStreak) {
                SectionTitle(t("Lịch tập", "Practice streak", "练习日历", "練習カレンダー"))
                CardBox {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${sn.state.current}", if (sn.state.status == StreakCalculator.Status.PAUSED) t("ngày (tạm dừng)", "days (paused)", "天（暂停）", "日（一時停止）") else t("ngày liền", "days in a row", "连续天数", "日連続"), Red, Modifier.weight(1f))
                        Stat("${sn.state.best}", t("kỷ lục", "best", "最高纪录", "最高記録"), Ink, Modifier.weight(1f))
                        Stat("${sn.weekDone}/7", t("tuần này", "this week", "本周", "今週"), Good, Modifier.weight(1f))
                    }
                    if (sn.state.status == StreakCalculator.Status.PAUSED) Text(
                        t("Lịch tập đang tạm dừng – tập lại trong 3 ngày để nối tiếp. Mỗi tuần có 2 ngày nghỉ tự động.", "Your streak is paused – practise within 3 days to continue it. You get 2 automatic rest days each week.", "练习连续记录已暂停——3天内练习即可接上。每周有2天自动休息日。", "連続記録は一時停止中 – 3日以内に練習すれば続きます。毎週2日まで自動でお休みできます。"),
                        fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    MonthCalendar(sn.days, sn.state.restDays)
                    Spacer(Modifier.height(10.dp))
                }
            }
            NotebookSection(container, onReview)
            SectionTitle(t("Bài tập", "Puzzles", "习题", "問題集"))
            CardBox {
                Text(t("Đã giải ${solved.size}/$puzzleCount bài", "Solved ${solved.size}/$puzzleCount", "已解${solved.size}/${puzzleCount}题", "解いた問題 ${solved.size}/${puzzleCount}"), fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 14.dp))
                player?.let { Text(t("Bậc bài vừa sức: ${it.puzzleBand}/5", "Puzzle level: ${it.puzzleBand}/5", "题目等级：${it.puzzleBand}/5", "問題の段階：${it.puzzleBand}/5"), fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)) }
            }
        }
    }
}

@Composable
private fun Stat(n: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(n, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

/** Danh mục khai cuộc: tên, ý nghĩa, thế cờ sau các nước tiêu biểu. */
@Composable
fun OpeningsScreen(settings: AppSettings, onBack: () -> Unit, onPlay: () -> Unit) {
    val items = remember {
        OpeningBook.catalog().mapNotNull { (codes, name) ->
            val g = Game()
            for (c in codes) {
                val m = Notation.parse(g.pos, c).takeIf { it != 0 } ?: return@mapNotNull null
                g.play(m)
            }
            Triple(codes, name, g)
        }
    }
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Khai cuộc", "Openings", "开局", "定跡"), onBack)
        Text(
            t("Khi bạn đi đúng các thế này trong ván, thầy sẽ gọi tên và chấm \"Đúng bài\".", "When you play these lines in a game, your coach names them and marks them \"Book move\".", "对局中走出这些布局时，老师会说出名称并标为\"定式\"。", "対局でこれらの手順を指すと、先生が名前を挙げて「定跡」と評価します。"),
            fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.second.title }) { (codes, name, g) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(96.dp)) { BoardView(board = g.pos.board, style = settings.pieceStyle, lastMove = g.moves.lastOrNull() ?: 0, showCoords = false) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                        Text(codes.mapIndexed { i, c -> (if (i % 2 == 0) "${i / 2 + 1}." else "") + Notation.localize(c) }.joinToString(" "), fontSize = 12.sp, color = Red, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
                        Text(name.note, fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp)
                    }
                }
            }
            item {
                com.huanhuynh.covua.ui.PillButton(t("Chơi thử với máy", "Try it vs computer", "和电脑试试", "コンピュータと試してみる"), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), onClick = onPlay)
            }
        }
    }
}

/** Giấy phép phần mềm bên thứ ba. Stockfish (GPL v3): ghi công + chỉ nơi lấy mã nguồn đúng phiên bản. */
@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Giấy phép phần mềm", "Open-source licences", "开源许可", "オープンソースライセンス")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Stockfish", fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    t(
                        "Máy đánh cờ của app là Stockfish – phần mềm tự do theo Giấy phép Công cộng GNU phiên bản 3 (GPL v3), bản quyền thuộc các tác giả Stockfish. App gọi Stockfish như một chương trình riêng. Bạn có quyền nhận, sửa và phân phối lại mã nguồn Stockfish theo GPL v3.",
                        "The app's chess engine is Stockfish – free software under the GNU General Public License version 3 (GPL v3), copyright the Stockfish authors. The app runs Stockfish as a separate program. You may obtain, modify and redistribute the Stockfish source code under the GPL v3.",
                        "本应用的国际象棋引擎是 Stockfish——依据 GNU 通用公共许可证第 3 版（GPL v3）发布的自由软件，版权归 Stockfish 作者所有。本应用将 Stockfish 作为独立程序运行。你可以依据 GPL v3 获取、修改和再发布 Stockfish 源代码。",
                        "このアプリのチェスエンジンは Stockfish です。GNU 一般公衆利用許諾書 第3版（GPL v3）に基づくフリーソフトウェアで、著作権は Stockfish の作者に帰属します。アプリは Stockfish を独立したプログラムとして実行します。GPL v3 に従い、Stockfish のソースコードを入手・改変・再配布できます。",
                    ),
                    fontSize = 13.sp, color = Muted, lineHeight = 18.sp,
                )
                Text(
                    t("Mã nguồn Stockfish (phiên bản sf_11)", "Stockfish source code (version sf_11)", "Stockfish 源代码（sf_11 版）", "Stockfish ソースコード（sf_11）"),
                    color = Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/official-stockfish/Stockfish/tree/sf_11"))) }
                    },
                )
                Text(
                    t("Toàn văn GPL v3", "Full text of the GPL v3", "GPL v3 全文", "GPL v3 全文"),
                    color = Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))) }
                    },
                )
                Text("DejaVu Fonts", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 12.dp))
                Text(t("Hình quân trên biểu tượng app dựa trên phông DejaVu Sans (giấy phép tự do Bitstream Vera).", "The pieces in the app icon are based on the DejaVu Sans font (Bitstream Vera free licence).", "应用图标中的棋子基于 DejaVu Sans 字体（Bitstream Vera 自由许可）。", "アイコンの駒は DejaVu Sans フォント（Bitstream Vera フリーライセンス）に基づきます。"), fontSize = 13.sp, color = Muted, lineHeight = 18.sp)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
