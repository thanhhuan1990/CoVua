package com.huanhuynh.covua

import com.huanhuynh.covua.i18n.t

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.engine.Handicap
import com.huanhuynh.covua.feature.daily.DailySessionScreen
import com.huanhuynh.covua.feature.onboarding.BigTextScope
import com.huanhuynh.covua.feature.onboarding.Onboarding
import com.huanhuynh.covua.feature.onboarding.SkillAnswer
import com.huanhuynh.covua.feature.onboarding.SkillQuestionDialog
import com.huanhuynh.covua.feature.review.ReviewScreen
import com.huanhuynh.covua.notify.ReminderScheduler
import com.huanhuynh.covua.notify.ReminderWorker
import com.huanhuynh.covua.ui.theme.Red
import androidx.appcompat.app.AppCompatActivity
import com.huanhuynh.covua.i18n.AppLang
import com.huanhuynh.covua.feature.onboarding.LanguagePicker
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.huanhuynh.covua.ads.AppOpenAdManager
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.ui.game.AnalysisScreen
import com.huanhuynh.covua.ui.game.GameScreen
import com.huanhuynh.covua.ui.game.ReplayScreen
import com.huanhuynh.covua.ui.game.findActivity
import com.huanhuynh.covua.ui.home.HomeScreen
import com.huanhuynh.covua.ui.home.NewGameScreen
import com.huanhuynh.covua.ui.more.OpeningsScreen
import com.huanhuynh.covua.ui.more.ProgressScreen
import com.huanhuynh.covua.ui.more.SettingsScreen
import com.huanhuynh.covua.ui.puzzle.PuzzleListScreen
import com.huanhuynh.covua.ui.puzzle.FitPuzzleScreen
import com.huanhuynh.covua.ui.puzzle.PuzzleScreen
import com.huanhuynh.covua.ui.theme.CoVuaTheme
import com.huanhuynh.covua.ui.theme.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    /** Đích mở từ thông báo (daily / review / game). */
    private val deepLink = mutableStateOf<String?>(null)

    private fun handleIntent(intent: Intent?, container: AppContainer): Boolean {
        val dest = intent?.getStringExtra(ReminderScheduler.EXTRA_DEST) ?: return false
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_FROM_NOTIF)
        intent.removeExtra(ReminderScheduler.EXTRA_DEST)
        // Mở từ thông báo: không App Open / interstitial trong 90 s
        container.openedFromNotificationAt = System.currentTimeMillis()
        container.adsManager.suppressFullScreenUntil = SystemClock.elapsedRealtime() + container.rcInt(RcKeys.NOTIF_OPEN_AD_GRACE_SEC) * 1000L
        container.events.log(Ev.NOTIF_OPENED, mapOf("type" to (type ?: dest).lowercase()))
        container.appScope.launch { container.settings.setInt(ReminderScheduler.K_IGNORED, 0) }
        deepLink.value = dest
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, (application as CoVuaApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        AppLang.sync(this)
        enableEdgeToEdge()

        val container = (application as CoVuaApp).container
        var keepSplash = savedInstanceState == null
        splash.setKeepOnScreenCondition { keepSplash }
        container.adsManager.gatherConsent(this) { container.onConsentDone() }
        val fromNotification = handleIntent(intent, container)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val launches = container.settings.incrementLaunchCount()
                val eligible = launches > 1 && !fromNotification
                if (eligible) {
                    val maxWait = FirebaseHelper.getLong(this@MainActivity, RcKeys.SPLASH_MAX_WAIT_MS)
                    withTimeoutOrNull(maxWait) {
                        container.appOpenAdManager.state.first {
                            it == AppOpenAdManager.LoadState.LOADED || it == AppOpenAdManager.LoadState.FAILED
                        }
                    }
                }
                keepSplash = false
                if (eligible) container.appOpenAdManager.showIfAvailable(this@MainActivity)
                // Đặt lại lịch "Thầy nhắc" (WorkManager giữ qua khởi động lại máy, nhưng giờ có thể đã đổi)
                val s = container.settings.current()
                if (s.notifOn && container.reminders.hasPermission()) container.reminders.schedule(ReminderWorker.targetMinute(container))
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            CoVuaTheme {
                BigTextScope(settings?.bigText == true) {
                    Box(Modifier.fillMaxSize().background(Paper)) {
                        settings?.let { AppNav(container, it, deepLink) }
                    }
                }
            }
        }
    }
}

private const val NAV_MS = 320

// Chuyển màn kiểu iOS: màn mới trượt từ phải, màn cũ lùi ~1/3 sang trái; quay lại thì ngược lại.
private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it } }
private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { -it / 3 } }
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { -it / 3 } }
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it } }

@Composable
private fun AppNav(container: AppContainer, settings: AppSettings, deepLink: MutableState<String?>) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- Chọn ngôn ngữ lần đầu mở app (trước mọi thứ khác) ----
    var langChosen by remember { mutableStateOf(AppLang.chosen(context)) }
    if (!langChosen) {
        LanguagePicker { tag ->
            container.events.log(Ev.LANGUAGE_PICK, mapOf("lang" to tag, "first" to "true"))
            AppLang.apply(context, tag)
            container.reminders.ensureChannels()
            langChosen = true
        }
        return
    }

    // ---- Câu hỏi trình độ: lần đầu bấm "Chơi với máy" hoặc "Buổi tập" ----
    var afterSkill by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun withSkill(action: () -> Unit) {
        if (!settings.skillAsked && container.flag(RcKeys.ONBOARD_SKILL_QUESTION)) afterSkill = action else action()
    }
    afterSkill?.let { next ->
        SkillQuestionDialog(settings.bigText, onAnswer = { a, big ->
            afterSkill = null
            scope.launch { Onboarding.apply(container, a, big); next() }
        }, onSkip = { big ->
            afterSkill = null
            scope.launch { Onboarding.apply(container, SkillAnswer.KNOWS, big, skipped = true); next() }
        })
    }

    fun startNewGame(nav: NavHostController, level: Level, sideChoice: Int, explain: Boolean, warn: Boolean, hint: Boolean, undo: Boolean, handicap: Handicap = Handicap.NONE, fromFen: String? = null) {
        val side = if (sideChoice == 0) (if (Random.nextBoolean()) 1 else -1) else sideChoice
        container.game.newGame(level, side, explain, warn, hint, undo, handicap, fromFen)
        if (fromFen != null) container.events.log(Ev.PLAY_FROM_POSITION)
        nav.navigate("game") { popUpTo("home") }
    }
    // F9: chọn cấp như màn Ván mới rồi chơi tiếp từ FEN
    var fromFen by remember { mutableStateOf<Pair<String, Int>?>(null) }
    fun playFrom(fen: String, humanSide: Int) {
        fromFen = fen to humanSide
        nav.navigate("new/fen")
    }

    // ---- Deep link từ thông báo (chạy cả khi app vừa được mở lại từ trạng thái bị tắt) ----
    LaunchedEffect(deepLink.value) {
        val d = deepLink.value ?: return@LaunchedEffect
        deepLink.value = null
        container.migrated.await()
        when (d) {
            ReminderScheduler.DEST_REVIEW -> nav.navigate("review")
            ReminderScheduler.DEST_GAME -> {
                val g = container.archive.inProgress()
                val ok = container.game.hasGame || (g != null && container.game.resumeFrom(g, container.archive.evals(g.id), settings.coachExplain, settings.coachWarn, settings.coachHint, settings.allowUndo))
                if (ok) nav.navigate("game") else nav.navigate("daily/notif")
            }
            else -> nav.navigate("daily/notif")
        }
    }

    // ---- Xin quyền thông báo: sau ván / buổi tập đầu, có màn giải thích trước; "Để sau" → hỏi lại sau 3 ngày, tối đa 2 lần ----
    var notifPrompt by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.events.log(Ev.NOTIF_PERMISSION, mapOf("granted" to granted.toString()))
        scope.launch {
            container.settings.setNotif(granted, settings.notifMinute, settings.notifAuto)
            if (granted) container.reminders.schedule(ReminderWorker.targetMinute(container))
        }
    }
    LaunchedEffect(settings.games) {
        if (!container.v11(RcKeys.NOTIF_ENABLED) || !settings.notifOn) return@LaunchedEffect
        container.migrated.await()
        val played = settings.games >= 1 || runCatching { container.activity.current().days.any { it.counts } }.getOrDefault(false)
        if (!played) return@LaunchedEffect
        if (container.reminders.hasPermission()) { container.reminders.schedule(ReminderWorker.targetMinute(container)); return@LaunchedEffect }
        val count = container.settings.getInt(ReminderScheduler.K_PROMPT_COUNT)
        val last = container.settings.getLongKey(ReminderScheduler.K_PROMPTED_AT, -100)
        if (count < 2 && container.clock.today() - last >= 3) notifPrompt = true
    }
    if (notifPrompt) {
        AlertDialog(
            onDismissRequest = { notifPrompt = false },
            title = { Text(t("Thầy nhắc bạn tập mỗi tối nhé?", "Want your coach to remind you to practise each evening?", "要老师每晚提醒你练习吗？", "毎晩、先生から練習のリマインドを受け取りますか？")) },
            text = { Text(t("Mỗi ngày tối đa một lời nhắc ngắn (ví dụ: có thế cờ cần ôn, bài của thầy hôm nay). Hôm nào bạn đã tập thì thầy không nhắc. Tắt lúc nào cũng được trong Cài đặt.", "At most one short reminder a day (e.g. positions to review, today's lesson). No reminder on days you've already practised. Turn it off any time in Settings.", "每天最多一条简短提醒（例如待复习的局面、今日练习）。当天已练习就不提醒。可随时在设置中关闭。", "1日最大1回の短いお知らせです（復習する局面、今日の課題など）。その日すでに練習していれば通知しません。設定でいつでもオフにできます。")) },
            confirmButton = {
                TextButton(onClick = {
                    notifPrompt = false
                    container.events.log(Ev.NOTIF_PROMPT, mapOf("result" to "yes"))
                    scope.launch { container.settings.setInt(ReminderScheduler.K_PROMPT_COUNT, 99) }
                    if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else scope.launch { container.reminders.schedule(ReminderWorker.targetMinute(container)) }
                }) { Text(t("Được", "Sure", "好的", "はい"), color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    notifPrompt = false
                    container.events.log(Ev.NOTIF_PROMPT, mapOf("result" to "later"))
                    scope.launch {
                        container.settings.setInt(ReminderScheduler.K_PROMPT_COUNT, container.settings.getInt(ReminderScheduler.K_PROMPT_COUNT) + 1)
                        container.settings.setLongKey(ReminderScheduler.K_PROMPTED_AT, container.clock.today())
                    }
                }) { Text(t("Để sau", "Later", "以后再说", "あとで")) }
            },
        )
    }

    // Bị bỏ qua 7 lần → đã dừng; hỏi lại đúng 1 lần trong app
    var stopAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (container.settings.getInt(ReminderScheduler.K_STOP_PENDING) == 1) stopAsk = true }
    if (stopAsk) {
        fun close(keep: Boolean) {
            stopAsk = false
            scope.launch {
                container.settings.setInt(ReminderScheduler.K_STOP_PENDING, 2)
                if (keep) {
                    container.settings.setInt(ReminderScheduler.K_IGNORED, 0)
                    container.reminders.schedule(ReminderWorker.targetMinute(container))
                } else {
                    container.settings.setNotif(false, settings.notifMinute, settings.notifAuto)
                    container.reminders.cancel()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { close(false) },
            title = { Text(t("Thầy có nên nhắc nữa không?", "Should your coach keep reminding you?", "老师还要继续提醒你吗？", "先生のリマインドを続けますか？")) },
            text = { Text(t("Mấy hôm nay thầy nhắc mà bạn chưa rảnh. Thầy tạm dừng nhắc rồi – bạn muốn thầy nhắc tiếp không?", "You've been busy lately, so reminders are paused. Do you want them back?", "最近你比较忙，提醒已暂停——要恢复提醒吗？", "最近お忙しいようなので、リマインドを一時停止しました。再開しますか？")) },
            confirmButton = { TextButton(onClick = { close(true) }) { Text(t("Nhắc tiếp", "Keep reminding", "继续提醒", "続ける"), color = Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { close(false) }) { Text(t("Thôi, không cần", "No, thanks", "不用了", "いいえ")) } },
        )
    }

    NavHost(
        nav, startDestination = "home",
        enterTransition = pushEnter, exitTransition = pushExit, popEnterTransition = popEnter, popExitTransition = popExit,
    ) {
        composable("home") {
            HomeScreen(
                container, settings,
                onContinue = {
                    scope.launch {
                        container.migrated.await()
                        val g = runCatching { container.archive.inProgress() }.getOrNull()
                        val ok = container.game.hasGame ||
                            (g != null && container.game.resumeFrom(g, runCatching { container.archive.evals(g.id) }.getOrDefault(emptyList()), settings.coachExplain, settings.coachWarn, settings.coachHint, settings.allowUndo)) ||
                            container.game.resume(
                                settings.savedGame, Level.of(settings.savedLevel), settings.savedHumanSide,
                                settings.coachExplain, settings.coachWarn, settings.coachHint, settings.allowUndo,
                            )
                        if (ok) nav.navigate("game")
                    }
                },
                onNewGame = { withSkill { nav.navigate("new") } },
                onPuzzles = { nav.navigate("puzzles") },
                onDailyPuzzle = { scope.launch { container.daily.dailyPuzzleId()?.let { nav.navigate("puzzle/${it.first}") } } },
                onDailySession = { withSkill { nav.navigate("daily/home") } },
                onProgress = { nav.navigate("progress") },
                onSettings = { nav.navigate("settings") },
                onOpenings = { nav.navigate("openings") },
            )
        }
        composable("new") {
            NewGameScreen(settings, onBack = { nav.popBackStack() }, handicapEnabled = container.flag(RcKeys.HANDICAP_ENABLED)) { c ->
                container.appScope.launch {
                    container.settings.setNewGamePrefs(c.level.id, c.side, c.explain, c.warn, c.hint, c.undo)
                    container.settings.setHandicap(c.handicap.id)
                }
                // Điểm chuyển màn tự nhiên (trước khi ván bắt đầu) – InterstitialController tự áp giới hạn tần suất
                if (FirebaseHelper.getLong(context, RcKeys.INTERSTITIAL_ON_NEW_GAME) != 0L && settings.games >= 1) {
                    context.findActivity()?.let { container.interstitial.onTransition(it) }
                }
                startNewGame(nav, c.level, c.side, c.explain, c.warn, c.hint, c.undo, c.handicap)
            }
        }
        composable("new/fen") {
            val f = fromFen
            if (f == null) LaunchedEffect(Unit) { nav.popBackStack() } else NewGameScreen(
                settings, onBack = { nav.popBackStack() }, handicapEnabled = false, fromFen = f.first, presetSide = f.second,
            ) { c ->
                container.appScope.launch { container.settings.setNewGamePrefs(c.level.id, settings.sideChoice, c.explain, c.warn, c.hint, c.undo) }
                startNewGame(nav, c.level, c.side, c.explain, c.warn, c.hint, c.undo, fromFen = f.first)
            }
        }
        composable("game") {
            GameScreen(
                container, settings,
                onBack = { nav.popBackStack() },
                onAnalysis = { nav.navigate("analysis") },
                onNewGame = { nav.navigate("new") },
                onOpenReview = { nav.navigate("progress") },
                onStartWith = { o ->
                    val st = container.game.state.value
                    startNewGame(nav, Level.of(o.level), st.humanSide, st.explain, st.warn, st.hintsOn || o.enableHints, st.undoOn, o.handicap)
                },
            )
        }
        composable("analysis") {
            fun leave(block: () -> Unit) {
                context.findActivity()?.let { container.interstitial.onTransition(it) }
                block()
            }
            AnalysisScreen(
                container, settings,
                onClose = { leave { nav.popBackStack() } },
                onReplay = { ply -> nav.navigate("replay/$ply") },
                onNewGame = { leave { nav.navigate("new") { popUpTo("home") } } },
            )
        }
        composable("replay/{ply}") { e ->
            ReplayScreen(container, settings, e.arguments?.getString("ply")?.toIntOrNull() ?: 0, onBack = { nav.popBackStack() }, onPlayFrom = ::playFrom)
        }
        composable("puzzles") {
            PuzzleListScreen(container, settings, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("puzzle/$it") }, onFit = { nav.navigate("puzzles/fit") })
        }
        composable("puzzles/fit") {
            FitPuzzleScreen(container, settings, onBack = { nav.popBackStack() }, onPlayFrom = { fen -> playFrom(fen, 1) })
        }
        composable("puzzle/{id}") { e ->
            PuzzleScreen(
                container, settings, e.arguments?.getString("id")?.toIntOrNull() ?: 1,
                onBack = { nav.popBackStack() },
                onNext = { id -> nav.navigate("puzzle/$id") { popUpTo("puzzles") } },
                onPlayFrom = { fen -> playFrom(fen, 1) },
            )
        }
        composable("daily/{src}") { e ->
            DailySessionScreen(container, settings, e.arguments?.getString("src") ?: "home", onHome = {
                if (!nav.popBackStack("home", inclusive = false)) nav.navigate("home")
            })
        }
        composable("review") { ReviewScreen(container, settings, onBack = { nav.popBackStack() }) }
        composable("progress") { ProgressScreen(container, settings, onBack = { nav.popBackStack() }, onReview = { nav.navigate("review") }) }
        composable("settings") { SettingsScreen(container, settings, onBack = { nav.popBackStack() }) }
        composable("openings") { OpeningsScreen(settings, onBack = { nav.popBackStack() }, onPlay = { withSkill { nav.navigate("new") } }) }
    }
}
