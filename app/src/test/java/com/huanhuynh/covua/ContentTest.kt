package com.huanhuynh.covua

import com.huanhuynh.covua.coach.Puzzle
import com.huanhuynh.covua.engine.MateProver
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.Position
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** Kiểm toàn bộ nội dung: lời giải hợp lệ, kết thúc chiếu hết, đúng số nước; lịch 730 ngày trỏ tới bài có thật. */
class ContentTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/content/$name", "app/src/main/assets/content/$name").map(::File).first { it.exists() }

    private fun gz(name: String) = GZIPInputStream(asset(name).inputStream()).bufferedReader().use { it.readText() }

    private val pack by lazy { Puzzle.parsePack(gz("puzzles.v2.json.gz")) }

    @Test fun sizeAndMix() {
        val (ver, list) = pack
        assertEquals(2, ver)
        assertTrue(list.size in 400..800)
        assertTrue((1..list.size).all { id -> list.any { it.id == id } })
        assertEquals(list.size, list.map { it.id }.toSet().size)
        val by = list.groupingBy { it.mateIn }.eachCount()
        assertTrue(by.keys.all { it in 1..4 })
        assertTrue(list.all { it.difficulty in 1..5 })
        val bytes = asset("puzzles.v2.json.gz").length() + asset("daily_schedule.json.gz").length()
        assertTrue("nội dung < 150 KB", bytes < 150 * 1024)
    }

    @Test fun everySolutionMates() {
        for (p in pack.second) {
            val pos = Position.fromFen(p.fen)
            assertEquals("bài ${p.id}: bên đi", p.side, pos.side)
            assertEquals("bài ${p.id}: độ dài lời giải", 2 * p.mateIn - 1, p.solution.size)
            for (code in p.solution) {
                val m = Notation.parse(pos, code)
                assertTrue("bài ${p.id}: nước $code không hợp lệ", m != 0)
                pos.make(m)
            }
            assertTrue("bài ${p.id}: chưa chiếu hết", pos.legalMoves().isEmpty() && pos.inCheck())
        }
    }

    @Test fun mateLengthIsExact() {
        // Không có đường chiếu hết ngắn hơn số nước ghi trên bài
        val prover = MateProver(1_000_000)
        for (p in pack.second.filter { it.mateIn >= 2 }) {
            val proof = prover.prove(Position.fromFen(p.fen), p.mateIn - 1, checkUnique = false)
            assertTrue("bài ${p.id}: có đường ngắn hơn", proof.mateIn == 0 || proof.exhausted)
        }
    }

    @Test fun dailyScheduleValid() {
        val o = Json.parseToJsonElement(gz("daily_schedule.json.gz")) as JsonObject
        val days = o["days"] as JsonArray
        assertEquals(730, days.size)
        val ids = pack.second.associateBy { it.id }
        for (d in days) {
            (d as JsonArray).forEachIndexed { i, e ->
                val p = ids[e.jsonPrimitive.int]
                assertTrue(p != null && p.mateIn <= i + 1)
            }
        }
    }
}
