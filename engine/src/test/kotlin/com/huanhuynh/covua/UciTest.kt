package com.huanhuynh.covua

import com.huanhuynh.covua.engine.Engines
import com.huanhuynh.covua.engine.GoLimit
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.engine.AiPlayer
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import com.huanhuynh.covua.engine.UciEngine
import com.huanhuynh.covua.coach.Coach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

/** Giả lập một engine UCI (trả nước đầu tiên hợp lệ, điểm giảm dần theo MultiPV) để kiểm vòng giao thức. */
class UciTest {
    private val commands = java.util.Collections.synchronizedList(ArrayList<String>())

    private fun fakeEngine(): UciEngine {
        val toEngine = PipedOutputStream(); val engineIn = PipedInputStream(toEngine, 1 shl 16)
        val engineOut = PipedOutputStream(); val fromEngine = PipedInputStream(engineOut, 1 shl 16)
        Thread {
            val r = engineIn.bufferedReader(); val w = engineOut.bufferedWriter()
            var multipv = 1
            var pos = Position.start()
            while (true) {
                val line = r.readLine() ?: break
                commands += line
                when {
                    line == "uci" -> { w.write("id name FakeFish\nuciok\n"); w.flush() }
                    line == "isready" -> { w.write("readyok\n"); w.flush() }
                    line.startsWith("setoption name MultiPV value ") -> multipv = line.substringAfterLast(' ').toInt()
                    line.startsWith("position ") -> {
                        val fen = if (line.startsWith("position startpos")) Position.START_FEN else line.removePrefix("position fen ").substringBefore(" moves")
                        pos = Position.fromFen(fen)
                        if (" moves " in line) for (u in line.substringAfter(" moves ").split(' ')) pos.make(Moves.fromUci(pos, u))
                    }
                    line.startsWith("go") -> {
                        val legal = pos.legalMoves()
                        legal.take(multipv).forEachIndexed { i, m ->
                            w.write("info depth 5 seldepth 7 multipv ${i + 1} score cp ${50 - 10 * i} nodes 100 pv ${Moves.uci(m)}\n")
                        }
                        w.write("bestmove ${Moves.uci(legal[0])}\n"); w.flush()
                    }
                    line == "quit" -> break
                }
            }
        }.apply { isDaemon = true; start() }
        val e = UciEngine(launcher = { error("không dùng") })
        e.attach(fromEngine, toEngine)
        return e
    }

    @After fun reset() { Engines.provider = null }

    @Test fun searchReturnsLines() {
        val e = fakeEngine()
        val r = e.search(Position.START_FEN, listOf("e2e4"), GoLimit(depth = 5), multiPv = 3, elo = 1500)
        assertEquals(3, r.lines.size)
        assertEquals(50, r.lines[0].cp)
        assertTrue(r.bestMove.length >= 4)
        assertTrue(commands.any { it == "position startpos moves e2e4" })
        assertTrue(commands.any { it == "setoption name UCI_Elo value 1500" })
    }

    @Test fun aiAndCoachUseExternalEngine() {
        val e = fakeEngine()
        Engines.provider = { e }
        val p = Position.start()
        val m = AiPlayer(Search(14)).chooseMove(p, Level.L6, listOf("x"), useBook = false)
        assertEquals(p.legalMoves()[0], m)
        val ta = Coach(Search(14)).analyzeTurn(p, 3)
        assertEquals(20, ta.scores.size)
        assertEquals(50, ta.bestScore)
        Engines.provider = null
        // Không có engine ngoài → dự phòng Kotlin vẫn chạy
        assertTrue(AiPlayer(Search(14)).chooseMove(Position.start(), Level.L6, listOf("x"), useBook = false) != 0)
    }
}
