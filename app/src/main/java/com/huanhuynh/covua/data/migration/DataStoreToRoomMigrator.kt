package com.huanhuynh.covua.data.migration

import com.huanhuynh.covua.data.db.DailyActivityEntity
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.data.db.GameMode
import com.huanhuynh.covua.data.db.GameStatus
import com.huanhuynh.covua.data.db.LegacyStatEntity
import com.huanhuynh.covua.data.db.PuzzleProgressEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ảnh chụp dữ liệu 1.0 trong DataStore (chỉ những gì cần chuyển). */
data class LegacySnapshot(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val winStreak: Int = 0,
    val launchCount: Int = 0,
    /** (yyyyMMdd, độ chính xác) – tối đa 60 ván gần nhất. */
    val accuracyLog: List<Pair<String, Int>> = emptyList(),
    val wonLevels: Set<Int> = emptySet(),
    val puzzlesSolved: Set<Int> = emptySet(),
    /** Ván đang dở dạng Game.serialize() (rỗng = không có). */
    val savedGame: String = "",
    val savedLevel: Int = 0,
    val savedSide: Int = 1,
)

/** Đích ghi (Room thật hoặc bộ giả trong test). Mọi hàm insert là INSERT OR IGNORE. */
interface MigrationTarget {
    suspend fun <T> inTransaction(block: suspend () -> T): T
    suspend fun insertGames(games: List<GameEntity>)
    suspend fun hasInProgressGame(): Boolean
    suspend fun insertGame(game: GameEntity)
    suspend fun insertStats(stats: List<LegacyStatEntity>)
    suspend fun insertPuzzleProgress(list: List<PuzzleProgressEntity>)
    suspend fun insertActivity(list: List<DailyActivityEntity>)
}

/** Cờ phiên bản lưu ở DataStore. */
interface MigrationFlag {
    suspend fun version(): Int
    suspend fun setVersion(v: Int)
}

/**
 * Chuyển dữ liệu 1.0 (DataStore) sang Room một lần.
 * - Bỏ qua nếu đã chạy (cờ ≥ [VERSION]).
 * - Trong MỘT giao dịch; mọi insert là "OR IGNORE" với khoá cố định → chạy lại không nhân đôi.
 * - Chỉ đặt cờ SAU khi giao dịch xong. Lỗi → báo [onError] (event migration_failed{step}), app tiếp tục đọc DataStore.
 * - Không xoá khoá cũ trong DataStore.
 */
class DataStoreToRoomMigrator(
    private val target: MigrationTarget,
    private val flag: MigrationFlag,
    private val engineVersion: Int,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val onError: (step: String, Throwable) -> Unit = { _, _ -> },
) {
    enum class Outcome { SKIPPED, MIGRATED, FAILED }

    suspend fun run(snapshot: suspend () -> LegacySnapshot): Outcome {
        var step = "flag"
        return try {
            if (flag.version() >= VERSION) return Outcome.SKIPPED
            step = "read"
            val s = snapshot()
            target.inTransaction {
                step = "games"; target.insertGames(legacyGames(s))
                step = "activity"; target.insertActivity(activityDays(s))
                step = "stats"; target.insertStats(stats(s))
                step = "puzzles"; target.insertPuzzleProgress(s.puzzlesSolved.map { PuzzleProgressEntity(puzzleId = it, solved = true, firstSolvedAt = null) })
                step = "saved_game"; savedGame(s)?.let { if (!target.hasInProgressGame()) target.insertGame(it) }
            }
            step = "flag_write"
            flag.setVersion(VERSION)
            Outcome.MIGRATED
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            onError(step, t)
            Outcome.FAILED
        }
    }

    private fun dayOf(yyyymmdd: String): LocalDate? = runCatching { LocalDate.parse(yyyymmdd, FMT) }.getOrNull()

    /** 60 ván trong nhật ký độ chính xác → game(legacy=true), id cố định 1..n để chạy lại vẫn IGNORE. */
    fun legacyGames(s: LegacySnapshot): List<GameEntity> = s.accuracyLog.mapIndexedNotNull { i, (d, acc) ->
        val day = dayOf(d) ?: return@mapIndexedNotNull null
        val at = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() + i // giữ thứ tự trong ngày
        GameEntity(
            id = (i + 1).toLong(), status = GameStatus.FINISHED, mode = GameMode.PRACTICE, level = 0,
            playerSide = 1, startFen = "", accuracy = acc, engineVersion = 0,
            startedAt = at, endedAt = at, legacy = true,
        )
    }

    /** Những ngày có ván trong nhật ký → daily_activity (để Lịch tập không mất ngày cũ). */
    fun activityDays(s: LegacySnapshot): List<DailyActivityEntity> =
        s.accuracyLog.mapNotNull { dayOf(it.first)?.toEpochDay() }.groupingBy { it }.eachCount()
            .map { (day, n) -> DailyActivityEntity(epochDay = day, gamesCounted = n) }

    fun stats(s: LegacySnapshot): List<LegacyStatEntity> = listOf(
        "games" to s.games, "wins" to s.wins, "losses" to s.losses, "draws" to s.draws,
        "win_streak" to s.winStreak, "launch_count" to s.launchCount,
    ).map { LegacyStatEntity(key = it.first, value = it.second.toString()) } +
        LegacyStatEntity(key = "won_levels", value = s.wonLevels.sorted().joinToString(","))

    fun savedGame(s: LegacySnapshot): GameEntity? {
        if (s.savedGame.isBlank()) return null
        val parts = s.savedGame.split('|', limit = 2)
        if (parts.size != 2) return null
        val t = now()
        return GameEntity(
            status = GameStatus.IN_PROGRESS, mode = GameMode.PRACTICE, level = s.savedLevel.coerceIn(1, 8),
            playerSide = s.savedSide, startFen = parts[0], moves = parts[1].split(',').filter { it.isNotBlank() }.joinToString(" "), engineVersion = engineVersion,
            startedAt = t,
        )
    }

    companion object {
        const val VERSION = 1
        private val FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
