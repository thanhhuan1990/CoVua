package com.huanhuynh.covua.data

import com.huanhuynh.covua.coach.Grade
import com.huanhuynh.covua.coach.MoveReview
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.data.db.GameStatus
import com.huanhuynh.covua.data.db.MoveEvalEntity
import com.huanhuynh.covua.data.db.UserDb
import com.huanhuynh.covua.engine.EngineInfo

/**
 * Lưu trữ ván trong Room: tạo hàng khi bắt đầu, ghi move_eval từng nước của người chơi, chốt khi xong.
 * Ván đang dở vẫn ghi song song vào DataStore (dự phòng khi Room lỗi).
 */
class GameArchiveRepository(private val db: UserDb, private val now: () -> Long = System::currentTimeMillis) {

    suspend fun start(mode: String, level: Int, handicap: String?, playerSide: Int, startFen: String, openingId: String? = null): Long {
        val t = now()
        val id = db.games().insert(
            GameEntity(
                status = GameStatus.IN_PROGRESS, mode = mode, level = level, handicap = handicap,
                playerSide = playerSide, startFen = startFen, engineVersion = EngineInfo.VERSION, startedAt = t,
                openingId = openingId,
            ),
        )
        db.games().abandonOthers(keep = id, at = t)
        return id
    }

    suspend fun saveProgress(id: Long, moves: List<Int>, accuracy: Int?, undos: Int, hints: Int) =
        db.games().updateProgress(id, GameEntity.encodeMoves(moves), accuracy, undos, hints)

    /** Lời thầy chỉ lưu cho nước từ "Chưa chính xác" trở xuống (đỡ tốn chỗ); nước tốt chỉ lưu điểm. */
    suspend fun saveEval(gameId: Long, ply: Int, r: MoveReview, scoreBefore: Int) {
        val keepText = r.grade.ordinal >= Grade.INACCURACY.ordinal
        db.evals().upsert(
            MoveEvalEntity(
                gameId = gameId, ply = ply, move = r.move, code = r.code, bestMove = if (r.better != 0) r.better else r.move,
                scoreBefore = scoreBefore, scoreAfter = r.playedScore, lossCp = r.loss, accuracy = r.accuracy,
                label = r.grade.ordinal, tags = r.tags,
                coachTitle = if (keepText) r.title else null,
                coachText = if (keepText) (listOf(r.body) + r.details + r.betterDetails).joinToString("\n") else null,
            ),
        )
    }

    suspend fun truncate(gameId: Long, fromPly: Int) = db.evals().deleteFrom(gameId, fromPly)

    suspend fun finish(id: Long, moves: List<Int>, result: Int, reason: String, accuracy: Int?, undos: Int, hints: Int): GameEntity? {
        val g = db.games().get(id) ?: return null
        val done = g.copy(
            status = GameStatus.FINISHED, moves = GameEntity.encodeMoves(moves), result = result, endReason = reason,
            accuracy = accuracy, undoCount = undos, hintCount = hints, endedAt = now(),
        )
        db.games().update(done)
        return done
    }

    suspend fun abandon(id: Long) {
        val g = db.games().get(id) ?: return
        if (g.status == GameStatus.IN_PROGRESS) db.games().update(g.copy(status = GameStatus.ABANDONED, endedAt = now()))
    }

    suspend fun inProgress(): GameEntity? = db.games().inProgress()
    suspend fun get(id: Long): GameEntity? = db.games().get(id)
    suspend fun evals(gameId: Long): List<MoveEvalEntity> = db.evals().forGame(gameId)
    suspend fun recentFinished(n: Int) = db.games().recentFinished(n)

    /** Giữ lời thầy của 300 ván gần nhất (gọi nền lúc khởi động). */
    suspend fun prune() = db.games().pruneCoachText(300)

    companion object {
        /** Dựng lại MoveReview gần đúng từ move_eval (để tiếp tục ván / phân tích sau khi app bị tắt). */
        fun toReview(e: MoveEvalEntity): MoveReview {
            val lines = e.coachText?.split('\n').orEmpty()
            val grade = Grade.entries.getOrElse(e.label) { Grade.GOOD }
            return MoveReview(
                move = e.move, code = e.code, grade = grade, title = e.coachTitle ?: grade.label,
                body = lines.firstOrNull() ?: "", better = if (e.bestMove != e.move) e.bestMove else 0,
                loss = e.lossCp, accuracy = e.accuracy, details = lines.drop(1), tags = e.tags,
                playedScore = e.scoreAfter, bestScore = e.scoreAfter + e.lossCp,
            )
        }
    }
}
