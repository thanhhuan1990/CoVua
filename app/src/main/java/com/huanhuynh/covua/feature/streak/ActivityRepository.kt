package com.huanhuynh.covua.feature.streak

import com.huanhuynh.covua.data.db.DailyActivityEntity
import com.huanhuynh.covua.data.db.StreakFreezeEntity
import com.huanhuynh.covua.data.db.UserDb
import com.huanhuynh.covua.training.DayClock
import com.huanhuynh.covua.training.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class StreakConfig(val enabled: Boolean = true, val freeRestPerWeek: Int = 2, val pauseDays: Int = 3, val minMoves: Int = 10)

/** Hoạt động theo ngày + Lịch tập (tính lại từ daily_activity, không lưu biến đếm). */
class ActivityRepository(
    private val db: UserDb,
    private val clock: DayClock,
    private val config: () -> StreakConfig,
    private val log: (String, Map<String, String>) -> Unit,
) {
    data class Snapshot(
        val today: Long,
        val days: List<DailyActivityEntity>,
        val state: StreakCalculator.State,
        val week: List<StreakCalculator.Dot>,
    ) {
        val todayActivity get() = days.firstOrNull { it.epochDay == today }
        val activeToday get() = todayActivity?.counts == true
        /** Số ngày hoạt động trong tuần hiện tại. */
        val weekDone get() = week.count { it == StreakCalculator.Dot.DONE }
    }

    private fun calc() = config().let { StreakCalculator(it.freeRestPerWeek, it.pauseDays) }

    fun snapshot(days: List<DailyActivityEntity>, today: Long = clock.today()): Snapshot {
        val active = days.filter { it.counts }.map { it.epochDay }.toSet()
        val c = calc()
        val st = c.compute(active, today)
        return Snapshot(today, days, st, c.weekDots(st, active, today))
    }

    fun flow(): Flow<Snapshot> = db.activity().allFlow().map { snapshot(it) }
    suspend fun current(): Snapshot = snapshot(db.activity().all())

    suspend fun markOpen() {
        val minute = clock.minuteOfDay()
        db.activity().mutate(clock.today()) { if (it.firstOpenMinute == null) it.copy(firstOpenMinute = minute) else it }
    }

    suspend fun openMinutes(days: Int = 14): List<Int> = db.activity().openMinutesSince(clock.today() - days)

    /** Ván ≥ [StreakConfig.minMoves] nước (mỗi bên) mới tính vào Lịch tập. Trả mốc mới đạt (nếu có). */
    suspend fun recordGame(plies: Int): Int? {
        if (plies < config().minMoves * 2) return null
        return record("game") { it.copy(gamesCounted = it.gamesCounted + 1) }
    }

    suspend fun recordPuzzle(daily: Boolean, lives: Int? = null): Int? = record(if (daily) "daily" else "puzzle") {
        if (daily) it.copy(puzzles = it.puzzles + 1, dailyPuzzleDone = true, dailyLives = maxOf(it.dailyLives ?: 0, lives ?: 0))
        else it.copy(puzzles = it.puzzles + 1)
    }

    suspend fun recordReview(): Int? = record("review") { it.copy(reviews = it.reviews + 1) }

    suspend fun markSessionPart(mask: Int): DailyActivityEntity =
        db.activity().mutate(clock.today()) { it.copy(sessionPartsDone = it.sessionPartsDone or mask) }

    suspend fun today(): DailyActivityEntity? = db.activity().get(clock.today())
    fun todayFlow(): Flow<DailyActivityEntity?> = db.activity().dayFlow(clock.today())

    private suspend fun record(activity: String, f: (DailyActivityEntity) -> DailyActivityEntity): Int? {
        val beforeSnap = current()
        val before = beforeSnap.state
        val wasActive = beforeSnap.activeToday
        db.activity().mutate(clock.today(), f)
        val after = current()
        // Ghi các ngày nghỉ tự động đã dùng (để hiện lịch, và để biết lý do chuỗi còn giữ)
        val known = db.activity().freezeDays().toSet()
        after.state.restDays.filter { it !in known }.forEach {
            db.activity().insertFreeze(StreakFreezeEntity(epochDayUsed = it))
            log("streak_rest_used", emptyMap())
        }
        if (!config().enabled || wasActive || !after.activeToday) return null
        val cur = after.state.current
        // Ngày đầu tiên được tính hôm nay: phát các sự kiện chuỗi
        if (before.status == StreakCalculator.Status.PAUSED) log("streak_resumed", mapOf("n" to cur.toString()))
        if (before.current > 0 && before.status == StreakCalculator.Status.NONE) log("streak_reset", mapOf("n" to before.current.toString(), "best" to before.best.toString()))
        log("streak_day", mapOf("n" to cur.toString(), "activity" to activity))
        if (cur in StreakCalculator.MILESTONES) {
            log("streak_milestone", mapOf("n" to cur.toString()))
            return cur
        }
        return null
    }
}
