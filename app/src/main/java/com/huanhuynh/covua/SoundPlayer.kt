package com.huanhuynh.covua

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Hiệu ứng âm thanh của app (tự tổng hợp: quân gỗ đập xuống bàn gỗ – mô phỏng cộng hưởng; không bản quyền). Bật/tắt bằng Cài đặt → "Âm thanh", không phụ thuộc âm chạm của máy. */
enum class Sfx(val res: Int, val volume: Float = 1f) {
    MOVE(R.raw.sfx_move),
    MOVE_2(R.raw.sfx_move2),
    MOVE_3(R.raw.sfx_move3),
    CAPTURE(R.raw.sfx_capture),
    CHECK(R.raw.sfx_check),
    WIN(R.raw.sfx_win),
    LOSE(R.raw.sfx_lose, 0.9f),
    DRAW(R.raw.sfx_draw, 0.9f),
    CORRECT(R.raw.sfx_correct),
    WRONG(R.raw.sfx_wrong, 0.9f),
}

class SoundPlayer(context: Context) {
    private val app = context.applicationContext
    @Volatile var enabled = true

    private val pool: SoundPool by lazy {
        SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build().also { p -> p.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id } }
    }
    private val ids = HashMap<Sfx, Int>()
    private val loaded = java.util.Collections.synchronizedSet(HashSet<Int>())

    /** Nạp trước (gọi một lần lúc khởi động, SoundPool tự giải mã nền). */
    fun preload() {
        runCatching { Sfx.entries.forEach { s -> ids[s] = pool.load(app, s.res, 1) } }
    }

    fun play(s: Sfx) {
        if (!enabled) return
        // Đi quân: chọn ngẫu nhiên 1 trong 3 tiếng gõ để nghe tự nhiên, không lặp y hệt
        val pick = if (s == Sfx.MOVE) listOf(Sfx.MOVE, Sfx.MOVE_2, Sfx.MOVE_3).random() else s
        val id = ids[pick] ?: return
        if (id !in loaded) return
        runCatching { pool.play(id, s.volume, s.volume, 1, 0, 1f) }
    }

    /** Âm cho một nước đi: chiếu > ăn quân > đi thường. */
    fun move(captured: Boolean, check: Boolean) = play(if (check) Sfx.CHECK else if (captured) Sfx.CAPTURE else Sfx.MOVE)
}
