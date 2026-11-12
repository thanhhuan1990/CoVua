package com.huanhuynh.covua.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

/**
 * Đóng gói nội dung:
 * - mỗi ván tự đánh lấy tối đa 2 bài (khác số nước); bỏ thế chênh lệch vật chất quá lớn / bên thủ chỉ còn Vua + Tốt;
 * - tỉ lệ 1/2/3/4 nước ≈ 30/40/22/8 %, xoay vòng theo chủ đề để đa dạng;
 * - độ khó 1–5: điểm = số nước + 1 (nước đầu không chiếu) + 1 (thí quân) + 0,5 (bên thủ ≥ 3 cách đỡ), chia đều theo thứ hạng;
 * - lịch "Bài của thầy" cố định 730 ngày (mỗi ngày 1 bài cho mỗi số nước 1–4 để Remote Config đổi độ khó theo thứ).
 */
class ContentPacker(private val seed: Long = 730L) {

    data class Item(val id: Int, val m: Mined, var score: Double = 0.0, var difficulty: Int = 0)

    fun run(inputs: List<File>, outDir: File, csv: File, legacy: File, total: Int = 600) {
        val rnd = Random(seed)
        val seen = HashSet<String>()
        val all = inputs.flatMapIndexed { fi, f ->
            f.readLines().filter { it.isNotBlank() }.mapNotNull { runCatching { Mined.parse(it) }.getOrNull() }
                .map { it.copy(game = fi * 1_000_000 + it.game) }
        }
            .filter { it.mateIn in 1..4 && it.material in -300..1800 }
            .filter { seen.add(it.key) }
            .shuffled(rnd)
        // mỗi ván tối đa 2 bài khác số nước (bài dài nhất + bài ngắn nhất), ưu tiên nước đầu không chiếu
        val perGame = all.groupBy { it.game }.values.flatMap { l ->
            val sorted = l.sortedWith(compareBy({ -it.mateIn.coerceAtMost(3) }, { if (it.firstIsCheck) 1 else 0 }))
            val first = sorted.first()
            listOfNotNull(first, sorted.lastOrNull { it.mateIn != first.mateIn })
        }.shuffled(rnd)
        val share = mapOf(1 to 0.30, 2 to 0.40, 3 to 0.22, 4 to 0.08)
        val picked = ArrayList<Mined>()
        var carry = 0
        for (n in 4 downTo 1) {
            val byTheme = perGame.filter { it.mateIn == n }.groupBy { it.theme }.mapValues { e -> e.value.sortedBy { if (it.firstIsCheck) 1 else 0 }.toMutableList() }
            var need = (total * share.getValue(n)).toInt() + carry
            while (need > 0 && byTheme.values.any { it.isNotEmpty() }) {
                for (l in byTheme.values) if (need > 0 && l.isNotEmpty()) { picked += l.removeAt(0); need-- }
            }
            carry = need // thiếu bài dài thì bù bằng bài ngắn hơn
        }
        // Còn thiếu: bù bằng bài 1–2 nước còn lại (kể cả bài khác của cùng ván, khác thế)
        if (picked.size < total) {
            val used = picked.map { it.key }.toHashSet()
            picked += all.filter { it.mateIn <= 2 && it.key !in used }.sortedBy { it.mateIn }.take(total - picked.size)
        }
        val items = picked.sortedWith(compareBy({ it.mateIn }, { it.theme }, { it.fen })).mapIndexed { i, m -> Item(i + 1, m) }
        items.forEach { it.score = it.m.mateIn + (if (!it.m.firstIsCheck) 1.0 else 0.0) + (if (it.m.sacrifice) 1.0 else 0.0) + (if (it.m.defenderReplies >= 3) 0.5 else 0.0) }
        // Chia đều 5 bậc theo thứ hạng điểm (hoà điểm thì xếp ngẫu nhiên cố định)
        items.shuffled(rnd).sortedBy { it.score }.forEachIndexed { rank, it -> it.difficulty = (rank * 5 / items.size + 1).coerceIn(1, 5) }

        fun puzzleJson(it: Item) = buildJsonObject {
            put("id", it.id); put("fen", it.m.fen); put("mateIn", it.m.mateIn); put("theme", it.m.theme)
            put("stars", it.m.mateIn.coerceAtMost(3)); put("difficulty", it.difficulty)
            put("solution", buildJsonArray { it.m.solution.forEach { s -> add(JsonPrimitive(s)) } })
        }
        outDir.mkdirs()
        gz(File(outDir, "puzzles.v2.json.gz"), buildJsonObject {
            put("content_version", 2)
            putJsonArray("puzzles") { items.forEach { add(puzzleJson(it)) } }
        }.toString())
        // Bản dự phòng (danh sách phẳng) – app dùng khi không đọc được gói nén
        legacy.writeText(buildJsonArray { items.take(40).forEach { add(puzzleJson(it)) } }.toString())
        val start = LocalDate.of(2026, 11, 2).toEpochDay() // Thứ Hai
        val queues = (1..4).associateWith { n ->
            val pool = items.filter { it.m.mateIn == n }.ifEmpty { items.filter { it.m.mateIn == n - 1 } }
            pool.shuffled(rnd)
        }
        val days = (0 until 730).map { d -> (1..4).map { n -> val q = queues.getValue(n); q[d % q.size].id } }
        gz(File(outDir, "daily_schedule.json.gz"), buildJsonObject {
            put("content_version", 2)
            put("start_epoch_day", start)
            putJsonArray("days") { days.forEach { d -> add(buildJsonArray { d.forEach { add(JsonPrimitive(it)) } }) } }
        }.toString())
        csv.parentFile?.mkdirs()
        csv.writeText("id,mate_in,difficulty,score,theme,first_check,sacrifice,replies,fen,solution\n" +
            items.joinToString("\n") { "${it.id},${it.m.mateIn},${it.difficulty},${it.score},${it.m.theme},${it.m.firstIsCheck},${it.m.sacrifice},${it.m.defenderReplies},${it.m.fen},${it.m.solution.joinToString(" ")}" })
        System.err.println("pack: ${items.size} bài (từ ${perGame.size} ván) " + items.groupingBy { it.m.mateIn }.eachCount() +
            " độ khó " + items.groupingBy { it.difficulty }.eachCount().toSortedMap() +
            " chủ đề " + items.groupingBy { it.m.theme }.eachCount())
    }

    private fun gz(f: File, s: String) {
        GZIPOutputStream(f.outputStream()).use { it.write(s.toByteArray()) }
        System.err.println("ghi $f: ${f.length() / 1024} KB")
    }
}
