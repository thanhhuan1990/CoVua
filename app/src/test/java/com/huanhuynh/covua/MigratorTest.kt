package com.huanhuynh.covua

import com.huanhuynh.covua.data.db.DailyActivityEntity
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.data.db.LegacyStatEntity
import com.huanhuynh.covua.data.db.PuzzleProgressEntity
import com.huanhuynh.covua.data.migration.DataStoreToRoomMigrator
import com.huanhuynh.covua.data.migration.LegacySnapshot
import com.huanhuynh.covua.data.migration.MigrationFlag
import com.huanhuynh.covua.data.migration.MigrationTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Room giả: mô phỏng INSERT OR IGNORE theo khoá chính và giao dịch (lỗi → huỷ hết). */
class FakeDb : MigrationTarget, MigrationFlag {
    val games = LinkedHashMap<Long, GameEntity>()
    val stats = LinkedHashMap<String, LegacyStatEntity>()
    val puzzles = LinkedHashMap<Int, PuzzleProgressEntity>()
    val activity = LinkedHashMap<Long, DailyActivityEntity>()
    var flagValue = 0
    var failAt: String? = null
    private var nextId = 1L

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val snap = listOf(HashMap(games), HashMap(stats), HashMap(puzzles), HashMap(activity))
        return try { block() } catch (t: Throwable) {
            games.clear(); games.putAll(snap[0] as Map<Long, GameEntity>)
            stats.clear(); stats.putAll(snap[1] as Map<String, LegacyStatEntity>)
            puzzles.clear(); puzzles.putAll(snap[2] as Map<Int, PuzzleProgressEntity>)
            activity.clear(); activity.putAll(snap[3] as Map<Long, DailyActivityEntity>)
            throw t
        }
    }
    override suspend fun insertGames(games: List<GameEntity>) { if (failAt == "games") error("boom"); games.forEach { this.games.putIfAbsent(it.id, it); nextId = maxOf(nextId, it.id + 1) } }
    override suspend fun hasInProgressGame() = games.values.any { it.status == "IN_PROGRESS" }
    override suspend fun insertGame(game: GameEntity) { val id = nextId++; games[id] = game.copy(id = id) }
    override suspend fun insertStats(stats: List<LegacyStatEntity>) { if (failAt == "stats") error("boom"); stats.forEach { this.stats.putIfAbsent(it.key, it) } }
    override suspend fun insertPuzzleProgress(list: List<PuzzleProgressEntity>) = list.forEach { puzzles.putIfAbsent(it.puzzleId, it) }
    override suspend fun insertActivity(list: List<DailyActivityEntity>) = list.forEach { activity.putIfAbsent(it.epochDay, it) }
    override suspend fun version() = flagValue
    override suspend fun setVersion(v: Int) { flagValue = v }
}

class MigratorTest {
    private fun migrator(db: FakeDb, errors: MutableList<String> = mutableListOf()) =
        DataStoreToRoomMigrator(db, db, engineVersion = 2, zone = ZoneId.of("Asia/Ho_Chi_Minh"), now = { 1_700_000_000_000 }) { step, _ -> errors += step }

    private val sixty = LegacySnapshot(
        games = 75, wins = 40, losses = 30, draws = 5, winStreak = 2, launchCount = 33,
        accuracyLog = (0 until 60).map { "202610%02d".format(it / 3 + 1) to (50 + it % 40) },
        wonLevels = setOf(1, 2, 3),
    )

    @Test fun emptyState() = runBlocking {
        val db = FakeDb()
        assertEquals(DataStoreToRoomMigrator.Outcome.MIGRATED, migrator(db).run { LegacySnapshot() })
        assertTrue(db.games.isEmpty()); assertEquals(1, db.flagValue)
        assertEquals("0", db.stats["games"]!!.value)
    }

    @Test fun sixtyGames() = runBlocking {
        val db = FakeDb()
        migrator(db).run { sixty }
        assertEquals(60, db.games.size)
        assertTrue(db.games.values.all { it.legacy && it.status == "FINISHED" })
        assertEquals(20, db.activity.size)
        assertEquals("1,2,3", db.stats["won_levels"]!!.value)
        assertEquals("75", db.stats["games"]!!.value)
    }

    @Test fun gameInProgress() = runBlocking {
        val db = FakeDb()
        migrator(db).run { LegacySnapshot(savedGame = "rheakaehr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RHEAKAEHR w|2310,1234", savedLevel = 4, savedSide = -1) }
        val g = db.games.values.single()
        assertEquals("IN_PROGRESS", g.status); assertEquals(4, g.level); assertEquals(-1, g.playerSide); assertEquals("2310 1234", g.moves)
    }

    @Test fun fortyPuzzles() = runBlocking {
        val db = FakeDb()
        migrator(db).run { LegacySnapshot(puzzlesSolved = (1..40).toSet()) }
        assertEquals((1..40).toSet(), db.puzzles.keys)
    }

    @Test fun runTwiceNoDuplicates() = runBlocking {
        val db = FakeDb()
        val all = sixty.copy(puzzlesSolved = (1..40).toSet(), savedGame = "x w|1", savedLevel = 3)
        migrator(db).run { all }
        db.flagValue = 0 // giả lập: đã commit nhưng chưa kịp ghi cờ
        migrator(db).run { all }
        assertEquals(61, db.games.size); assertEquals(40, db.puzzles.size); assertEquals(20, db.activity.size)
        // có cờ → bỏ qua
        assertEquals(DataStoreToRoomMigrator.Outcome.SKIPPED, migrator(db).run { all })
    }

    @Test fun failureRollsBackAndKeepsFlag() = runBlocking {
        val db = FakeDb().apply { failAt = "stats" }
        val errors = mutableListOf<String>()
        assertEquals(DataStoreToRoomMigrator.Outcome.FAILED, migrator(db, errors).run { sixty })
        assertEquals(listOf("stats"), errors)
        assertTrue(db.games.isEmpty()); assertEquals(0, db.flagValue)
        db.failAt = null
        assertEquals(DataStoreToRoomMigrator.Outcome.MIGRATED, migrator(db).run { sixty })
    }
}
