package com.huanhuynh.covua.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Kiểu quân: hình quân cờ vua (Staunton) hoặc chữ cái trên quân tròn (dễ nhận cho người mới). */
enum class PieceStyle { CLASSIC, LETTERS }

/** Bên người chơi cầm: 1 = Trắng, -1 = Đen, 0 = ngẫu nhiên. */
data class AppSettings(
    val level: Int = 3,
    val sideChoice: Int = 1,
    val coachExplain: Boolean = true,
    val coachWarn: Boolean = true,
    val coachHint: Boolean = true,
    val allowUndo: Boolean = true,
    val pieceStyle: PieceStyle = PieceStyle.CLASSIC,
    val showTargets: Boolean = true,
    val flipWhenBlack: Boolean = true,
    val coachFull: Boolean = true,
    val tts: Boolean = false,
    /** Hiện hộp thoại khi có nước tốt hơn. */
    val betterDialog: Boolean = true,
    val sound: Boolean = true,
    val adsRemoved: Boolean = false,
    val launchCount: Int = 0,
    // Ván đang dở
    val savedGame: String = "",
    val savedLevel: Int = 0,
    val savedHumanSide: Int = 1,
    val savedAccuracy: Int = -1,
    // Thống kê
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** "yyyyMMdd:độ chính xác" của các ván gần nhất, cách nhau dấu phẩy. */
    val accuracyLog: String = "",
    val puzzlesSolved: Set<String> = emptySet(),
    /** Các cấp đã từng thắng (id). */
    val wonLevels: Set<String> = emptySet(),
    val winStreak: Int = 0,
    val seenIntro: Boolean = false,
    // ---- 1.1 ----
    /** Đã trả lời / bỏ qua câu hỏi "Bạn chơi cờ vua thế nào?". */
    val skillAsked: Boolean = false,
    /** Chữ to hơn (+20%). */
    val bigText: Boolean = false,
    /** Lựa chọn chấp quân gần nhất (Handicap.id). */
    val handicap: String = "none",
    /** Ẩn Lịch tập (chip + tiến bộ). */
    val hideStreak: Boolean = false,
    /** Thầy nhắc: bật/tắt, giờ (phút trong ngày), theo giờ hay mở app. */
    val notifOn: Boolean = true,
    val notifMinute: Int = -1,
    val notifAuto: Boolean = false,
    /** Chỉ đánh cờ: tắt hết lời thầy, cảnh báo, gợi ý trong ván với máy (vẫn chấm điểm ngầm để xem phân tích sau ván). */
    val playOnly: Boolean = false,
    /** Vừa thua một ván "Chỉ đánh cờ": phải xem 1 quảng cáo trước khi vào ván mới. */
    val lossAdOwed: Boolean = false,
) {
    val hasSavedGame get() = savedGame.isNotEmpty()

    /** Độ chính xác trung bình 7 ngày gần nhất (null nếu chưa có ván). */
    fun accuracy7d(): Int? {
        val cut = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 7L * 86_400_000))
        val vals = accuracyEntries().filter { it.first >= cut }.map { it.second }
        return if (vals.isEmpty()) null else vals.average().toInt()
    }

    fun accuracyEntries(): List<Pair<String, Int>> = accuracyLog.split(',').filter { it.contains(':') }.mapNotNull {
        val (d, a) = it.split(':')
        a.toIntOrNull()?.let { v -> d to v }
    }
}

class SettingsRepository(private val context: Context) {

    private object K {
        val LEVEL = intPreferencesKey("level")
        val SIDE = intPreferencesKey("side_choice")
        val EXPLAIN = booleanPreferencesKey("coach_explain")
        val WARN = booleanPreferencesKey("coach_warn")
        val HINT = booleanPreferencesKey("coach_hint")
        val UNDO = booleanPreferencesKey("allow_undo")
        val STYLE = stringPreferencesKey("piece_style")
        val TARGETS = booleanPreferencesKey("show_targets")
        val FLIP = booleanPreferencesKey("flip_black")
        val FULL = booleanPreferencesKey("coach_full")
        val TTS = booleanPreferencesKey("tts")
        val BETTER = booleanPreferencesKey("better_dialog")
        val SOUND = booleanPreferencesKey("sound")
        val ADS_REMOVED = booleanPreferencesKey("adsRemoved")
        val LAUNCH = intPreferencesKey("launch_count")
        val SAVED = stringPreferencesKey("saved_game")
        val SAVED_LEVEL = intPreferencesKey("saved_level")
        val SAVED_SIDE = intPreferencesKey("saved_side")
        val SAVED_ACC = intPreferencesKey("saved_acc")
        val GAMES = intPreferencesKey("games")
        val WINS = intPreferencesKey("wins")
        val LOSSES = intPreferencesKey("losses")
        val DRAWS = intPreferencesKey("draws")
        val ACC_LOG = stringPreferencesKey("acc_log")
        val PUZZLES = stringSetPreferencesKey("puzzles_solved")
        val WON_LEVELS = stringSetPreferencesKey("won_levels")
        val STREAK = intPreferencesKey("win_streak")
        val INTRO = booleanPreferencesKey("seen_intro")
        val SKILL_ASKED = booleanPreferencesKey("skill_asked")
        val BIG_TEXT = booleanPreferencesKey("big_text")
        val HANDICAP = stringPreferencesKey("handicap")
        val HIDE_STREAK = booleanPreferencesKey("hide_streak")
        val NOTIF_ON = booleanPreferencesKey("notif_on")
        val NOTIF_MINUTE = intPreferencesKey("notif_minute")
        val NOTIF_AUTO = booleanPreferencesKey("notif_auto")
        val PLAY_ONLY = booleanPreferencesKey("play_only")
        val LOSS_AD = booleanPreferencesKey("loss_ad_owed")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            level = p[K.LEVEL] ?: 3,
            sideChoice = p[K.SIDE] ?: 1,
            coachExplain = p[K.EXPLAIN] ?: true,
            coachWarn = p[K.WARN] ?: true,
            coachHint = p[K.HINT] ?: true,
            allowUndo = p[K.UNDO] ?: true,
            pieceStyle = runCatching { PieceStyle.valueOf(p[K.STYLE] ?: "CLASSIC") }.getOrDefault(PieceStyle.CLASSIC),
            showTargets = p[K.TARGETS] ?: true,
            flipWhenBlack = p[K.FLIP] ?: true,
            coachFull = p[K.FULL] ?: true,
            tts = p[K.TTS] ?: false,
            betterDialog = p[K.BETTER] ?: true,
            sound = p[K.SOUND] ?: true,
            adsRemoved = p[K.ADS_REMOVED] ?: false,
            launchCount = p[K.LAUNCH] ?: 0,
            savedGame = p[K.SAVED] ?: "",
            savedLevel = p[K.SAVED_LEVEL] ?: 0,
            savedHumanSide = p[K.SAVED_SIDE] ?: 1,
            savedAccuracy = p[K.SAVED_ACC] ?: -1,
            games = p[K.GAMES] ?: 0,
            wins = p[K.WINS] ?: 0,
            losses = p[K.LOSSES] ?: 0,
            draws = p[K.DRAWS] ?: 0,
            accuracyLog = p[K.ACC_LOG] ?: "",
            puzzlesSolved = p[K.PUZZLES] ?: emptySet(),
            wonLevels = p[K.WON_LEVELS] ?: emptySet(),
            winStreak = p[K.STREAK] ?: 0,
            seenIntro = p[K.INTRO] ?: false,
            skillAsked = p[K.SKILL_ASKED] ?: false,
            bigText = p[K.BIG_TEXT] ?: false,
            handicap = p[K.HANDICAP] ?: "none",
            hideStreak = p[K.HIDE_STREAK] ?: false,
            notifOn = p[K.NOTIF_ON] ?: true,
            notifMinute = p[K.NOTIF_MINUTE] ?: -1,
            notifAuto = p[K.NOTIF_AUTO] ?: false,
            playOnly = p[K.PLAY_ONLY] ?: false,
            lossAdOwed = p[K.LOSS_AD] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun incrementLaunchCount(): Int {
        var v = 0
        context.dataStore.edit { p -> v = (p[K.LAUNCH] ?: 0) + 1; p[K.LAUNCH] = v }
        return v
    }

    suspend fun setNewGamePrefs(level: Int, side: Int, explain: Boolean, warn: Boolean, hint: Boolean, undo: Boolean) =
        context.dataStore.edit {
            it[K.LEVEL] = level; it[K.SIDE] = side; it[K.EXPLAIN] = explain; it[K.WARN] = warn; it[K.HINT] = hint; it[K.UNDO] = undo
        }

    suspend fun setPieceStyle(v: PieceStyle) = context.dataStore.edit { it[K.STYLE] = v.name }
    suspend fun setShowTargets(v: Boolean) = context.dataStore.edit { it[K.TARGETS] = v }
    suspend fun setFlip(v: Boolean) = context.dataStore.edit { it[K.FLIP] = v }
    suspend fun setCoachFull(v: Boolean) = context.dataStore.edit { it[K.FULL] = v }
    suspend fun setTts(v: Boolean) = context.dataStore.edit { it[K.TTS] = v }
    suspend fun setBetterDialog(v: Boolean) = context.dataStore.edit { it[K.BETTER] = v }
    suspend fun setSound(v: Boolean) = context.dataStore.edit { it[K.SOUND] = v }
    suspend fun setAdsRemoved(v: Boolean) = context.dataStore.edit { it[K.ADS_REMOVED] = v }
    suspend fun setSeenIntro() = context.dataStore.edit { it[K.INTRO] = true }

    suspend fun setSkillAsked() = context.dataStore.edit { it[K.SKILL_ASKED] = true }
    suspend fun setBigText(v: Boolean) = context.dataStore.edit { it[K.BIG_TEXT] = v }
    suspend fun setHandicap(id: String) = context.dataStore.edit { it[K.HANDICAP] = id }
    suspend fun setHideStreak(v: Boolean) = context.dataStore.edit { it[K.HIDE_STREAK] = v }
    suspend fun setNotif(on: Boolean, minute: Int, auto: Boolean) = context.dataStore.edit {
        it[K.NOTIF_ON] = on; it[K.NOTIF_MINUTE] = minute; it[K.NOTIF_AUTO] = auto
    }
    suspend fun setPlayOnly(v: Boolean) = context.dataStore.edit { it[K.PLAY_ONLY] = v }
    suspend fun setLossAdOwed(v: Boolean) = context.dataStore.edit { it[K.LOSS_AD] = v }
    suspend fun setLevel(level: Int) = context.dataStore.edit { it[K.LEVEL] = level }

    // ---- Khoá phụ (trạng thái nội bộ, không hiện trong AppSettings) ----
    suspend fun getInt(key: String, def: Int = 0): Int = context.dataStore.data.first()[intPreferencesKey(key)] ?: def
    suspend fun setInt(key: String, v: Int) = context.dataStore.edit { it[intPreferencesKey(key)] = v }
    suspend fun getLongKey(key: String, def: Long = 0L): Long = context.dataStore.data.first()[longPreferencesKey(key)] ?: def
    suspend fun setLongKey(key: String, v: Long) = context.dataStore.edit { it[longPreferencesKey(key)] = v }
    suspend fun getStr(key: String): String? = context.dataStore.data.first()[stringPreferencesKey(key)]
    suspend fun setStr(key: String, v: String) = context.dataStore.edit { it[stringPreferencesKey(key)] = v }

    suspend fun saveGame(serialized: String, level: Int, humanSide: Int, accuracy: Int) = context.dataStore.edit {
        it[K.SAVED] = serialized; it[K.SAVED_LEVEL] = level; it[K.SAVED_SIDE] = humanSide; it[K.SAVED_ACC] = accuracy
    }

    suspend fun clearSavedGame() = context.dataStore.edit { it.remove(K.SAVED); it.remove(K.SAVED_ACC) }

    /** result: 1 thắng, -1 thua, 0 hoà. */
    suspend fun recordGame(result: Int, accuracy: Int?, level: Int) = context.dataStore.edit { p ->
        p[K.GAMES] = (p[K.GAMES] ?: 0) + 1
        p[K.STREAK] = if (result == 1) (p[K.STREAK] ?: 0) + 1 else 0
        if (result == 1) p[K.WON_LEVELS] = (p[K.WON_LEVELS] ?: emptySet()) + level.toString()
        when (result) {
            1 -> p[K.WINS] = (p[K.WINS] ?: 0) + 1
            -1 -> p[K.LOSSES] = (p[K.LOSSES] ?: 0) + 1
            else -> p[K.DRAWS] = (p[K.DRAWS] ?: 0) + 1
        }
        if (accuracy != null) {
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val log = ((p[K.ACC_LOG] ?: "").split(',').filter { it.isNotBlank() } + "$day:$accuracy").takeLast(60)
            p[K.ACC_LOG] = log.joinToString(",")
        }
        p.remove(K.SAVED); p.remove(K.SAVED_ACC)
    }

    suspend fun markPuzzleSolved(id: Int) = context.dataStore.edit { p ->
        p[K.PUZZLES] = (p[K.PUZZLES] ?: emptySet()) + id.toString()
    }
}
