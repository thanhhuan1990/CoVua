package com.huanhuynh.covua.tools

import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.engine.MateProver
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.OpeningBook
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.random.Random

/** Một bài đào được. */
data class Mined(
    val fen: String,
    val mateIn: Int,
    val solution: List<String>,
    val theme: String,
    val firstIsCheck: Boolean,
    val sacrifice: Boolean,
    val defenderReplies: Int,
    val key: String,
    val game: Int,
    val material: Int,
) {
    fun toJson(): String = buildJsonObject {
        put("fen", fen); put("mateIn", mateIn); put("theme", theme); put("firstIsCheck", firstIsCheck)
        put("sacrifice", sacrifice); put("defenderReplies", defenderReplies); put("key", key); put("game", game); put("material", material)
        put("solution", buildJsonArray { solution.forEach { add(JsonPrimitive(it)) } })
    }.toString()

    companion object {
        fun parse(line: String): Mined {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(line) as kotlinx.serialization.json.JsonObject
            fun s(k: String) = (o[k] as JsonPrimitive).content
            return Mined(s("fen"), s("mateIn").toInt(),
                (o["solution"] as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content },
                s("theme"), s("firstIsCheck").toBoolean(), s("sacrifice").toBoolean(), s("defenderReplies").toInt(), s("key"), s("game").toInt(), s("material").toInt())
        }
    }
}

/**
 * Máy tự đánh với nhau (engine Kotlin, cấp 2–6 có nhiễu, khai cuộc theo sách + ngẫu nhiên) và đào bài chiếu hết ngay trong ván:
 * mỗi thế từ nửa nước 16, lọc nhanh bằng [Search] có cắt tỉa, rồi CHỨNG MINH bằng [MateProver] (tìm đủ) – nước đầu phải duy nhất.
 */
class ChessMiner(private val seed: Long = 20261112L) {

    fun run(games: Int, out: File, threads: Int, minutes: Int) {
        val pool = Executors.newFixedThreadPool(threads)
        val done = AtomicInteger()
        val found = AtomicInteger()
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        out.parentFile?.mkdirs()
        val writer = out.bufferedWriter()
        repeat(threads) { t ->
            pool.execute {
                val rnd = Random(seed + t * 7919)
                val search = Search(16)
                val prover = MateProver(1_500_000)
                var g = t
                while (g < games && System.currentTimeMillis() < deadline) {
                    val res = runCatching { playOne(g, rnd, search, prover) }.getOrElse { emptyList() }
                    synchronized(writer) { res.forEach { writer.write(it.toJson()); writer.write("\n") }; writer.flush() }
                    found.addAndGet(res.size)
                    val n = done.incrementAndGet()
                    if (n % 20 == 0) System.err.println("ván $n, bài ${found.get()}")
                    g += threads
                }
            }
        }
        pool.shutdown(); pool.awaitTermination(7, TimeUnit.DAYS)
        writer.close()
        System.err.println("xong: ${done.get()} ván, ${found.get()} bài → $out")
    }

    private fun playOne(gi: Int, rnd: Random, search: Search, prover: MateProver): List<Mined> {
        val pos = Position.start()
        val lv = intArrayOf(0, rnd.nextInt(2, 7), rnd.nextInt(2, 7)) // [1] Trắng, [2] Đen
        val history = ArrayList<String>()
        val out = ArrayList<Mined>()
        var ply = 0
        val bookPlies = rnd.nextInt(2, 12)
        while (ply < 200) {
            val legal = pos.legalMoves()
            if (legal.isEmpty() || pos.repetitionCount() >= 2 || pos.halfmove >= 100 || pos.insufficientMaterial()) break
            if (ply >= 16 && out.size < 3) probe(pos, gi, search, prover)?.let { m -> if (out.none { it.key == m.key }) out += m }
            val m = when {
                ply < bookPlies -> OpeningBook.pick(pos, rnd, history) ?: legal[rnd.nextInt(legal.size)]
                else -> choose(pos, legal, Level.of(lv[if (pos.side > 0) 1 else 2]), rnd, search)
            }
            history += Notation.of(pos, m)
            pos.make(m); ply++
        }
        return out
    }

    private fun choose(pos: Position, legal: IntArray, level: Level, rnd: Random, search: Search): Int {
        val depth = level.depth.coerceAtMost(4)
        val scores = search.scoreAll(pos, depth.coerceAtLeast(1), legal)
        val best = scores.max()
        val noise = if (level.noise > 0) level.noise else 30
        val errRate = if (level.errRate > 0) level.errRate else 0.05
        val sloppy = rnd.nextDouble() < errRate
        val margin = if (sloppy) noise else noise / 6
        val cand = legal.indices.filter { scores[it] >= best - margin }
        return legal[if (cand.isEmpty()) scores.indices.maxBy { scores[it] } else cand[rnd.nextInt(cand.size)]]
    }

    /** Thế [pos] có bài chiếu hết ≤ 4 nước, nước đầu duy nhất? */
    private fun probe(pos: Position, gi: Int, search: Search, prover: MateProver): Mined? {
        search.clear()
        val r = search.think(pos, 6, maxNodes = 120_000)
        if (!Search.isMate(r.score) || r.score < 0) return null
        val n = Search.mateIn(r.score)
        if (n !in 1..4) return null
        val copy = Position.fromFen(pos.toFen())
        val proof = prover.prove(copy, n)
        if (proof.exhausted || proof.mateIn == 0 || !proof.unique) return null
        val line = prover.line(copy, proof.mateIn) ?: return null
        if (line.first() != proof.firstMove && proof.mateIn > 1) return null
        // Lời giải SAN
        val p = Position.fromFen(pos.toFen())
        val sol = line.map { m -> Notation.of(p, m).also { p.make(m) } }
        if (!sol.last().endsWith("#")) return null
        val start = Position.fromFen(pos.toFen())
        val first = line.first()
        start.make(first)
        val check = start.inCheck()
        val replies = start.legalMoves().size
        start.unmake()
        val sac = isSacrifice(start, first)
        return Mined(
            fen = pos.toFen(), mateIn = proof.mateIn, solution = sol,
            theme = theme(Position.fromFen(pos.toFen()), line, sac), firstIsCheck = check, sacrifice = sac,
            defenderReplies = replies, key = pos.toFen(full = false), game = gi, material = material(pos, pos.side) - material(pos, -pos.side),
        )
    }

    companion object {
        fun material(pos: Position, s: Int): Int {
            var n = 0
            for (sq in 0 until 64) { val v = pos[sq] * s; if (v > 1) n += com.huanhuynh.covua.engine.Eval.VALUE[v] }
            return n
        }

        /** Nước đầu là thí quân: quân đi tới ô bị tấn công mà giá trị lớn hơn quân rẻ nhất tấn công nó (hoặc không được bảo vệ). */
        fun isSacrifice(pos: Position, m: Int): Boolean {
            val me = pos.side
            val moved = abs(pos[Moves.from(m)])
            if (moved == Position.PAWN || moved == Position.KING) return false
            val gain = com.huanhuynh.covua.engine.Eval.VALUE[abs(pos[Moves.to(m)])]
            pos.make(m)
            val atk = pos.attackers(Moves.to(m), -me)
            val def = pos.attackers(Moves.to(m), me)
            pos.unmake()
            if (atk.isEmpty()) return false
            val v = com.huanhuynh.covua.engine.Eval.VALUE[moved]
            val cheapest = atk.minOf { com.huanhuynh.covua.engine.Eval.VALUE[abs(pos[it])] }
            return v - gain >= 200 && (def.isEmpty() || cheapest < v)
        }

        /** Chủ đề theo nước chiếu hết cuối cùng (và nước đầu). */
        fun theme(pos: Position, line: List<Int>, sacrifice: Boolean): String {
            val attacker = pos.side
            val firstType = abs(pos[Moves.from(line.first())])
            for (m in line.dropLast(1)) pos.make(m)
            val last = line.last()
            val type = abs(pos[Moves.from(last)])
            val promo = Moves.promo(last)
            pos.make(last)
            val k = pos.kingSquare(-attacker)
            val kr = k shr 3; val kc = k and 7
            var ownAround = 0; var around = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = kr + dr; val c = kc + dc
                if (r !in 0..7 || c !in 0..7) continue
                around++
                if (pos[r * 8 + c] * -attacker > 0) ownAround++
            }
            val backRank = if (attacker > 0) 7 else 0
            val mateType = if (promo != 0) promo else type
            val toRank = Moves.to(last) shr 3
            return when {
                promo != 0 -> "Phong cấp"
                type == Position.KNIGHT && ownAround == around -> "Chiếu ngột"
                kr == backRank && toRank == backRank && (mateType == Position.ROOK || mateType == Position.QUEEN) && ownAround >= 2 -> "Hàng cuối"
                sacrifice && line.size >= 3 -> "Thí quân"
                line.size >= 5 && firstType != type -> "Phối hợp"
                mateType == Position.QUEEN -> "Đòn Hậu"
                mateType == Position.ROOK -> "Đòn Xe"
                mateType == Position.KNIGHT -> "Đòn Mã"
                mateType == Position.BISHOP -> "Đòn Tượng"
                mateType == Position.PAWN -> "Tốt chiếu"
                else -> "Phối hợp"
            }
        }
    }
}
