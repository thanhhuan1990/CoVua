package com.huanhuynh.covua.domain

import android.content.Context
import com.huanhuynh.covua.data.FirebaseHelper

/** Tên sự kiện analytics (snake_case, ≤ 40 ký tự) – một chỗ để tránh gõ sai. */
object Ev {
    const val MIGRATION_FAILED = "migration_failed"
    const val LANGUAGE_PICK = "language_pick"
    const val REVIEW_CARD_CREATED = "review_card_created"
    const val REVIEW_CARD_DONE = "review_card_done"
    const val REVIEW_CARD_GRADUATED = "review_card_graduated"
    const val REVIEW_SESSION_DONE = "review_session_done"
    const val REVIEW_CARD_DROPPED = "review_card_dropped"
    const val PUZZLE_ATTEMPT = "puzzle_attempt"
    const val DAILY_SESSION_START = "daily_session_start"
    const val DAILY_STEP_DONE = "daily_step_done"
    const val DAILY_SESSION_DONE = "daily_session_done"
    const val ONBOARD_SKILL = "onboard_skill"
    const val COACH_TUTORIAL = "coach_tutorial"
    const val GAME_START = "game_start"
    const val GAME_END = "game_end"
    const val DDA_PROMPT = "dda_prompt"
    const val DDA_CHOICE = "dda_choice"
    const val STREAK_DAY = "streak_day"
    const val STREAK_MILESTONE = "streak_milestone"
    const val STREAK_RESUMED = "streak_resumed"
    const val NOTIF_PROMPT = "notif_prompt"
    const val NOTIF_PERMISSION = "notif_permission"
    const val NOTIF_SHOWN = "notif_shown"
    const val NOTIF_OPENED = "notif_opened"
    const val NOTIF_REDUCED = "notif_reduced"
    const val NOTIF_STOPPED = "notif_stopped"
    const val STREAK_REST_USED = "streak_rest_used"
    const val STREAK_PAUSED = "streak_paused"
    const val STREAK_RESET = "streak_reset"
    const val STREAK_HIDDEN = "streak_hidden"
    const val IAR_REQUEST = "iar_request"
    const val DRAW_OFFER = "draw_offer"
    const val PLAY_FROM_POSITION = "play_from_position"
    const val TITLE_UP = "title_up"
    const val SHARE = "share"

    init {
        // bảo đảm quy ước tên (chạy một lần khi nạp lớp ở bản debug cũng không tốn gì)
        listOf(MIGRATION_FAILED, REVIEW_SESSION_DONE, DAILY_SESSION_START).forEach { require(it.length <= 40) }
    }
}

/** Ghi sự kiện qua Firebase (không có Firebase → bỏ qua). */
class EventLogger(private val context: Context) {
    fun log(name: String, params: Map<String, String> = emptyMap()) = FirebaseHelper.logEvent(context, name, params)
}
