package com.huanhuynh.covua.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/** Khóa Remote Config + giá trị mặc định (dùng khi chưa có Firebase). */
object RcKeys {
    const val INTERSTITIAL_MIN_INTERVAL_SEC = "interstitial_min_interval_sec"
    const val INTERSTITIAL_MIN_ACTIONS = "interstitial_min_actions"
    const val INTERSTITIAL_MAX_PER_DAY = "interstitial_max_per_day"
    const val INTERSTITIAL_AFTER_APP_OPEN_SEC = "interstitial_after_app_open_sec"
    const val NEW_USER_GRACE_SEC = "new_user_grace_sec"
    const val APP_OPEN_MIN_INTERVAL_SEC = "app_open_min_interval_sec"
    const val APP_OPEN_MAX_PER_DAY = "app_open_max_per_day"
    const val APP_OPEN_MIN_BACKGROUND_SEC = "app_open_min_background_sec"
    const val SPLASH_MAX_WAIT_MS = "splash_max_wait_ms"
    /** 1 = hiện mục "Ứng dụng khác của chúng tôi" (XPromo), 0 = ẩn. */
    const val XPROMO_ENABLED = "xpromo_enabled"
    /** Số gợi ý miễn phí giữa 2 lần mời xem quảng cáo có thưởng (0 = tắt quảng cáo ở gợi ý). */
    const val HINT_FREE_BETWEEN_ADS = "hint_free_between_ads"
    /** Số lần "đi lại" (từ hộp thoại nước tốt hơn) miễn phí giữa 2 lần mời xem quảng cáo (0 = tắt). */
    const val RETRY_FREE_BETWEEN_ADS = "retry_free_between_ads"
    /** 1 = thử hiện interstitial khi bắt đầu ván mới (vẫn theo các giới hạn tần suất). */
    const val INTERSTITIAL_ON_NEW_GAME = "interstitial_on_new_game"

    // ---- 1.1 "Thầy giao bài mỗi ngày" (mọi tính năng có cờ) ----
    /** 1 = nhóm giữ lại (đặt bằng điều kiện "ngẫu nhiên 10%" trên console): tắt Buổi tập, Lịch tập, Thầy nhắc, gợi ý cấp độ. */
    const val V11_HOLDOUT = "v11_holdout"
    const val REVIEW_ENABLED = "review_enabled"
    const val REVIEW_MIN_LEVEL = "review_min_level"
    const val REVIEW_MIN_LOSS_CP = "review_min_loss_cp"
    const val REVIEW_MAX_CARDS_PER_GAME = "review_max_cards_per_game"
    const val REVIEW_MAX_OK_MOVES = "review_max_ok_moves"
    const val REVIEW_INTERVALS_DAYS = "review_intervals_days" // chuỗi
    const val REVIEW_VERIFY_DEPTH = "review_verify_depth"
    const val PUZZLE_INTERSTITIAL_EVERY = "puzzle_interstitial_every"
    const val PUZZLE_BAND_UP_AFTER = "puzzle_band_up_after"
    const val PUZZLE_BAND_DOWN_AFTER = "puzzle_band_down_after"
    const val DAILY_SESSION_ENABLED = "daily_session_enabled"
    const val DAILY_REVIEW_CARDS = "daily_review_cards"
    const val DAILY_EXTRA_PUZZLES = "daily_extra_puzzles"
    const val DAILY_LIVES = "daily_lives"
    const val DAILY_DIFFICULTY_BY_WEEKDAY = "daily_difficulty_by_weekday" // chuỗi
    const val INTERSTITIAL_AFTER_DAILY_SESSION = "interstitial_after_daily_session"
    const val HANDICAP_ENABLED = "handicap_enabled"
    const val DDA_ENABLED = "dda_enabled"
    const val DDA_LOSS_STREAK = "dda_loss_streak"
    const val DDA_WIN_STREAK = "dda_win_streak"
    const val DDA_COOLDOWN_GAMES = "dda_cooldown_games"
    const val ONBOARD_SKILL_QUESTION = "onboard_skill_question"
    const val STREAK_ENABLED = "streak_enabled"
    const val STREAK_FREE_REST_DAYS_WEEK = "streak_free_rest_days_week"
    const val STREAK_PAUSE_DAYS = "streak_pause_days"
    const val STREAK_MIN_MOVES = "streak_min_moves"
    const val STREAK_AD_REPAIR_ENABLED = "streak_ad_repair_enabled"
    const val NOTIF_ENABLED = "notif_enabled"
    const val NOTIF_DEFAULT_HOUR = "notif_default_hour"
    const val NOTIF_QUIET_START = "notif_quiet_start"
    const val NOTIF_QUIET_END = "notif_quiet_end" // chuỗi số thực, ví dụ "7.5"
    const val NOTIF_IGNORE_REDUCE_AFTER = "notif_ignore_reduce_after"
    const val NOTIF_IGNORE_STOP_AFTER = "notif_ignore_stop_after"
    const val NOTIF_OPEN_AD_GRACE_SEC = "notif_open_ad_grace_sec"
    const val IAR_ENABLED = "iar_enabled"
    const val DRAW_OFFER_ENABLED = "draw_offer_enabled"
    const val PLAY_FROM_POSITION_ENABLED = "play_from_position_enabled"
    const val TITLES_ENABLED = "titles_enabled"
    /** IAR: số ván tối thiểu trước khi hỏi đánh giá. */
    const val REVIEW_PROMPT_MIN_GAMES = "review_prompt_min_games"

    val STRING_DEFAULTS: Map<String, String> = mapOf(
        REVIEW_INTERVALS_DAYS to "1,3,7,16,35",
        DAILY_DIFFICULTY_BY_WEEKDAY to "1,1,2,2,2,2,3",
        NOTIF_QUIET_END to "7.5",
    )

    val DEFAULTS: Map<String, Long> = mapOf(
        V11_HOLDOUT to 0L,
        REVIEW_ENABLED to 1L,
        REVIEW_MIN_LEVEL to 2L,
        REVIEW_MIN_LOSS_CP to 150L,
        REVIEW_MAX_CARDS_PER_GAME to 3L,
        REVIEW_MAX_OK_MOVES to 3L,
        REVIEW_VERIFY_DEPTH to 6L,
        PUZZLE_INTERSTITIAL_EVERY to 3L,
        PUZZLE_BAND_UP_AFTER to 3L,
        PUZZLE_BAND_DOWN_AFTER to 2L,
        DAILY_SESSION_ENABLED to 1L,
        DAILY_REVIEW_CARDS to 3L,
        DAILY_EXTRA_PUZZLES to 2L,
        DAILY_LIVES to 5L,
        INTERSTITIAL_AFTER_DAILY_SESSION to 1L,
        HANDICAP_ENABLED to 1L,
        DDA_ENABLED to 1L,
        DDA_LOSS_STREAK to 3L,
        DDA_WIN_STREAK to 3L,
        DDA_COOLDOWN_GAMES to 5L,
        ONBOARD_SKILL_QUESTION to 1L,
        STREAK_ENABLED to 1L,
        STREAK_FREE_REST_DAYS_WEEK to 2L,
        STREAK_PAUSE_DAYS to 3L,
        STREAK_MIN_MOVES to 10L,
        STREAK_AD_REPAIR_ENABLED to 0L,
        NOTIF_ENABLED to 1L,
        NOTIF_DEFAULT_HOUR to 19L,
        NOTIF_QUIET_START to 21L,
        NOTIF_IGNORE_REDUCE_AFTER to 3L,
        NOTIF_IGNORE_STOP_AFTER to 7L,
        NOTIF_OPEN_AD_GRACE_SEC to 90L,
        IAR_ENABLED to 1L,
        DRAW_OFFER_ENABLED to 1L,
        PLAY_FROM_POSITION_ENABLED to 1L,
        TITLES_ENABLED to 1L,
        REVIEW_PROMPT_MIN_GAMES to 3L,
        INTERSTITIAL_MIN_INTERVAL_SEC to 180L,
        INTERSTITIAL_MIN_ACTIONS to 3L,
        INTERSTITIAL_MAX_PER_DAY to 4L,
        INTERSTITIAL_AFTER_APP_OPEN_SEC to 60L,
        NEW_USER_GRACE_SEC to 120L,
        APP_OPEN_MIN_INTERVAL_SEC to 240L,
        APP_OPEN_MAX_PER_DAY to 6L,
        APP_OPEN_MIN_BACKGROUND_SEC to 30L,
        SPLASH_MAX_WAIT_MS to 3000L,
        XPROMO_ENABLED to 1L,
        HINT_FREE_BETWEEN_ADS to 2L,
        RETRY_FREE_BETWEEN_ADS to 2L,
        INTERSTITIAL_ON_NEW_GAME to 1L,
    )
}

/**
 * Mọi lời gọi Firebase đi qua đây và được bọc runCatching:
 * app vẫn chạy bình thường khi chưa có google-services.json.
 */
object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    fun isAvailable(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    fun init(context: Context) {
        if (!isAvailable(context)) {
            Log.i(TAG, "Firebase chưa được cấu hình – dùng giá trị mặc định")
            return
        }
        runCatching {
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600).build(),
            )
            rc.setDefaultsAsync(RcKeys.DEFAULTS.mapValues { it.value as Any } + RcKeys.STRING_DEFAULTS)
            rc.fetchAndActivate()
        }.onFailure { Log.w(TAG, "Remote Config lỗi", it) }
    }

    fun getLong(context: Context, key: String): Long {
        val fallback = RcKeys.DEFAULTS[key] ?: 0L
        if (!isAvailable(context)) return fallback
        return runCatching {
            val rc = FirebaseRemoteConfig.getInstance()
            val value = rc.getValue(key)
            if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) fallback else value.asLong()
        }.getOrDefault(fallback)
    }

    fun getString(context: Context, key: String): String {
        val fallback = RcKeys.STRING_DEFAULTS[key] ?: ""
        if (!isAvailable(context)) return fallback
        return runCatching {
            val value = FirebaseRemoteConfig.getInstance().getValue(key)
            if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) fallback else value.asString()
        }.getOrDefault(fallback)
    }

    fun flag(context: Context, key: String): Boolean = getLong(context, key) != 0L

    /** Tính năng 1.1 thuộc nhóm giữ lại (F3, F5, F6, phần gợi ý cấp độ của F4) bị tắt khi v11_holdout = 1. */
    fun v11(context: Context, key: String): Boolean = flag(context, key) && !flag(context, RcKeys.V11_HOLDOUT)

    fun logEvent(context: Context, name: String, params: Map<String, String> = emptyMap()) {
        if (!isAvailable(context)) return
        runCatching {
            val bundle = Bundle().apply { params.forEach { (k, v) -> putString(k, v) } }
            FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
        }
    }

    fun recordException(context: Context, t: Throwable) {
        if (!isAvailable(context)) return
        runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
    }
}
