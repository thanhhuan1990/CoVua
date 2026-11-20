package com.huanhuynh.covua

import android.app.Application
import com.huanhuynh.covua.ads.AdsManager
import com.huanhuynh.covua.ads.AppOpenAdManager
import com.huanhuynh.covua.ads.InterstitialController
import com.huanhuynh.covua.ads.RewardedController
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.GameArchiveRepository
import com.huanhuynh.covua.data.PlayerRepository
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.data.SettingsRepository
import com.huanhuynh.covua.data.content.ContentRepository
import com.huanhuynh.covua.data.db.UserDb
import com.huanhuynh.covua.data.migration.DataStoreToRoomMigrator
import com.huanhuynh.covua.data.migration.LegacySnapshot
import com.huanhuynh.covua.data.migration.MigrationFlag
import com.huanhuynh.covua.data.migration.RoomMigrationTarget
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.domain.EventLogger
import com.huanhuynh.covua.engine.EngineInfo
import com.huanhuynh.covua.feature.daily.DailyConfig
import com.huanhuynh.covua.feature.daily.DailyRepository
import com.huanhuynh.covua.feature.review.ReviewConfig
import com.huanhuynh.covua.feature.review.ReviewRepository
import com.huanhuynh.covua.feature.streak.ActivityRepository
import com.huanhuynh.covua.feature.streak.StreakConfig
import com.huanhuynh.covua.notify.ReminderScheduler
import com.huanhuynh.covua.training.DailyPlanner
import com.huanhuynh.covua.training.LeitnerScheduler
import com.huanhuynh.covua.training.SystemDayClock
import com.huanhuynh.covua.ui.game.GameController
import com.huanhuynh.xpromo.XPromo
import com.huanhuynh.xpromo.XPromoConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PRIVACY_POLICY_URL = "https://thanhhuan1990.github.io/privacy-policy/covua/"

class CoVuaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.huanhuynh.covua.i18n.AppLang.sync(this)
        container = AppContainer(this)
        container.stockfish.install()
        FirebaseHelper.init(this)
        container.appOpenAdManager.register()
        container.startBackground()
        // Quảng bá chéo các app khác của HuanHuynh (thư viện xpromo-android).
        XPromo.init(
            this,
            XPromoConfig(
                selfPackage = packageName,
                hostGroups = listOf("games", "fun"),
                languages = listOf("vi", "en", "zh", "ja"),
                enabled = { FirebaseHelper.getLong(this, RcKeys.XPROMO_ENABLED) != 0L },
                analytics = { name, params ->
                    // Bấm thẻ "Ứng dụng khác" → mở Google Play / app khác: lúc quay lại không hiện App Open.
                    if (name == "xpromo_click" || name == "xpromo_open_app" || name == "xpromo_open_deeplink") {
                        container.appOpenAdManager.markExternalLaunch()
                    }
                    FirebaseHelper.logEvent(this, name, params.mapValues { it.value?.toString() ?: "" })
                },
            ),
        ).refreshIfStale(container.appScope)
    }
}

/** DI thủ công – các phần 1.1 khởi tạo lười (lazy). */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app)
    val adsManager = AdsManager(app, settings, appScope)
    val interstitial = InterstitialController(app, adsManager)
    val rewarded = RewardedController(app, adsManager)
    val appOpenAdManager = AppOpenAdManager(app, adsManager)
    val tts = TtsHelper(app)
    /** Stockfish (nếu đã build vào jniLibs) – máy cấp 4–8 và phân tích của thầy. */
    val stockfish = com.huanhuynh.covua.stockfish.StockfishHub(app)
    val sounds = SoundPlayer(app)
    val events = EventLogger(app)
    val clock = SystemDayClock()
    /** Số bài tập đã giải trong phiên (để giãn interstitial). */
    var puzzlesSolvedSession = 0

    private fun rc(key: String) = FirebaseHelper.getLong(app, key)
    fun rcInt(key: String) = rc(key).toInt()
    val appContext: android.content.Context get() = app
    fun flag(key: String) = FirebaseHelper.flag(app, key)
    /** Cờ tính năng thuộc nhóm v11_holdout (F3, F5, F6, DDA). */
    fun v11(key: String) = FirebaseHelper.v11(app, key)
    private val log: (String, Map<String, String>) -> Unit = { n, p -> events.log(n, p) }

    val db: UserDb by lazy { UserDb.build(app) }
    val content by lazy { ContentRepository(app, appScope) }
    val archive by lazy { GameArchiveRepository(db) }
    val player by lazy { PlayerRepository(db) }
    val reviews by lazy {
        ReviewRepository(db, clock, appScope, {
            ReviewConfig(
                enabled = flag(RcKeys.REVIEW_ENABLED), minLevel = rc(RcKeys.REVIEW_MIN_LEVEL).toInt(),
                minLossCp = rc(RcKeys.REVIEW_MIN_LOSS_CP).toInt(), maxCardsPerGame = rc(RcKeys.REVIEW_MAX_CARDS_PER_GAME).toInt(),
                maxOkMoves = rc(RcKeys.REVIEW_MAX_OK_MOVES).toInt(),
                intervals = LeitnerScheduler.parse(FirebaseHelper.getString(app, RcKeys.REVIEW_INTERVALS_DAYS)),
                verifyDepth = rc(RcKeys.REVIEW_VERIFY_DEPTH).toInt().coerceIn(3, 8),
            )
        }, log)
    }
    val activity by lazy {
        ActivityRepository(db, clock, {
            StreakConfig(
                enabled = v11(RcKeys.STREAK_ENABLED), freeRestPerWeek = rc(RcKeys.STREAK_FREE_REST_DAYS_WEEK).toInt(),
                pauseDays = rc(RcKeys.STREAK_PAUSE_DAYS).toInt(), minMoves = rc(RcKeys.STREAK_MIN_MOVES).toInt(),
            )
        }, log)
    }
    val daily by lazy {
        DailyRepository(settings, content, reviews, player, activity, clock) {
            DailyConfig(
                enabled = v11(RcKeys.DAILY_SESSION_ENABLED), reviewCards = rc(RcKeys.DAILY_REVIEW_CARDS).toInt(),
                extraPuzzles = rc(RcKeys.DAILY_EXTRA_PUZZLES).toInt(), lives = rc(RcKeys.DAILY_LIVES).toInt(),
                weekday = DailyPlanner.parseWeekday(FirebaseHelper.getString(app, RcKeys.DAILY_DIFFICULTY_BY_WEEKDAY)),
            )
        }
    }
    val reminders by lazy { ReminderScheduler(app) }
    val game by lazy { GameController(appScope, settings, this) }

    /** Xong chuyển dữ liệu 1.0 → Room (Home chờ cái này trước khi đọc thống kê). */
    val migrated = CompletableDeferred<Boolean>()

    /** Thời điểm mở app từ thông báo (để không hiện App Open / interstitial trong 90 s). */
    @Volatile var openedFromNotificationAt = 0L
    fun inNotificationGrace(): Boolean =
        System.currentTimeMillis() - openedFromNotificationAt < rc(RcKeys.NOTIF_OPEN_AD_GRACE_SEC) * 1000

    fun startBackground() {
        sounds.preload()
        appScope.launch { settings.settings.collect { sounds.enabled = it.sound } }
        appScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { migrate() }.getOrDefault(false) }
            migrated.complete(ok)
            withContext(Dispatchers.IO) {
                runCatching { activity.markOpen() }
                runCatching { archive.prune() }
            }
            content.get()
        }
        // Khởi động Stockfish sẵn trên luồng nền
        appScope.launch(Dispatchers.Default) { runCatching { stockfish.warmUp() } }
    }

    private suspend fun migrate(): Boolean {
        val flag = object : MigrationFlag {
            override suspend fun version() = settings.getInt("room_migration_version")
            override suspend fun setVersion(v: Int) = settings.setInt("room_migration_version", v).let { }
        }
        val m = DataStoreToRoomMigrator(RoomMigrationTarget(db), flag, EngineInfo.VERSION) { step, t ->
            events.log(Ev.MIGRATION_FAILED, mapOf("step" to step))
            FirebaseHelper.recordException(app, t)
        }
        val out = m.run {
            val s = settings.current()
            LegacySnapshot(
                games = s.games, wins = s.wins, losses = s.losses, draws = s.draws, winStreak = s.winStreak,
                launchCount = s.launchCount, accuracyLog = s.accuracyEntries(),
                wonLevels = s.wonLevels.mapNotNull { it.toIntOrNull() }.toSet(),
                puzzlesSolved = s.puzzlesSolved.mapNotNull { it.toIntOrNull() }.toSet(),
                savedGame = s.savedGame, savedLevel = s.savedLevel, savedSide = s.savedHumanSide,
            )
        }
        return out != DataStoreToRoomMigrator.Outcome.FAILED
    }

    fun onConsentDone() {
        appScope.launch {
            if (settings.current().adsRemoved) {
                appOpenAdManager.markUnavailable()
                return@launch
            }
            adsManager.whenReady {
                appOpenAdManager.load()
                interstitial.preload()
                rewarded.preload()
            }
            if (!adsManager.consentAllowsAds()) appOpenAdManager.markUnavailable()
        }
    }
}
