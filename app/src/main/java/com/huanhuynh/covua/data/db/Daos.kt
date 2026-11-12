package com.huanhuynh.covua.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Insert suspend fun insert(g: GameEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(g: List<GameEntity>)
    @Update suspend fun update(g: GameEntity)

    @Query("SELECT * FROM game WHERE id = :id") suspend fun get(id: Long): GameEntity?

    @Query("UPDATE game SET moves = :moves, accuracy = :accuracy, undo_count = :undos, hint_count = :hints WHERE id = :id")
    suspend fun updateProgress(id: Long, moves: String, accuracy: Int?, undos: Int, hints: Int)

    @Query("SELECT * FROM game WHERE profile_id = :p AND status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    suspend fun inProgress(p: Long = 1): GameEntity?

    @Query("UPDATE game SET status = 'ABANDONED', ended_at = :at WHERE profile_id = :p AND status = 'IN_PROGRESS' AND id != :keep")
    suspend fun abandonOthers(keep: Long, at: Long, p: Long = 1)

    @Query("SELECT * FROM game WHERE profile_id = :p AND status = 'FINISHED' ORDER BY ended_at DESC LIMIT :n")
    suspend fun recentFinished(n: Int, p: Long = 1): List<GameEntity>

    @Query("SELECT * FROM game WHERE profile_id = :p AND status = 'FINISHED' AND legacy = 0 ORDER BY ended_at DESC LIMIT :n")
    fun recentFinishedFlow(n: Int, p: Long = 1): Flow<List<GameEntity>>

    @Query("SELECT COUNT(*) FROM game WHERE profile_id = :p AND status = 'FINISHED'")
    suspend fun finishedCount(p: Long = 1): Int

    @Query("SELECT COUNT(*) FROM game WHERE profile_id = :p AND status = 'FINISHED' AND legacy = 0 AND id > :afterId")
    suspend fun finishedSince(afterId: Long, p: Long = 1): Int

    /** Cấp cao nhất đã thắng khi KHÔNG chấp (cho danh hiệu). */
    @Query("SELECT MAX(level) FROM game WHERE profile_id = :p AND status = 'FINISHED' AND result = 1 AND (handicap IS NULL OR handicap = 'none') AND mode != 'FROM_POSITION'")
    suspend fun highestLevelWonNoHandicap(p: Long = 1): Int?

    /** Ván mới thứ [keep]+1 trở về trước: tỉa lời thầy cho gọn (giữ nước đi và điểm). */
    @Query(
        "UPDATE move_eval SET coach_title = NULL, coach_text = NULL WHERE coach_text IS NOT NULL AND game_id IN " +
            "(SELECT id FROM game WHERE profile_id = :p AND status = 'FINISHED' ORDER BY ended_at DESC LIMIT -1 OFFSET :keep)",
    )
    suspend fun pruneCoachText(keep: Int = 300, p: Long = 1)
}

@Dao
interface MoveEvalDao {
    @Upsert suspend fun upsert(e: MoveEvalEntity)
    @Query("SELECT * FROM move_eval WHERE game_id = :gameId ORDER BY ply") suspend fun forGame(gameId: Long): List<MoveEvalEntity>
    @Query("SELECT * FROM move_eval WHERE game_id = :gameId AND ply = :ply") suspend fun get(gameId: Long, ply: Int): MoveEvalEntity?
    @Query("DELETE FROM move_eval WHERE game_id = :gameId AND ply >= :fromPly") suspend fun deleteFrom(gameId: Long, fromPly: Int)
}

@Dao
interface ReviewCardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(c: ReviewCardEntity): Long
    @Update suspend fun update(c: ReviewCardEntity)
    @Query("SELECT * FROM review_card WHERE id = :id") suspend fun get(id: Long): ReviewCardEntity?

    @Query("SELECT * FROM review_card WHERE profile_id = :p AND source = :source AND zobrist = :zobrist")
    suspend fun byZobrist(zobrist: Long, source: String = CardSource.MISTAKE, p: Long = 1): ReviewCardEntity?

    @Query("SELECT * FROM review_card WHERE profile_id = :p AND status = 'ACTIVE' AND due_day <= :today ORDER BY due_day, box, id")
    suspend fun due(today: Long, p: Long = 1): List<ReviewCardEntity>

    @Query("SELECT COUNT(*) FROM review_card WHERE profile_id = :p AND status = 'ACTIVE' AND due_day <= :today")
    fun dueCountFlow(today: Long, p: Long = 1): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_card WHERE profile_id = :p AND status = 'ACTIVE' AND due_day <= :today")
    suspend fun dueCount(today: Long, p: Long = 1): Int

    @Query("SELECT * FROM review_card WHERE profile_id = :p AND status != 'SUSPENDED' ORDER BY created_at DESC")
    fun allFlow(p: Long = 1): Flow<List<ReviewCardEntity>>

    @Query("SELECT COUNT(*) FROM review_card WHERE profile_id = :p AND status = 'ACTIVE'")
    suspend fun activeCount(p: Long = 1): Int

    /** Vượt trần: bỏ thẻ đã "Đã sửa" cũ nhất trước, sau đó thẻ ACTIVE cũ nhất. */
    @Query("DELETE FROM review_card WHERE id IN (SELECT id FROM review_card WHERE profile_id = :p AND status = 'GRADUATED' ORDER BY last_reviewed_at LIMIT :n)")
    suspend fun dropOldestGraduated(n: Int, p: Long = 1)

    @Query("DELETE FROM review_card WHERE id IN (SELECT id FROM review_card WHERE profile_id = :p AND status = 'ACTIVE' ORDER BY created_at LIMIT :n)")
    suspend fun dropOldestActive(n: Int, p: Long = 1)
}

@Dao
interface PuzzleDao {
    @Insert suspend fun insertAttempt(a: PuzzleAttemptEntity)
    @Upsert suspend fun upsertProgress(p: PuzzleProgressEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProgressIgnore(p: List<PuzzleProgressEntity>)
    @Query("SELECT * FROM puzzle_progress WHERE profile_id = :p AND puzzle_id = :id") suspend fun progress(id: Int, p: Long = 1): PuzzleProgressEntity?
    @Query("SELECT * FROM puzzle_progress WHERE profile_id = :p") fun progressFlow(p: Long = 1): Flow<List<PuzzleProgressEntity>>
    @Query("SELECT * FROM puzzle_progress WHERE profile_id = :p") suspend fun allProgress(p: Long = 1): List<PuzzleProgressEntity>
    @Query("SELECT * FROM puzzle_attempt WHERE profile_id = :p ORDER BY at DESC LIMIT :n") suspend fun recentAttempts(n: Int, p: Long = 1): List<PuzzleAttemptEntity>
}

@Dao
abstract class ActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertIgnore(a: DailyActivityEntity)
    @Update abstract suspend fun update(a: DailyActivityEntity)
    @Query("SELECT * FROM daily_activity WHERE profile_id = :p AND epoch_day = :day") abstract suspend fun get(day: Long, p: Long = 1): DailyActivityEntity?
    @Query("SELECT * FROM daily_activity WHERE profile_id = :p AND epoch_day = :day") abstract fun dayFlow(day: Long, p: Long = 1): Flow<DailyActivityEntity?>
    @Query("SELECT * FROM daily_activity WHERE profile_id = :p ORDER BY epoch_day") abstract fun allFlow(p: Long = 1): Flow<List<DailyActivityEntity>>
    @Query("SELECT * FROM daily_activity WHERE profile_id = :p ORDER BY epoch_day") abstract suspend fun all(p: Long = 1): List<DailyActivityEntity>
    @Query("SELECT first_open_minute FROM daily_activity WHERE profile_id = :p AND epoch_day >= :fromDay AND first_open_minute IS NOT NULL")
    abstract suspend fun openMinutesSince(fromDay: Long, p: Long = 1): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertFreeze(f: StreakFreezeEntity)
    @Query("SELECT epoch_day_used FROM streak_freeze WHERE profile_id = :p") abstract suspend fun freezeDays(p: Long = 1): List<Long>

    /** Đọc–sửa–ghi trong một giao dịch. */
    @Transaction
    open suspend fun mutate(day: Long, f: (DailyActivityEntity) -> DailyActivityEntity): DailyActivityEntity {
        insertIgnore(DailyActivityEntity(epochDay = day))
        val cur = get(day)!!
        val next = f(cur)
        if (next != cur) update(next)
        return next
    }
}

@Dao
abstract class PlayerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertIgnore(s: PlayerStateEntity)
    @Update abstract suspend fun update(s: PlayerStateEntity)
    @Query("SELECT * FROM player_state WHERE profile_id = :p") abstract suspend fun get(p: Long = 1): PlayerStateEntity?
    @Query("SELECT * FROM player_state WHERE profile_id = :p") abstract fun flow(p: Long = 1): Flow<PlayerStateEntity?>

    @Transaction
    open suspend fun mutate(f: (PlayerStateEntity) -> PlayerStateEntity): PlayerStateEntity {
        insertIgnore(PlayerStateEntity())
        val cur = get()!!
        val next = f(cur)
        if (next != cur) update(next)
        return next
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertLegacy(s: List<LegacyStatEntity>)
    @Query("SELECT * FROM legacy_stats WHERE profile_id = :p") abstract suspend fun legacy(p: Long = 1): List<LegacyStatEntity>
}
