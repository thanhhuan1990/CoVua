package com.huanhuynh.covua.data.content

import android.content.Context
import com.huanhuynh.covua.coach.Puzzle
import com.huanhuynh.covua.training.DailySchedule
import com.huanhuynh.covua.training.PuzzleMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.zip.GZIPInputStream

/** Nội dung chỉ đọc (assets/content: puzzles.v2.json.gz, daily_schedule.json.gz). Nạp một lần trên luồng IO. */
class ContentRepository(private val context: Context, scope: CoroutineScope) {

    data class Content(
        val version: Int,
        val puzzles: List<Puzzle>,
        val schedule: DailySchedule?,
    ) {
        private val byId = puzzles.associateBy { it.id }
        fun puzzle(id: Int): Puzzle? = byId[id]
        val metas: List<PuzzleMeta> by lazy { puzzles.map { PuzzleMeta(it.id, it.difficulty.coerceIn(1, 5), it.theme, it.mateIn) } }
        val themes: List<String> by lazy { puzzles.groupingBy { it.theme }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    }

    private val loading: Deferred<Content> = scope.async(Dispatchers.IO) { load() }

    @Volatile private var cached: Content? = null

    suspend fun get(): Content = cached ?: loading.await().also { cached = it }

    /** Dùng trong Compose sau khi đã nạp (trả null nếu chưa xong). */
    fun peek(): Content? = cached ?: if (loading.isCompleted) loading.getCompleted().also { cached = it } else null

    /**
     * Đọc asset JSON đã nén gzip. Lưu ý: khi đóng gói APK/AAB, công cụ build tự giải nén và BỎ đuôi ".gz"
     * (assets/content/x.json.gz → x.json trong bản release). Vì vậy thử cả hai tên và tự nhận diện gzip
     * theo 2 byte đầu (1F 8B) – trước đây chỉ mở tên ".gz" nên bản release luôn đọc thất bại
     * (Buổi tập hôm nay trống, bài thế rơi về bộ cũ).
     */
    private fun readGz(path: String): String? {
        val base = path.removeSuffix(".gz")
        for (name in listOf("$base.gz", base)) {
            val bytes = runCatching { context.assets.open(name).use { it.readBytes() } }.getOrNull() ?: continue
            val gz = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            val text = runCatching {
                if (gz) GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() } else bytes.toString(Charsets.UTF_8)
            }.getOrNull()
            if (text != null) return text
        }
        return null
    }

    private fun load(): Content {
        val pack = readGz("content/puzzles.v2.json.gz")?.let { runCatching { Puzzle.parsePack(it) }.getOrNull() }
        val (ver, puzzles) = pack ?: (1 to runCatching {
            Puzzle.parseAll(context.assets.open("puzzles.json").bufferedReader().use { it.readText() })
        }.getOrDefault(emptyList()).map { it.copy(difficulty = it.stars) })
        val schedule = readGz("content/daily_schedule.json.gz")?.let { raw ->
            runCatching {
                val o = Json.parseToJsonElement(raw) as JsonObject
                val start = o["start_epoch_day"]!!.jsonPrimitive.long
                val days = (o["days"] as JsonArray).map { d ->
                    (d as JsonArray).mapIndexed { i, id -> (i + 1) to id.jsonPrimitive.int }.toMap()
                }
                DailySchedule(start, days)
            }.getOrNull()
        }
        return Content(ver, puzzles, schedule)
    }
}
