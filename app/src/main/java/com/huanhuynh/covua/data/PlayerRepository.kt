package com.huanhuynh.covua.data

import com.huanhuynh.covua.data.db.PlayerStateEntity
import com.huanhuynh.covua.data.db.PuzzleAttemptEntity
import com.huanhuynh.covua.data.db.PuzzleProgressEntity
import com.huanhuynh.covua.data.db.UserDb
import com.huanhuynh.covua.training.BandState
import com.huanhuynh.covua.training.BandTracker
import com.huanhuynh.covua.training.Titles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Trạng thái người chơi (trình độ bài tập, câu hỏi trình độ, gợi ý cấp độ, danh hiệu) + tiến độ bài tập. */
class PlayerRepository(private val db: UserDb, private val now: () -> Long = System::currentTimeMillis) {

    fun flow(): Flow<PlayerStateEntity> = db.player().flow().map { it ?: PlayerStateEntity() }
    suspend fun get(): PlayerStateEntity = db.player().get() ?: PlayerStateEntity()
    suspend fun update(f: (PlayerStateEntity) -> PlayerStateEntity) = db.player().mutate(f)

    // ---- Bài tập ----
    fun solvedFlow(): Flow<Set<Int>> = db.puzzles().progressFlow().map { l -> l.filter { it.solved }.map { it.puzzleId }.toSet() }
    fun progressFlow(): Flow<List<PuzzleProgressEntity>> = db.puzzles().progressFlow()
    suspend fun progress(): List<PuzzleProgressEntity> = db.puzzles().allProgress()
    suspend fun recentAttempts(n: Int) = db.puzzles().recentAttempts(n)

    /**
     * Ghi một lần giải. [failed] = bấm Làm lại hoặc xem gợi ý mức 3. Trả trạng thái band mới.
     * Band chỉ đổi theo bài "Bài vừa sức"/Buổi tập (source != BROWSE thì vẫn tính – mọi bài đều phản ánh trình độ).
     */
    suspend fun recordAttempt(
        puzzleId: Int, mode: String, success: Boolean, hintLevel: Int, livesLeft: Int?, timeMs: Long,
        failed: Boolean, tracker: BandTracker,
    ): PlayerStateEntity {
        val t = now()
        db.puzzles().insertAttempt(PuzzleAttemptEntity(puzzleId = puzzleId, mode = mode, success = success, hintLevel = hintLevel, livesLeft = livesLeft, timeMs = timeMs, at = t))
        val old = db.puzzles().progress(puzzleId)
        val solved = success || old?.solved == true
        db.puzzles().upsertProgress(
            PuzzleProgressEntity(
                puzzleId = puzzleId, solved = solved,
                firstSolvedAt = old?.firstSolvedAt ?: if (success) t else null,
                bestLives = listOfNotNull(old?.bestLives, livesLeft.takeIf { success }).maxOrNull(),
                lastAttemptAt = t,
            ),
        )
        val clean = success && hintLevel == 0 && !failed
        return db.player().mutate { s ->
            val b = tracker.record(BandState(s.puzzleBand, s.bandOkStreak, s.bandFailStreak), solvedClean = clean, failed = failed || !success)
            s.copy(puzzleBand = b.band, bandOkStreak = b.okStreak, bandFailStreak = b.failStreak)
        }
    }

    // ---- Danh hiệu ----
    /** Cấp cao nhất đã thắng không chấp (Room + danh sách cấp đã thắng ở 1.0). */
    suspend fun highestLevelWon(legacyWon: Set<String>): Int =
        maxOf(db.games().highestLevelWonNoHandicap() ?: 0, legacyWon.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0)

    /** Cập nhật danh hiệu (chỉ đi lên). Trả tên danh hiệu mới nếu vừa lên. */
    suspend fun updateTitle(legacyWon: Set<String>): String? {
        val lv = highestLevelWon(legacyWon)
        var up: String? = null
        db.player().mutate { s ->
            if ((s.titleId ?: 0) < lv) { if (s.titleId != null) up = Titles.of(lv); s.copy(titleId = lv) } else s
        }
        return up
    }
}
