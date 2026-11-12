package com.huanhuynh.covua.engine

import com.huanhuynh.covua.i18n.t
import kotlin.random.Random

/**
 * 8 cấp độ máy. Cấp 1–3: engine thuần Kotlin có "nhiễu" (đi như người mới); cấp 4–8: Stockfish giới hạn Elo
 * ([elo], null = sức tối đa). Không có Stockfish thì mọi cấp dùng [depth]/[timeMs]/[noise] của engine Kotlin.
 */
enum class Level(
    val id: Int,
    private val titleVi: String,
    private val blurbVi: String,
    val depth: Int,
    val timeMs: Long,
    val noise: Int,
    val errRate: Double,
    /** Elo giới hạn khi dùng Stockfish (0 = không dùng Stockfish cho cấp này). */
    val elo: Int,
    /** Thời gian nghĩ của Stockfish (ms). */
    val sfMs: Long,
) {
    L1(1, "Mới học", "Đi gần như ngẫu nhiên, hay bỏ quân – để làm quen luật đi.", 1, 0, 400, 0.6, 0, 0),
    L2(2, "Tập sự", "Thấy nước ăn quân trước mắt, còn nhiều sơ hở.", 2, 0, 180, 0.3, 0, 0),
    L3(3, "Phong trào", "Tính trước 2–3 nước, thỉnh thoảng đi chưa tối ưu.", 3, 0, 90, 0.15, 0, 0),
    L4(4, "Kỳ thủ", "Khoảng 1400 Elo – ít khi bỏ quân. Hợp người chơi phong trào.", 4, 0, 45, 0.07, 1400, 300),
    L5(5, "Khá", "Khoảng 1700 Elo – chơi chắc, biết đòn chiến thuật.", 5, 600, 0, 0.0, 1700, 400),
    L6(6, "Giỏi", "Khoảng 2000 Elo – trình độ câu lạc bộ mạnh.", 7, 1000, 0, 0.0, 2000, 600),
    L7(7, "Kiện tướng", "Khoảng 2400 Elo – rất khó thắng.", 10, 2000, 0, 0.0, 2400, 900),
    L8(8, "Đại kiện tướng", "Stockfish sức tối đa – gần như không thể thắng.", 16, 3000, 0, 0.0, 0, 1500);

    val title: String get() = t(titleVi, TITLES_EN[id], TITLES_ZH[id], TITLES_JA[id])
    val blurb: String get() = t(blurbVi, BLURBS_EN[id], BLURBS_ZH[id], BLURBS_JA[id])

    /** Cấp này dùng Stockfish khi có. */
    val usesStockfish: Boolean get() = id >= 4

    companion object {
        private val TITLES_EN = listOf("", "Beginner", "Novice", "Casual player", "Club player", "Strong", "Expert", "Master", "Grandmaster")
        private val TITLES_ZH = listOf("", "初学", "入门", "业余", "棋手", "好手", "高手", "大师", "特级大师")
        private val TITLES_JA = listOf("", "入門", "初心者", "アマ初級", "アマ中級", "アマ上級", "強豪", "マスター", "グランドマスター")
        private val BLURBS_EN = listOf(
            "", "Moves almost at random and drops pieces – for learning how the pieces move.", "Sees captures right in front of it, still leaves gaps.",
            "Looks 2–3 moves ahead, sometimes plays sub-optimally.", "About 1400 Elo – rarely drops pieces. Good for casual players.",
            "About 1700 Elo – solid, knows tactics.", "About 2000 Elo – strong club level.", "About 2400 Elo – very hard to beat.",
            "Full-strength Stockfish – almost unbeatable.",
        )
        private val BLURBS_ZH = listOf(
            "", "几乎乱走、常丢子——用来熟悉走法。", "能看到眼前的吃子，漏洞还多。", "能算2–3步，偶尔走得不够好。",
            "约1400等级分——很少丢子。适合业余棋友。", "约1700等级分——下得稳，懂战术。", "约2000等级分——强俱乐部水平。",
            "约2400等级分——很难战胜。", "全力Stockfish——几乎无法战胜。",
        )
        private val BLURBS_JA = listOf(
            "", "ほぼランダムに指し、駒をよく取られます – 駒の動きに慣れるために。", "目の前の駒取りは見えますが、まだ隙が多いです。",
            "2〜3手先まで読みますが、時々ミスをします。", "約1400 Elo – 駒をただで取られることはまれ。アマ向け。",
            "約1700 Elo – 堅実で戦術も知っています。", "約2000 Elo – 強いクラブレベル。", "約2400 Elo – 勝つのはとても難しいです。",
            "全力のStockfish – ほぼ勝てません。",
        )
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: L4
    }
}

/** Máy chọn nước theo cấp. Chạy trên luồng nền. */
class AiPlayer(private val search: Search = Search(), private val random: Random = Random.Default) {

    fun cancel() { search.cancel = true; Engines.external()?.stop() }

    /**
     * [pos] phải là bản sao riêng của luồng gọi (xem [Game.positionCopy]); [history] = các nước đã đi (SAN).
     * [startFen]/[uciMoves]: ván từ đầu (để Stockfish biết lịch sử – luật lặp thế); bỏ trống thì gửi FEN hiện tại.
     */
    fun chooseMove(
        pos: Position, level: Level, history: List<String> = emptyList(), useBook: Boolean = true,
        startFen: String? = null, uciMoves: List<String> = emptyList(),
    ): Int {
        val p = pos
        val legal = p.legalMoves()
        if (legal.isEmpty()) return 0
        if (legal.size == 1) return legal[0]
        // Luôn đi nước chiếu hết ngay nếu thấy (cấp ≥ 2)
        if (level.id >= 2) mateInOne(p, legal)?.let { return it }
        // Khai cuộc: cấp ≥ 3 đi theo sách nếu có (đa dạng ván)
        if (useBook && level.id >= 3) OpeningBook.pick(p, random, history)?.let { if (it in legal) return it }
        if (level.usesStockfish) {
            val sf = Engines.withExternal { e ->
                val fen = startFen ?: p.toFen()
                val moves = if (startFen != null) uciMoves else emptyList()
                e.search(fen, moves, GoLimit(movetimeMs = level.sfMs), elo = level.elo.takeIf { it > 0 })
            }
            if (sf != null) {
                val m = Moves.fromUci(p, sf.bestMove)
                if (m != 0) return m
            }
        }
        if (level.noise == 0) {
            return search.think(p, level.depth, level.timeMs).move.takeIf { it != 0 } ?: legal[0]
        }
        // Cấp thấp: chấm điểm mọi nước rồi chọn ngẫu nhiên trong nhóm "đủ tốt"
        val scores = search.scoreAll(p, level.depth, legal)
        val best = scores.max()
        val sloppy = random.nextDouble() < level.errRate
        val margin = if (sloppy) level.noise else level.noise / 6
        val candidates = legal.indices.filter { scores[it] >= best - margin }
            // cấp ≥ 3 không bỏ quân lớn khi "đi ẩu"
            .filter { level.id < 3 || scores[it] >= best - 350 }
        val idx = if (candidates.isEmpty()) scores.indices.maxBy { scores[it] } else candidates[random.nextInt(candidates.size)]
        return legal[idx]
    }

    private fun mateInOne(pos: Position, legal: IntArray): Int? {
        for (m in legal) {
            pos.make(m)
            val mate = pos.legalMoves().isEmpty() && pos.inCheck()
            pos.unmake()
            if (mate) return m
        }
        return null
    }
}
