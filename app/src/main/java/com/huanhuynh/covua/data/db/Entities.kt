package com.huanhuynh.covua.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * user.db v1 – quy ước:
 *  - mọi bảng có profile_id (mặc định 1) để sau này có nhiều hồ sơ;
 *  - ngày lưu bằng epoch_day theo giờ máy (DayClock), mốc thời gian *_at bằng mili-giây;
 *  - enum lưu dạng chữ (đọc được khi debug, đổi thứ tự không hỏng dữ liệu).
 */

object GameStatus { const val IN_PROGRESS = "IN_PROGRESS"; const val FINISHED = "FINISHED"; const val ABANDONED = "ABANDONED" }
object GameMode { const val PRACTICE = "PRACTICE"; const val HANDICAP = "HANDICAP"; const val FROM_POSITION = "FROM_POSITION" }
object EndReason {
    const val MATE = "MATE"; const val RESIGN = "RESIGN"; const val AGREED = "AGREED"
    const val REPETITION = "REPETITION"; const val NO_CAPTURE = "NO_CAPTURE"; const val MATERIAL = "MATERIAL"; const val OTHER = "OTHER"
}

@Entity(
    tableName = "game",
    indices = [
        Index(value = ["profile_id", "ended_at"]),
        Index(value = ["profile_id", "status"]),
        Index(value = ["profile_id", "level"]),
    ],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    val status: String,
    val mode: String,
    val level: Int,
    /** Handicap.id (null = không chấp). */
    val handicap: String? = null,
    @ColumnInfo(name = "player_side") val playerSide: Int,
    @ColumnInfo(name = "start_fen") val startFen: String,
    /** Các nước (mã số int) cách nhau dấu cách. */
    val moves: String = "",
    /** 1 thắng, 0 hoà, -1 thua (góc người chơi); null khi chưa xong. */
    val result: Int? = null,
    @ColumnInfo(name = "end_reason") val endReason: String? = null,
    val accuracy: Int? = null,
    @ColumnInfo(name = "undo_count", defaultValue = "0") val undoCount: Int = 0,
    @ColumnInfo(name = "hint_count", defaultValue = "0") val hintCount: Int = 0,
    @ColumnInfo(name = "opening_id") val openingId: String? = null,
    @ColumnInfo(name = "engine_version") val engineVersion: Int,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val legacy: Boolean = false,
) {
    val plies: Int get() = moveList().size
    fun moveList(): List<Int> = moves.split(' ', ',').mapNotNull { it.trim().toIntOrNull() }

    companion object {
        fun encodeMoves(moves: List<Int>) = moves.joinToString(" ")
    }
}

@Entity(
    tableName = "move_eval",
    primaryKeys = ["game_id", "ply"],
    indices = [Index(value = ["game_id"])],
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["game_id"], onDelete = ForeignKey.CASCADE)],
)
data class MoveEvalEntity(
    @ColumnInfo(name = "game_id") val gameId: Long,
    val ply: Int,
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    val move: Int,
    /** Ký hiệu nước (P2-5…). */
    val code: String,
    @ColumnInfo(name = "best_move") val bestMove: Int,
    @ColumnInfo(name = "second_best_score") val secondBestScore: Int? = null,
    @ColumnInfo(name = "score_before") val scoreBefore: Int,
    @ColumnInfo(name = "score_after") val scoreAfter: Int,
    @ColumnInfo(name = "loss_cp") val lossCp: Int,
    /** Độ chính xác của nước 0–100. */
    @ColumnInfo(defaultValue = "100") val accuracy: Int = 100,
    /** Grade.ordinal */
    val label: Int,
    /** CoachTags bitmask */
    val tags: Int,
    /** Chỉ lưu cho nước của người chơi từ "Chưa chính xác" trở xuống; tỉa sau 300 ván. */
    @ColumnInfo(name = "coach_title") val coachTitle: String? = null,
    @ColumnInfo(name = "coach_text") val coachText: String? = null,
)

object CardSource { const val MISTAKE = "MISTAKE" }
object CardStatusDb { const val ACTIVE = "ACTIVE"; const val GRADUATED = "GRADUATED"; const val SUSPENDED = "SUSPENDED" }

@Entity(
    tableName = "review_card",
    indices = [
        Index(value = ["profile_id", "source", "zobrist"], unique = true),
        Index(value = ["profile_id", "status", "due_day"]),
    ],
)
data class ReviewCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    val source: String = CardSource.MISTAKE,
    val fen: String,
    val zobrist: Long,
    @ColumnInfo(name = "played_move") val playedMove: Int,
    /** Các nước chấp nhận được, cách nhau dấu phẩy (nước tốt nhất đứng đầu). */
    @ColumnInfo(name = "best_moves") val bestMoves: String,
    @ColumnInfo(name = "accept_loss_cp", defaultValue = "50") val acceptLossCp: Int = 50,
    /** CoachTags bitmask */
    @ColumnInfo(name = "theme_tags") val themeTags: Int,
    val box: Int,
    @ColumnInfo(name = "due_day") val dueDay: Long,
    val reps: Int = 0,
    val lapses: Int = 0,
    val status: String = CardStatusDb.ACTIVE,
    @ColumnInfo(name = "game_id") val gameId: Long? = null,
    val ply: Int? = null,
    /** Cấp máy của ván gốc (để hiện "Ván ngày 12/11 (cấp 4)"). */
    val level: Int = 0,
    @ColumnInfo(name = "engine_version") val engineVersion: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long? = null,
) {
    fun bestMoveList(): List<Int> = bestMoves.split(',').mapNotNull { it.toIntOrNull() }
}

object PuzzleMode { const val DAILY = "DAILY"; const val BROWSE = "BROWSE"; const val SESSION = "SESSION"; const val REVIEW = "REVIEW" }

@Entity(tableName = "puzzle_attempt", indices = [Index(value = ["profile_id", "puzzle_id"]), Index(value = ["profile_id", "at"])])
data class PuzzleAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    @ColumnInfo(name = "puzzle_id") val puzzleId: Int,
    val mode: String,
    val success: Boolean,
    @ColumnInfo(name = "hint_level") val hintLevel: Int,
    @ColumnInfo(name = "lives_left") val livesLeft: Int? = null,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    val at: Long,
)

@Entity(tableName = "puzzle_progress", primaryKeys = ["profile_id", "puzzle_id"])
data class PuzzleProgressEntity(
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    @ColumnInfo(name = "puzzle_id") val puzzleId: Int,
    val solved: Boolean,
    @ColumnInfo(name = "first_solved_at") val firstSolvedAt: Long?,
    @ColumnInfo(name = "best_lives") val bestLives: Int? = null,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
)

@Entity(tableName = "daily_activity", primaryKeys = ["profile_id", "epoch_day"])
data class DailyActivityEntity(
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "games_counted", defaultValue = "0") val gamesCounted: Int = 0,
    @ColumnInfo(defaultValue = "0") val puzzles: Int = 0,
    @ColumnInfo(defaultValue = "0") val reviews: Int = 0,
    @ColumnInfo(name = "daily_puzzle_done", defaultValue = "0") val dailyPuzzleDone: Boolean = false,
    /** Lives còn lại khi giải bài của thầy (5 = ★). */
    @ColumnInfo(name = "daily_lives") val dailyLives: Int? = null,
    @ColumnInfo(name = "session_parts_done", defaultValue = "0") val sessionPartsDone: Int = 0,
    @ColumnInfo(name = "first_open_minute") val firstOpenMinute: Int? = null,
) {
    /** Ngày được tính vào Lịch tập. */
    val counts: Boolean get() = gamesCounted > 0 || dailyPuzzleDone || reviews > 0
}

@Entity(tableName = "streak_freeze", indices = [Index(value = ["profile_id", "epoch_day_used"], unique = true)])
data class StreakFreezeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    @ColumnInfo(name = "epoch_day_used") val epochDayUsed: Long,
    val source: String = "AUTO",
)

@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: Long = 1,
    @ColumnInfo(name = "puzzle_band", defaultValue = "2") val puzzleBand: Int = 2,
    @ColumnInfo(name = "band_ok_streak", defaultValue = "0") val bandOkStreak: Int = 0,
    @ColumnInfo(name = "band_fail_streak", defaultValue = "0") val bandFailStreak: Int = 0,
    @ColumnInfo(name = "skill_answer") val skillAnswer: Int? = null,
    @ColumnInfo(name = "dda_last_prompt_game_id") val ddaLastPromptGameId: Long? = null,
    @ColumnInfo(name = "dda_declined_until_game") val ddaDeclinedUntilGame: Int? = null,
    @ColumnInfo(name = "title_id") val titleId: Int? = null,
    @ColumnInfo(name = "best_streak", defaultValue = "0") val bestStreak: Int = 0,
)

@Entity(tableName = "legacy_stats", primaryKeys = ["profile_id", "key"])
data class LegacyStatEntity(
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1,
    val key: String,
    val value: String,
)
