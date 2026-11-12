package com.huanhuynh.covua.data.migration

import androidx.room.withTransaction
import com.huanhuynh.covua.data.db.DailyActivityEntity
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.data.db.LegacyStatEntity
import com.huanhuynh.covua.data.db.PuzzleProgressEntity
import com.huanhuynh.covua.data.db.UserDb

class RoomMigrationTarget(private val db: UserDb) : MigrationTarget {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
    override suspend fun insertGames(games: List<GameEntity>) = db.games().insertAll(games)
    override suspend fun hasInProgressGame(): Boolean = db.games().inProgress() != null
    override suspend fun insertGame(game: GameEntity) { db.games().insert(game) }
    override suspend fun insertStats(stats: List<LegacyStatEntity>) = db.player().insertLegacy(stats)
    override suspend fun insertPuzzleProgress(list: List<PuzzleProgressEntity>) = db.puzzles().insertProgressIgnore(list)
    override suspend fun insertActivity(list: List<DailyActivityEntity>) = list.forEach { db.activity().insertIgnore(it) }
}
