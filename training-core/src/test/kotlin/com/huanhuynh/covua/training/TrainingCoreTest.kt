package com.huanhuynh.covua.training

import com.huanhuynh.covua.engine.Handicap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class LeitnerTest {
    private val noFuzz = LeitnerScheduler(fuzz = 0.0)

    @Test fun boxesMoveByTable() {
        val today = 1000L
        var c = noFuzz.newCard(1, today)
        assertEquals(1001L, c.dueDay)
        val expect = listOf(2 to 3, 3 to 7, 4 to 16, 5 to 35)
        for ((box, gap) in expect) {
            c = noFuzz.answer(c, true, today)
            assertEquals(box, c.box); assertEquals(today + gap, c.dueDay)
        }
        c = noFuzz.answer(c, true, today)
        assertEquals(CardStatus.GRADUATED, c.status)
    }

    @Test fun wrongGoesBackToBoxOne() {
        val c = noFuzz.answer(CardSchedule(4, 0, 3, 0, CardStatus.ACTIVE), false, 50)
        assertEquals(1, c.box); assertEquals(51L, c.dueDay); assertEquals(1, c.lapses)
        val r = noFuzz.relapse(CardSchedule(3, 99, 2, 1, CardStatus.ACTIVE), 60)
        assertEquals(1, r.box); assertEquals(61L, r.dueDay); assertEquals(2, r.lapses)
    }

    @Test fun selfFoundStartsInBoxTwo() = assertEquals(1003L, noFuzz.newCard(2, 1000).dueDay)

    @Test fun fuzzWithinTenPercentAndSeeded() {
        val a = LeitnerScheduler(random = Random(7))
        val b = LeitnerScheduler(random = Random(7))
        repeat(200) {
            val x = a.interval(5)
            assertEquals(x, b.interval(5))
            assertTrue(x in 31..39)
            assertEquals(1, a.interval(1)) // 1 ngày không fuzz
        }
    }

    @Test fun parseRc() {
        assertEquals(listOf(1, 2, 4, 8, 16), LeitnerScheduler.parse("1,2,4,8,16"))
        assertEquals(LeitnerScheduler.DEFAULT_INTERVALS, LeitnerScheduler.parse("abc"))
    }
}

class StreakTest {
    private val calc = StreakCalculator(2, 3)
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay() // Thứ Hai

    private fun days(vararg offsets: Int) = offsets.map { monday + it }.toSet()

    @Test fun continuous() {
        val s = calc.compute(days(0, 1, 2, 3, 4), monday + 4)
        assertEquals(5, s.current); assertEquals(5, s.best); assertEquals(StreakCalculator.Status.ACTIVE, s.status)
    }

    @Test fun todayPendingNotMissed() {
        val s = calc.compute(days(0, 1, 2), monday + 3)
        assertEquals(3, s.current); assertTrue(s.restDays.isEmpty())
    }

    @Test fun missOneAndTwoUseRestDays() {
        val s1 = calc.compute(days(0, 2), monday + 2)
        assertEquals(2, s1.current); assertEquals(setOf(monday + 1), s1.restDays)
        val s2 = calc.compute(days(0, 3), monday + 3)
        assertEquals(2, s2.current); assertEquals(2, s2.restDays.size)
    }

    @Test fun missThreeInWeekPausesThenResumes() {
        // T2 tập, lỡ T3 T4 (nghỉ), lỡ T5 → tạm dừng, T6 quay lại → nối tiếp
        val s = calc.compute(days(0, 4), monday + 4)
        assertEquals(2, s.current); assertEquals(StreakCalculator.Status.ACTIVE, s.status)
        val paused = calc.compute(days(0), monday + 4)
        assertEquals(StreakCalculator.Status.PAUSED, paused.status); assertEquals(1, paused.current)
    }

    @Test fun restDaysResetEachWeekBoundary() {
        // Tuần 1: lỡ CN (ngày nghỉ 1 của tuần 1), tuần 2: lỡ T2, T3 (2 ngày nghỉ tuần 2) → vẫn liên tục
        val s = calc.compute(days(0, 1, 2, 3, 4, 5, 9), monday + 9)
        assertEquals(7, s.current)
        assertEquals(StreakCalculator.Status.ACTIVE, s.status)
    }

    @Test fun pauseTooLongResetsButKeepsBest() {
        // T2–T6 tập; T7,CN nghỉ (tuần 1); T2,T3 nghỉ (tuần 2); T4 bắt đầu tạm dừng
        val paused = calc.compute(days(0, 1, 2, 3, 4), monday + 11)
        assertEquals(StreakCalculator.Status.PAUSED, paused.status); assertEquals(5, paused.current)
        // quá 3 ngày tạm dừng → về 0; kỷ lục giữ 5
        val s = calc.compute(days(0, 1, 2, 3, 4), monday + 13)
        assertEquals(0, s.current); assertEquals(5, s.best)
        val back = calc.compute(days(0, 1, 2, 3, 4, 14), monday + 14)
        assertEquals(1, back.current); assertEquals(5, back.best)
    }

    @Test fun resumeOnLastAllowedDay() {
        // lỡ T6,T7 (nghỉ), lỡ CN, T2, T3 (3 ngày tạm dừng), T4 quay lại → nối tiếp (nhưng T2,T3 là tuần mới: dùng nghỉ)
        val s = calc.compute(days(0, 1, 2, 3, 9), monday + 9)
        assertTrue(s.current >= 5)
    }

    @Test fun futureDaysIgnored() {
        val s = calc.compute(days(0, 1, 2, 30), monday + 2)
        assertEquals(3, s.current)
    }

    @Test fun weekDots() {
        val st = calc.compute(days(0, 2), monday + 3)
        val dots = calc.weekDots(st, days(0, 2), monday + 3)
        assertEquals(
            listOf(StreakCalculator.Dot.DONE, StreakCalculator.Dot.REST, StreakCalculator.Dot.DONE, StreakCalculator.Dot.TODAY_PENDING,
                StreakCalculator.Dot.FUTURE, StreakCalculator.Dot.FUTURE, StreakCalculator.Dot.FUTURE),
            dots,
        )
    }

    @Test fun timezoneIndependentEpochDays() {
        // Tính trên epoch day nên đổi múi giờ chỉ đổi "hôm nay" – không làm hỏng dữ liệu cũ
        val s = calc.compute(days(0, 1), monday + 1)
        assertEquals(2, s.current)
    }
}

class PuzzleSelectionTest {
    private val list = (1..30).map { PuzzleMeta(it, (it - 1) % 5 + 1, if (it % 2 == 0) "Song xe" else "Mã Pháo", 1) }

    @Test fun picksUnsolvedSameBand() {
        val p = PuzzleSelector(list, Random(1)).next(3, solved = setOf(3))!!
        assertEquals(3, p.difficulty); assertTrue(p.id != 3)
    }

    @Test fun fallsBackToAdjacentBand() {
        val solved = list.filter { it.difficulty == 5 }.map { it.id }.toSet()
        val p = PuzzleSelector(list, Random(1)).next(5, solved)!!
        assertEquals(4, p.difficulty)
    }

    @Test fun prefersLessRecentTheme() {
        val p = PuzzleSelector(list, Random(1)).next(1, emptySet(), recentThemes = listOf("Mã Pháo", "Mã Pháo"))!!
        assertEquals("Song xe", p.theme)
    }

    @Test fun allSolvedRepeatsOldest() {
        val solved = list.map { it.id }.toSet()
        val p = PuzzleSelector(list).next(2, solved, lastSolvedAt = list.associate { it.id to (100L - it.id) })!!
        assertEquals(30, p.id)
    }

    @Test fun bandUpDown() {
        val t = BandTracker(3, 2)
        var s = BandState(2)
        repeat(3) { s = t.record(s, solvedClean = true, failed = false) }
        assertEquals(3, s.band)
        s = t.record(s, false, true); assertEquals(3, s.band)
        s = t.record(s, false, true); assertEquals(2, s.band)
        repeat(10) { s = t.record(s, false, true) }
        assertEquals(1, s.band)
    }
}

class LevelAdvisorTest {
    private val adv = LevelAdvisor()
    private fun g(id: Long, r: Int, lv: Int = 4, h: Handicap = Handicap.NONE) = FinishedGame(id, r, lv, h)

    @Test fun threeLossesSuggestEasier() {
        val s = adv.advise(listOf(g(3, -1), g(2, -1), g(1, -1)), null)!!
        assertEquals(LevelSuggestion.Type.EASIER, s.type)
        assertEquals(Handicap.AI_KNIGHT, s.options[0].handicap)
        assertEquals(3, s.options[1].level)
    }

    @Test fun mixedNoSuggestion() = assertNull(adv.advise(listOf(g(3, -1), g(2, 1), g(1, -1)), null))

    @Test fun threeWinsSuggestHarder() {
        val s = adv.advise(listOf(g(3, 1), g(2, 1), g(1, 1)), null)!!
        assertEquals(5, s.options[0].level)
        val h = adv.advise(listOf(g(3, 1, h = Handicap.AI_ROOK), g(2, 1, h = Handicap.AI_ROOK), g(1, 1, h = Handicap.AI_ROOK)), null)!!
        assertEquals(Handicap.AI_KNIGHT, h.options[0].handicap)
    }

    @Test fun cooldownAfterDecline() {
        val games = listOf(g(3, -1), g(2, -1), g(1, -1))
        assertNull(adv.advise(games, 4))
        assertNotNull(adv.advise(games, 5))
    }

    @Test fun differentLevelBreaksStreak() = assertNull(adv.advise(listOf(g(3, -1), g(2, -1, lv = 3), g(1, -1)), null))

    @Test fun ignoredGames() {
        val games = listOf(g(4, -1).copy(earlyResign = true), g(3, -1), g(2, -1).copy(fromPosition = true), g(1, -1))
        assertNull(adv.advise(games, null))
    }

    @Test fun easiestEncourages() {
        val h = Handicap.AI_QUEEN
        val s = adv.advise(listOf(g(3, -1, 1, h), g(2, -1, 1, h), g(1, -1, 1, h)), null)!!
        assertEquals(LevelSuggestion.Type.ENCOURAGE, s.type)
    }

    @Test fun handicapLadder() {
        assertEquals(Handicap.AI_KNIGHT, LevelAdvisor.easierHandicap(Handicap.AI_PAWN))
        assertEquals(Handicap.AI_QUEEN, LevelAdvisor.easierHandicap(Handicap.AI_ROOK))
        assertNull(LevelAdvisor.easierHandicap(Handicap.AI_QUEEN))
        assertEquals(Handicap.AI_PAWN, LevelAdvisor.harderHandicap(Handicap.AI_KNIGHT))
        assertEquals(Handicap.YOU_ROOK, LevelAdvisor.harderHandicap(Handicap.YOU_KNIGHT))
    }
}

class MistakeExtractorTest {
    private val ex = MistakeExtractor()
    private fun e(ply: Int, loss: Int, ok: Int = 1, fen: String = "f$ply") = PlayerMoveEval(ply, fen, ply, loss, ok, 0)

    @Test fun topThreeByLoss() {
        val r = ex.extract(4, false, listOf(e(1, 160), e(3, 900), e(5, 400), e(7, 200), e(9, 100)))
        assertEquals(listOf(3, 5, 7), r.map { it.eval.ply })
    }

    @Test fun levelOneAndLegacySkipped() {
        assertTrue(ex.extract(1, false, listOf(e(1, 900))).isEmpty())
        assertTrue(ex.extract(5, true, listOf(e(1, 900))).isEmpty())
    }

    @Test fun ambiguousPositionsSkipped() = assertTrue(ex.extract(4, false, listOf(e(1, 900, ok = 5))).isEmpty())

    @Test fun retryBoxes() {
        val a = e(1, 500).copy(retried = RetryKind.FOLLOWED_HINT)
        val b = e(3, 500).copy(retried = RetryKind.SELF, retryFoundGood = true)
        val r = ex.extract(4, false, listOf(a, b)).associate { it.eval.ply to it.startBox }
        assertEquals(1, r[1]); assertEquals(2, r[3])
    }
}

class DailyPlannerTest {
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()
    private val schedule = DailySchedule(monday - 3, (0 until 730).map { mapOf(1 to 1000 + it, 2 to 2000 + it, 3 to 3000 + it) })

    @Test fun sameDaySameDailyPuzzle() {
        val p = DailyPlanner()
        assertEquals(p.dailyPuzzle(schedule, monday), DailyPlanner().dailyPuzzle(schedule, monday))
    }

    @Test fun weekdayDifficulty() {
        val p = DailyPlanner()
        assertEquals(1, p.dailyPuzzle(schedule, monday)!!.second)       // Thứ Hai
        assertEquals(2, p.dailyPuzzle(schedule, monday + 2)!!.second)   // Thứ Tư
        assertEquals(3, p.dailyPuzzle(schedule, monday + 6)!!.second)   // Chủ nhật
    }

    @Test fun planWithCardsAndExtras() {
        var next = 1
        val plan = DailyPlanner().plan(monday, schedule, listOf(5L, 6L, 7L, 8L)) { excl -> generateSequence { next++ }.first { it !in excl } }!!
        assertEquals(listOf(5L, 6L, 7L), plan.reviewCardIds)
        assertEquals(2, plan.extraPuzzleIds.size)
        assertFalse(SessionParts.isComplete(SessionParts.DAILY, plan))
        assertTrue(SessionParts.isComplete(SessionParts.ALL, plan))
        val noCards = plan.copy(reviewCardIds = emptyList())
        assertTrue(SessionParts.isComplete(SessionParts.DAILY or SessionParts.EXTRA, noCards))
    }

    @Test fun scheduleIsFixedAndWraps() {
        assertEquals(schedule.entry(monday), schedule.entry(monday + 730))
    }

    @Test fun clockChangeAffectsOnlyToday() {
        val clock = FixedDayClock(monday)
        val a = DailyPlanner().dailyPuzzle(schedule, clock.today())
        clock.day = monday + 1
        val b = DailyPlanner().dailyPuzzle(schedule, clock.today())
        assertTrue(a != b)
    }
}

class ReminderPolicyTest {
    private val p = ReminderPolicy()
    private val tue = LocalDate.of(2026, 11, 17).toEpochDay()

    @Test fun rules() {
        assertTrue(p.shouldSend(tue, 19 * 60, true, false, null, 0))
        assertFalse(p.shouldSend(tue, 19 * 60, true, true, null, 0))       // đã hoạt động
        assertFalse(p.shouldSend(tue, 22 * 60, true, false, null, 0))      // giờ yên lặng
        assertFalse(p.shouldSend(tue, 7 * 60, true, false, null, 0))
        assertFalse(p.shouldSend(tue, 19 * 60, true, false, tue, 0))       // đã gửi hôm nay
        assertTrue(p.shouldSend(tue, 19 * 60, true, false, null, 3))       // giảm: Thứ Ba vẫn gửi
        assertFalse(p.shouldSend(tue + 1, 19 * 60, true, false, null, 3))  // Thứ Tư không
        assertFalse(p.shouldSend(tue, 19 * 60, true, false, null, 7))      // dừng
        assertEquals(20 * 60 + 30, p.effectiveMinute(22 * 60))
        assertEquals(600, p.medianMinute(listOf(500, 600, 900), 1140))
    }

    @Test fun contentPriority() {
        assertEquals(ReminderPolicy.Type.REVIEW, p.pickContent(2, "x", "y", 3, 0)!!.type)
        assertEquals(ReminderPolicy.Type.GAME, p.pickContent(0, "x", "y", 3, 0)!!.type)
        assertEquals(ReminderPolicy.Type.DAILY, p.pickContent(0, null, "y", 3, 0)!!.type)
        assertEquals(ReminderPolicy.Type.WEEK, p.pickContent(0, null, null, 3, 0)!!.type)
        assertNull(p.pickContent(0, null, null, null, 0))
    }
}

class MiscTest {
    @Test fun titles() {
        assertEquals("Tân thủ", Titles.of(0)); assertEquals("Tay cờ phường", Titles.of(4)); assertEquals("Đại kỳ tài", Titles.of(9))
    }

    @Test fun draw() {
        val d = DrawPolicy()
        assertTrue(d.decide(40, 0, false) is DrawPolicy.Decision.TooEarly)
        assertTrue(d.decide(62, 30, false) is DrawPolicy.Decision.Accept)
        assertTrue(d.decide(62, 400, false) is DrawPolicy.Decision.Decline)
        assertFalse(d.canOffer(70, 62)); assertTrue(d.canOffer(82, 62))
        assertTrue(StreakRules.gameCounts(20)); assertFalse(StreakRules.gameCounts(19))
    }
}
