package com.huanhuynh.covua

import com.huanhuynh.covua.coach.Coach
import com.huanhuynh.covua.coach.CoachTags
import com.huanhuynh.covua.coach.Grade
import com.huanhuynh.covua.coach.Puzzle
import com.huanhuynh.covua.coach.PuzzleSession
import com.huanhuynh.covua.engine.Game
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {
    @Test fun openingIsBook() {
        val g = Game()
        val r = Coach().review(g.positionCopy(), Notation.parse(g.pos, "e4"), emptyList())
        assertEquals(Grade.BOOK, r.grade)
        g.play(Notation.parse(g.pos, "e4")); g.play(Notation.parse(g.pos, "c5"))
        val g2 = Game(); for (s in listOf("e4", "c5")) g2.play(Notation.parse(g2.pos, s))
        assertEquals(Grade.BOOK, Coach().review(g2.positionCopy(), Notation.parse(g2.pos, "Nf3"), g2.codes).grade)
    }

    @Test fun hangingQueenIsBlunder() {
        // Hậu đi vào ô Tốt đen ăn được
        val fen = "rnbqkbnr/ppp2ppp/3p4/4p3/4P3/5Q2/PPPP1PPP/RNB1KBNR w KQkq - 0 3"
        val p = Position.fromFen(fen)
        val m = Notation.parse(p, "Qf6")
        val r = Coach().review(p, m, listOf("x", "y", "z"))
        assertTrue("${r.grade} ${r.title} ${r.body}", r.grade == Grade.MISTAKE || r.grade == Grade.BLUNDER)
        assertTrue(r.tags and (CoachTags.HANGING or CoachTags.LOST_PIECE) != 0)
        assertTrue(r.better != 0)
        assertEquals(fen, p.toFen()) // thầy trả lại nguyên thế
    }

    @Test fun missedMateIsCaught() {
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        val r = Coach().review(p, Notation.parse(p, "h3"), listOf("x"))
        assertTrue(r.grade.isBad)
        assertTrue(r.tags and CoachTags.MISSED_MATE != 0)
        assertEquals("Ra8#", Notation.of(p, r.better))
    }

    @Test fun checkmateIsBrilliant() {
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        assertEquals(Grade.BRILLIANT, Coach().review(p, Notation.parse(p, "Ra8"), listOf("x")).grade)
    }

    @Test fun warningsMarkDangerSquares() {
        val p = Position.fromFen("rnbqkbnr/ppp2ppp/3p4/4p3/4P3/5Q2/PPPP1PPP/RNB1KBNR w KQkq - 0 3")
        val c = Coach()
        val ta = c.analyzeTurn(p, 3)
        val w = c.warningsFor(p, Position.parseSquare("f3"), ta)
        assertTrue(w.keys.map { Position.squareName(it) }.toString(), Position.parseSquare("f6") in w)
        assertFalse(Position.parseSquare("e2") in w)
    }

    @Test fun hintAndOpponentText() {
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        val c = Coach()
        val h = c.hint(p, null, 3, 500)!!
        assertEquals("Ra8#", Notation.of(p, h.move))
        assertTrue(c.describeOpponent(p, h.move).isNotBlank())
    }

    @Test fun puzzleSessionBlackToMove() {
        // Đen đi: chiếu hết hàng cuối 1 nước
        val pz = Puzzle(1, "r5k1/8/8/8/8/8/5PPP/6K1 b - - 0 1", 1, "Hàng cuối", 1, listOf("Ra1#"), 1)
        assertEquals(-1, pz.side)
        val s = PuzzleSession(pz)
        assertFalse(s.tryMove(Notation.parse(s.pos, "Ra2")))
        assertTrue(s.tryMove(Notation.parse(s.pos, "Ra1")))
        assertTrue(s.solved)
        assertTrue(pz.solutionShown().isNotEmpty())
    }

    @Test fun puzzleMateIn2() {
        // Vua + Xe: Kb6 Kb8 Rh8#
        val fen = "k7/8/2K5/8/8/8/8/7R w - - 0 1"
        val line = com.huanhuynh.covua.engine.MateProver().line(Position.fromFen(fen), 2)!!
        val pp = Position.fromFen(fen)
        val sol = line.map { m -> Notation.of(pp, m).also { pp.make(m) } }
        val pz = Puzzle(2, fen, 2, "Đòn Xe", 2, sol)
        val s = PuzzleSession(pz)
        val first = s.nextSolutionMove()
        assertTrue(s.tryMove(first))
        assertTrue(s.opponentReply() != 0)
        assertTrue(s.tryMove(s.nextSolutionMove()))
        assertTrue(s.solved)
        assertTrue(Moves.from(first) >= 0)
    }
}
