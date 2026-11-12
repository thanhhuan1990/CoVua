package com.huanhuynh.covua.feature.daily

import com.huanhuynh.covua.data.PlayerRepository
import com.huanhuynh.covua.data.SettingsRepository
import com.huanhuynh.covua.data.content.ContentRepository
import com.huanhuynh.covua.feature.review.ReviewRepository
import com.huanhuynh.covua.feature.streak.ActivityRepository
import com.huanhuynh.covua.training.DailyPlan
import com.huanhuynh.covua.training.DailyPlanner
import com.huanhuynh.covua.training.DayClock
import com.huanhuynh.covua.training.PuzzleSelector

data class DailyConfig(
    val enabled: Boolean = true,
    val reviewCards: Int = 3,
    val extraPuzzles: Int = 2,
    val lives: Int = 5,
    val weekday: List<Int> = DailyPlanner.DEFAULT_WEEKDAY,
)

/**
 * "Buổi tập hôm nay": kế hoạch lập một lần mỗi ngày rồi lưu lại (mở lại app vẫn đúng bài).
 * Bài của thầy theo lịch cố định – cả nước cùng một bài trong ngày.
 */
class DailyRepository(
    private val settings: SettingsRepository,
    private val content: ContentRepository,
    private val reviews: ReviewRepository,
    private val player: PlayerRepository,
    private val activity: ActivityRepository,
    private val clock: DayClock,
    private val config: () -> DailyConfig,
) {
    private fun encode(p: DailyPlan) = listOf(p.day, p.reviewCardIds.joinToString(";"), p.dailyPuzzleId, p.dailyMateIn, p.extraPuzzleIds.joinToString(";")).joinToString("|")

    private fun decode(s: String): DailyPlan? = runCatching {
        val a = s.split('|')
        DailyPlan(
            a[0].toLong(), a[1].split(';').filter { it.isNotBlank() }.map { it.toLong() }, a[2].toInt(), a[3].toInt(),
            a[4].split(';').filter { it.isNotBlank() }.map { it.toInt() },
        )
    }.getOrNull()

    /** Kế hoạch hôm nay (lập nếu chưa có / đã sang ngày mới). null nếu chưa có nội dung. */
    suspend fun today(): DailyPlan? {
        val day = clock.today()
        settings.getStr(KEY)?.let(::decode)?.takeIf { it.day == day }?.let { return it }
        val c = content.get()
        val schedule = c.schedule ?: return null
        val cfg = config()
        val due = reviews.due().map { it.id }
        val ps = player.get()
        val solved = player.progress().filter { it.solved }.map { it.puzzleId }.toSet()
        val selector = PuzzleSelector(c.metas, kotlin.random.Random(day))
        val plan = DailyPlanner(cfg.weekday, cfg.reviewCards, cfg.extraPuzzles)
            .plan(day, schedule, due) { excl -> selector.next(ps.puzzleBand, solved, exclude = excl)?.id } ?: return null
        settings.setStr(KEY, encode(plan))
        return plan
    }

    /** Bài của thầy hôm nay (không cần lập cả kế hoạch). */
    suspend fun dailyPuzzleId(): Pair<Int, Int>? {
        val s = content.get().schedule ?: return null
        return DailyPlanner(config().weekday).dailyPuzzle(s, clock.today())
    }

    suspend fun partsDone(): Int = activity.today()?.sessionPartsDone ?: 0

    companion object {
        private const val KEY = "daily_plan"
    }
}
