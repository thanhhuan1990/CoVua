package com.huanhuynh.covua.engine

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.math.abs

/** Một dòng phân tích (MultiPV) của engine UCI. Điểm theo góc nhìn bên đi. */
data class UciLine(
    val multipv: Int,
    val depth: Int,
    /** Điểm centi-tốt (null nếu là chiếu hết). */
    val cp: Int?,
    /** Chiếu hết sau n nước (dương = bên đi thắng, âm = bị chiếu hết). */
    val mate: Int?,
    /** Biến chính (ký hiệu UCI: e2e4…). */
    val pv: List<String>,
) {
    /** Điểm quy về thang của [Search] (MATE − khoảng cách khi chiếu hết). */
    val score: Int get() = when {
        mate != null && mate > 0 -> Search.MATE - (2 * mate - 1)
        mate != null -> -Search.MATE + 2 * abs(mate)
        else -> (cp ?: 0).coerceIn(-Search.MATE + 1000, Search.MATE - 1000)
    }
}

data class UciResult(val bestMove: String, val lines: List<UciLine>)

/** Giới hạn tìm kiếm: theo độ sâu, theo thời gian (ms) hoặc số node. */
data class GoLimit(val depth: Int = 0, val movetimeMs: Long = 0, val nodes: Long = 0) {
    fun command(): String = buildString {
        append("go")
        if (depth > 0) append(" depth ").append(depth)
        if (movetimeMs > 0) append(" movetime ").append(movetimeMs)
        if (nodes > 0) append(" nodes ").append(nodes)
        if (depth <= 0 && movetimeMs <= 0 && nodes <= 0) append(" depth 10")
    }
}

/** Đọc dòng "info … score … pv …" (thuần – có unit test). */
object UciParse {
    fun info(line: String): UciLine? {
        if (!line.startsWith("info ") || " pv " !in line || " score " !in line) return null
        val tok = line.split(' ').filter { it.isNotEmpty() }
        var depth = 0; var multipv = 1; var cp: Int? = null; var mate: Int? = null
        var pv: List<String> = emptyList()
        var i = 1
        while (i < tok.size) {
            when (tok[i]) {
                "depth" -> { depth = tok.getOrNull(i + 1)?.toIntOrNull() ?: 0; i += 2 }
                "multipv" -> { multipv = tok.getOrNull(i + 1)?.toIntOrNull() ?: 1; i += 2 }
                "score" -> {
                    when (tok.getOrNull(i + 1)) {
                        "cp" -> cp = tok.getOrNull(i + 2)?.toIntOrNull()
                        "mate" -> mate = tok.getOrNull(i + 2)?.toIntOrNull()
                    }
                    i += 3
                    // bỏ "lowerbound"/"upperbound"
                    if (tok.getOrNull(i) == "lowerbound" || tok.getOrNull(i) == "upperbound") return null
                }
                "pv" -> { pv = tok.subList(i + 1, tok.size); i = tok.size }
                else -> i++
            }
        }
        if (pv.isEmpty() || (cp == null && mate == null)) return null
        return UciLine(multipv, depth, cp, mate, pv)
    }
}

/**
 * Engine cờ vua giao thức UCI (Stockfish) chạy như một tiến trình riêng.
 * Mọi lời gọi tuần tự (synchronized); [stop] có thể gọi từ luồng khác để dừng sớm.
 */
class UciEngine(
    private val launcher: () -> Process,
    private val threads: Int = 1,
    private val hashMb: Int = 16,
    private val onLog: (String) -> Unit = {},
) : Closeable {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: Writer? = null
    private val options = HashMap<String, String>()
    @Volatile private var searching = false

    var engineName: String = "UCI"
        private set

    /** Khởi động + bắt tay UCI. Ném [IOException] nếu không chạy được. */
    @Synchronized
    fun start() {
        if (process != null) return
        val p = launcher()
        process = p
        attach(p.inputStream, p.outputStream)
        send("uci")
        readUntil { line -> if (line.startsWith("id name ")) engineName = line.removePrefix("id name ").trim(); line == "uciok" }
        setOption("Threads", threads.toString())
        setOption("Hash", hashMb.toString())
        ready()
    }

    /** Gắn luồng vào/ra (dùng cho test với tiến trình giả). */
    fun attach(input: InputStream, output: OutputStream) {
        reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        writer = OutputStreamWriter(output, Charsets.UTF_8)
    }

    private fun send(cmd: String) {
        val w = writer ?: throw IOException("UCI: chưa khởi động")
        w.write(cmd); w.write("\n"); w.flush()
    }

    private fun readLine(): String {
        val line = reader?.readLine() ?: throw IOException("UCI: tiến trình đã đóng")
        return line.trim()
    }

    private inline fun readUntil(stop: (String) -> Boolean) {
        var n = 0
        while (true) {
            val line = readLine()
            if (stop(line)) return
            if (++n > 100_000) throw IOException("UCI: không phản hồi")
        }
    }

    private fun ready() {
        send("isready")
        readUntil { it == "readyok" }
    }

    private fun setOption(name: String, value: String) {
        if (options[name] == value) return
        send("setoption name $name value $value")
        options[name] = value
    }

    /**
     * Tìm kiếm thế [fen] (+ các nước UCI [moves] tiếp theo). [multiPv] số biến; [elo] giới hạn sức cờ (null = tối đa);
     * [skill] Skill Level 0–20 (null = 20).
     */
    @Synchronized
    fun search(fen: String, moves: List<String>, limit: GoLimit, multiPv: Int = 1, elo: Int? = null, skill: Int? = null): UciResult {
        if (process == null && writer == null) start()
        setOption("MultiPV", multiPv.coerceAtLeast(1).toString())
        if (elo != null) { setOption("UCI_LimitStrength", "true"); setOption("UCI_Elo", elo.toString()) }
        else setOption("UCI_LimitStrength", "false")
        setOption("Skill Level", (skill ?: 20).coerceIn(0, 20).toString())
        send(if (fen == Position.START_FEN || fen == "startpos") "position startpos" + mv(moves) else "position fen $fen" + mv(moves))
        searching = true
        send(limit.command())
        val lines = HashMap<Int, UciLine>()
        var best = "0000"
        try {
            readUntil { line ->
                if (line.startsWith("bestmove")) {
                    best = line.split(' ').getOrNull(1) ?: "0000"
                    true
                } else {
                    UciParse.info(line)?.let { l ->
                        val old = lines[l.multipv]
                        if (old == null || l.depth >= old.depth) lines[l.multipv] = l
                    }
                    false
                }
            }
        } finally {
            searching = false
        }
        return UciResult(best, lines.values.sortedBy { it.multipv })
    }

    private fun mv(moves: List<String>) = if (moves.isEmpty()) "" else " moves " + moves.joinToString(" ")

    /** Dừng tìm kiếm đang chạy (gọi được từ luồng khác). */
    fun stop() {
        if (!searching) return
        runCatching { writer?.let { it.write("stop\n"); it.flush() } }
    }

    override fun close() {
        runCatching { send("quit") }
        runCatching { process?.destroy() }
        process = null
        reader = null
        writer = null
        onLog("UCI: đã đóng")
    }
}

/**
 * Engine ngoài dùng chung cho cả app (Stockfish). App đặt [provider] khi khởi động;
 * null → mọi thứ dùng engine thuần Kotlin ([Search]).
 */
object Engines {
    @Volatile var provider: (() -> UciEngine?)? = null
    /** Báo lỗi khi engine ngoài hỏng giữa chừng (app chuyển sang dự phòng). */
    @Volatile var onFailure: ((Throwable) -> Unit)? = null

    fun external(): UciEngine? = runCatching { provider?.invoke() }.getOrNull()

    /** Chạy [block] với engine ngoài; lỗi → báo [onFailure] và trả null để dùng dự phòng. */
    inline fun <T> withExternal(block: (UciEngine) -> T): T? {
        val e = external() ?: return null
        return try { block(e) } catch (t: Throwable) { onFailure?.invoke(t); null }
    }
}
