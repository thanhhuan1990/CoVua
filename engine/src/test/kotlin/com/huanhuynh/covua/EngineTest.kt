package com.huanhuynh.covua

import com.huanhuynh.covua.engine.Game
import com.huanhuynh.covua.engine.GoLimit
import com.huanhuynh.covua.engine.Handicap
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.engine.AiPlayer
import com.huanhuynh.covua.engine.MateProver
import com.huanhuynh.covua.engine.MoveList
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.OpeningBook
import com.huanhuynh.covua.engine.Outcome
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import com.huanhuynh.covua.engine.UciParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {
    private fun perft(p: Position, d: Int): Long {
        if (d == 0) return 1
        var n = 0L
        for (m in p.legalMoves()) { p.make(m); n += perft(p, d - 1); p.unmake() }
        return n
    }

    @Test fun perftStart() {
        val p = Position.start()
        assertEquals(20L, perft(p, 1))
        assertEquals(400L, perft(p, 2))
        assertEquals(8902L, perft(p, 3))
        assertEquals(197281L, perft(p, 4))
        assertEquals(Position.START_FEN, p.toFen())
    }

    @Test fun perftKiwipete() {
        // Thế kiểm tra kinh điển: nhập thành, bắt tốt qua đường, phong cấp, ghim
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(p, 1))
        assertEquals(2039L, perft(p, 2))
        assertEquals(97862L, perft(p, 3))
    }

    @Test fun perftPosition3() {
        val p = Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(p, 1))
        assertEquals(191L, perft(p, 2))
        assertEquals(2812L, perft(p, 3))
        assertEquals(43238L, perft(p, 4))
    }

    @Test fun perftPosition4() {
        val p = Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(p, 1))
        assertEquals(264L, perft(p, 2))
        assertEquals(9467L, perft(p, 3))
    }

    @Test fun hashRestoredAfterUnmake() {
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        val h = p.hash; val fen = p.toFen()
        for (m in p.legalMoves()) {
            p.make(m)
            // hash tăng dần khớp với hash dựng lại từ FEN
            assertEquals(Notation.of(Position.fromFen(fen), m), Position.fromFen(p.toFen()).hash, p.hash)
            p.unmake()
        }
        assertEquals(h, p.hash); assertEquals(fen, p.toFen())
    }

    @Test fun sanNotation() {
        val g = Game()
        for (san in listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O")) {
            val m = Notation.parse(g.pos, san)
            assertNotEquals(san, 0, m)
            assertTrue(g.play(m))
        }
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O"), g.codes)
    }

    @Test fun disambiguationAndCheck() {
        val p = Position.fromFen("4k3/8/8/8/8/8/K7/R6R w - - 0 1")
        val m = p.legalMoves().first { Moves.from(it) == 0 && Moves.to(it) == 3 }
        assertEquals("Rad1", Notation.of(p, m))
        val q = Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        assertEquals("Ra8#", Notation.of(q, Notation.parse(q, "Ra8")))
    }

    @Test fun enPassantAndPromotion() {
        val p = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val ep = Notation.parse(p, "exd6")
        assertNotEquals(0, ep)
        p.make(ep)
        assertEquals(0, p[Position.parseSquare("d5")])
        p.unmake()
        assertEquals(-Position.PAWN, p[Position.parseSquare("d5")])
        val q = Position.fromFen("8/4P3/8/8/8/k7/8/4K3 w - - 0 1")
        assertEquals(4, q.legalMoves().count { Moves.from(it) == Position.parseSquare("e7") })
        assertEquals("e8=Q", Notation.of(q, Notation.parse(q, "e8=Q")))
    }

    @Test fun castlingRules() {
        // Không nhập thành qua ô bị kiểm soát
        val p = Position.fromFen("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1")
        val sans = p.legalMoves().map { Notation.of(p, it) }
        assertFalse("O-O" in sans)
        assertTrue("O-O-O" in sans)
        // Vua đi rồi thì mất quyền
        val g = Game("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        g.play(Notation.parse(g.pos, "Ke2")); g.play(Notation.parse(g.pos, "Kd8")); g.play(Notation.parse(g.pos, "Ke1")); g.play(Notation.parse(g.pos, "Ke8"))
        assertFalse(g.pos.legalMoves().map { Notation.of(g.pos, it) }.any { it.startsWith("O-O") })
    }

    @Test fun outcomes() {
        val mate = Game("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        mate.play(Notation.parse(mate.pos, "Ra8"))
        assertEquals(Outcome.WHITE_WIN, mate.outcome())
        val stale = Game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(Outcome.DRAW, stale.outcome())
        assertEquals(Outcome.DRAW, Game("8/8/4k3/8/8/3NK3/8/8 w - - 0 1").outcome())
        val g = Game()
        repeat(2) { for (s in listOf("Nf3", "Nf6", "Ng1", "Ng8")) g.play(Notation.parse(g.pos, s)) }
        assertEquals(Outcome.DRAW, g.outcome())
        val back = Game.deserialize(g.serialize())!!
        assertEquals(g.codes, back.codes)
    }

    @Test fun searchFindsMates() {
        val s = Search(16)
        val r = s.think(Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"), 4)
        assertTrue(Search.isMate(r.score)); assertEquals(1, Search.mateIn(r.score))
        // Vua + Xe: Kb6 rồi Rh8#
        val p = Position.fromFen("k7/8/2K5/8/8/8/8/7R w - - 0 1")
        val proof = MateProver().prove(p, 3)
        assertEquals(2, proof.mateIn)
    }

    @Test fun searchWinsHangingQueen() {
        val p = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p1q1/4P3/3P4/PPP2PPP/RNBQKBNR w KQkq - 0 1")
        val m = Search(16).think(p, 4).move
        assertEquals("Bxg5", Notation.of(p, m))
    }

    @Test fun aiPlaysLegalMovesAllLevels() {
        for (lv in Level.entries) {
            val g = Game()
            val ai = AiPlayer(Search(14), Random(lv.id))
            repeat(6) {
                val m = ai.chooseMove(g.positionCopy(), lv, g.codes)
                assertTrue("$lv", g.play(m))
            }
        }
    }

    @Test fun handicapFens() {
        val f = Handicap.AI_QUEEN.startFen(1)
        val p = Position.fromFen(f)
        assertEquals(0, p[Position.parseSquare("d8")])
        assertEquals(Position.QUEEN, p[Position.parseSquare("d1")])
        val r = Position.fromFen(Handicap.YOU_ROOK.startFen(1))
        assertEquals(0, r[0])
        assertFalse(r.toFen().contains("Q") && r.toFen().split(' ')[2].contains('Q'))
        for (h in Handicap.entries) assertTrue(h.id, Handicap.rung(h) >= 0)
    }

    @Test fun openingLinesAreLegalAndNamed() {
        for ((line, name) in OpeningBook.catalog()) {
            val g = Game()
            for (san in line) { val m = Notation.parse(g.pos, san); assertNotEquals("${name.title}: $san", 0, m); g.play(m) }
            assertEquals(name.title, OpeningBook.nameExact(g.codes)?.title)
        }
        val g = Game()
        val p = OpeningBook.pick(g.positionCopy(), Random(1), emptyList())
        assertTrue(p != null && p != 0)
    }

    @Test fun uciInfoParsing() {
        val l = UciParse.info("info depth 12 seldepth 18 multipv 2 score cp -35 nodes 1000 nps 1 pv e7e5 g1f3 b8c6")!!
        assertEquals(2, l.multipv); assertEquals(-35, l.cp); assertEquals(listOf("e7e5", "g1f3", "b8c6"), l.pv)
        val m = UciParse.info("info depth 5 multipv 1 score mate 2 pv d1h5 g7g6 h5e5")!!
        assertEquals(Search.MATE - 3, m.score)
        assertNull(UciParse.info("info depth 5 score cp 10 lowerbound pv e2e4"))
        assertNull(UciParse.info("info string NNUE evaluation"))
        assertEquals("go depth 8 movetime 500", GoLimit(depth = 8, movetimeMs = 500).command())
        val p = Position.start()
        assertEquals("e2e4", Moves.uci(Moves.fromUci(p, "e2e4")))
        assertEquals(0, Moves.fromUci(p, "e2e5"))
    }

    @Test fun movelistGrows() {
        val l = MoveList(2); repeat(10) { l.add(it) }; assertEquals(10, l.size)
    }
}
