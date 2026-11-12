package com.huanhuynh.covua.stockfish

import android.content.Context
import android.util.Log
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.engine.Engines
import com.huanhuynh.covua.engine.UciEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Stockfish cho cả app: file thực thi `libstockfish.so` trong nativeLibraryDir (build bằng engine-native/build_stockfish.sh).
 * Có → gắn vào [Engines] (máy cấp 4–8 + phân tích đầu lượt của thầy). Không có / lỗi giữa chừng → engine Kotlin dự phòng.
 * Stockfish là phần mềm GPL v3 – xem màn Giới thiệu → Giấy phép.
 */
class StockfishHub(private val context: Context) {

    enum class Status { STARTING, STOCKFISH, FALLBACK }

    private val _status = MutableStateFlow(Status.STARTING)
    val status: StateFlow<Status> = _status

    @Volatile private var engine: UciEngine? = null
    @Volatile private var failed = false

    private val binary: File get() = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")

    val name: String get() = engine?.engineName?.takeIf { !failed } ?: "Kotlin"

    fun hasStockfish(): Boolean = runCatching { binary.exists() && binary.canExecute() }.getOrDefault(false)

    /** Gắn vào [Engines] – gọi một lần lúc mở app. Khởi động tiến trình lười (lần dùng đầu tiên, trên luồng nền). */
    fun install() {
        Engines.provider = { current() }
        Engines.onFailure = { fail(it) }
        if (!hasStockfish()) { failed = true; _status.value = Status.FALLBACK }
    }

    /** Khởi động sẵn (trên luồng nền) để nước đầu không phải chờ. */
    fun warmUp() { current() }

    @Synchronized
    private fun current(): UciEngine? {
        if (failed) return null
        engine?.let { return it }
        if (!hasStockfish()) { failed = true; _status.value = Status.FALLBACK; return null }
        return try {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
            val e = UciEngine(
                launcher = { ProcessBuilder(binary.absolutePath).directory(context.filesDir).redirectErrorStream(true).start() },
                threads = threads, hashMb = 32, onLog = { Log.d("Stockfish", it) },
            )
            e.start()
            engine = e
            _status.value = Status.STOCKFISH
            e
        } catch (t: Throwable) {
            fail(t); null
        }
    }

    private fun fail(t: Throwable) {
        Log.w("Stockfish", "Không chạy được Stockfish, dùng engine dự phòng", t)
        FirebaseHelper.recordException(context, t)
        failed = true
        runCatching { engine?.close() }
        engine = null
        _status.value = Status.FALLBACK
    }

    fun close() { runCatching { engine?.close() }; engine = null }
}
