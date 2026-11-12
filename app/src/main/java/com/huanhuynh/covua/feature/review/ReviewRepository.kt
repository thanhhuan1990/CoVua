package com.huanhuynh.covua.feature.review

import com.huanhuynh.covua.coach.CoachTags
import com.huanhuynh.covua.data.db.CardStatusDb
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.data.db.ReviewCardEntity
import com.huanhuynh.covua.data.db.UserDb
import com.huanhuynh.covua.engine.EngineInfo
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import com.huanhuynh.covua.training.CardSchedule
import com.huanhuynh.covua.training.CardStatus
import com.huanhuynh.covua.training.DayClock
import com.huanhuynh.covua.training.LeitnerScheduler
import com.huanhuynh.covua.training.MistakeExtractor
import com.huanhuynh.covua.training.PlayerMoveEval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Cấu hình Sổ tay (đọc từ Remote Config). */
data class ReviewConfig(
    val enabled: Boolean = true,
    val minLevel: Int = 2,
    val minLossCp: Int = 150,
    val maxCardsPerGame: Int = 3,
    val maxOkMoves: Int = 3,
    val intervals: List<Int> = LeitnerScheduler.DEFAULT_INTERVALS,
    val verifyDepth: Int = 6,
)

/**
 * Sổ tay sai lầm: tạo thẻ sau ván (kiểm sâu riêng một engine), lên lịch Leitner, chấm câu trả lời.
 * Engine kiểm thẻ chạy trên luồng riêng, không dùng chung bảng băm với ván đang chơi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRepository(
    private val db: UserDb,
    private val clock: DayClock,
    private val scope: CoroutineScope,
    private val config: () -> ReviewConfig,
    private val log: (String, Map<String, String>) -> Unit,
) {
    private val engine = Dispatchers.Default.limitedParallelism(1)
    private val search by lazy { Search(16) }
    private var verifyJob: Job? = null

    /** Số thẻ vừa ghi từ ván gần nhất (null = đang kiểm / chưa có). */
    private val _lastGameCards = MutableStateFlow<Pair<Long, Int>?>(null)
    val lastGameCards: StateFlow<Pair<Long, Int>?> = _lastGameCards

    private fun scheduler() = LeitnerScheduler(config().intervals)

    fun dueCountFlow(): Flow<Int> = db.cards().dueCountFlow(clock.today())
    fun allFlow(): Flow<List<ReviewCardEntity>> = db.cards().allFlow()
    suspend fun due(): List<ReviewCardEntity> = db.cards().due(clock.today())
    suspend fun card(id: Long) = db.cards().get(id)

    /** Ván mới bắt đầu → huỷ việc kiểm thẻ đang chạy. */
    fun cancelVerification() { verifyJob?.cancel(); search.cancel = true }

    /** Sau ván: chọn ứng viên → kiểm sâu (độ sâu 6, ≤ 2 s/thẻ) → ghi thẻ. */
    fun processFinishedGame(game: GameEntity, moves: List<PlayerMoveEval>) {
        val cfg = config()
        if (!cfg.enabled || game.legacy) { _lastGameCards.value = game.id to 0; return }
        val ex = MistakeExtractor(cfg.minLevel, cfg.minLossCp, cfg.maxCardsPerGame, cfg.maxOkMoves)
        val candidates = ex.extract(game.level, game.legacy, moves)
        _lastGameCards.value = null
        if (candidates.isEmpty()) { _lastGameCards.value = game.id to 0; return }
        verifyJob?.cancel()
        verifyJob = scope.launch {
            var written = 0
            for (c in candidates) {
                coroutineContext.ensureActive()
                val v = withContext(engine) { verify(c.eval.fenBefore, c.eval.move, cfg.verifyDepth, 2000) }
                val reason = when {
                    v == null -> "verify"
                    v.loss < cfg.minLossCp -> "shallow"
                    v.okMoves.size > cfg.maxOkMoves -> "ambiguous"
                    else -> null
                }
                if (reason != null) { log("review_card_dropped", mapOf("reason" to reason)); continue }
                if (addCard(game, c.eval, c.startBox, v!!)) written++
            }
            _lastGameCards.value = game.id to written
        }
    }

    data class Verified(val best: Int, val loss: Int, val zobrist: Long, val okMoves: List<Int>)

    /** Kiểm sâu: nước tốt nhất và mức mất của nước đã đi. null = không kết luận được. */
    private fun verify(fen: String, played: Int, depth: Int, timeMs: Long): Verified? {
        val pos = runCatching { Position.fromFen(fen) }.getOrNull() ?: return null
        if (!pos.isLegal(played)) return null
        search.clear()
        val best = search.think(pos, depth, timeMs / 2)
        if (best.move == 0 || search.cancel) return null
        // "Nước đạt" (mất ≤ 50 cp): chấm nhanh mọi nước, luôn gồm nước tốt nhất ở độ sâu kiểm lại
        val legal = pos.legalMoves()
        val sc = search.scoreAll(pos, 3, legal)
        val top = sc.maxOrNull() ?: best.score
        val ok = (listOf(best.move) + legal.indices.filter { top - sc[it] <= OK_LOSS }.sortedByDescending { sc[it] }.map { legal[it] }).distinct()
        if (best.move == played) return Verified(best.move, 0, pos.hash, ok)
        pos.make(played)
        val after = search.think(pos, depth - 1, timeMs / 2)
        pos.unmake()
        if (search.cancel) return null
        val playedScore = -after.score
        return Verified(best.move, (best.score - playedScore).coerceAtLeast(0), pos.hash, ok.filter { it != played })
    }

    private suspend fun addCard(game: GameEntity, e: PlayerMoveEval, startBox: Int, v: Verified): Boolean {
        val today = clock.today()
        val existing = db.cards().byZobrist(v.zobrist)
        if (existing != null) {
            // Lặp lại đúng sai lầm cũ → về hộp 1, ôn ngày mai
            val s = scheduler().relapse(existing.schedule(), today)
            db.cards().update(existing.withSchedule(s).copy(status = CardStatusDb.ACTIVE, gameId = game.id, ply = e.ply, level = game.level))
            return true
        }
        // Kho đầy (300 thẻ ACTIVE): xoá thẻ "Đã sửa" cũ nhất trước; vẫn đầy thì không tạo thêm
        if (!makeRoom()) { log("review_card_dropped", mapOf("reason" to "full")); return false }
        val s = scheduler().newCard(startBox, today)
        val id = db.cards().insert(
            ReviewCardEntity(
                fen = e.fenBefore, zobrist = v.zobrist, playedMove = e.move, bestMoves = v.okMoves.joinToString(","),
                themeTags = e.tags, box = s.box, dueDay = s.dueDay, gameId = game.id, ply = e.ply, level = game.level,
                engineVersion = EngineInfo.VERSION, createdAt = clock.nowMillis(),
            ),
        )
        if (id <= 0) return false
        log("review_card_created", mapOf("theme" to CoachTags.theme(e.tags), "level" to game.level.toString(), "source" to game.mode.lowercase(), "box" to s.box.toString()))
        return true
    }

    /** true nếu còn chỗ cho thẻ mới. */
    private suspend fun makeRoom(cap: Int = MAX_ACTIVE): Boolean {
        if (db.cards().activeCount() < cap) return true
        db.cards().dropOldestGraduated(1)
        return db.cards().activeCount() < cap
    }

    /** Lời thầy cũ cho nước sai (move_eval.coach_text); null nếu đã bị tỉa. */
    suspend fun oldCoachText(card: ReviewCardEntity): String? {
        val g = card.gameId ?: return null
        val p = card.ply ?: return null
        return db.evals().get(g, p)?.let { e -> listOfNotNull(e.coachTitle, e.coachText?.lineSequence()?.firstOrNull()).joinToString(". ") }?.takeIf { it.isNotBlank() }
    }

    /** Sinh lại lời thầy bằng Coach (khi move_eval đã bị tỉa). */
    suspend fun regenerateCoachText(card: ReviewCardEntity): String? = withContext(engine) {
        runCatching {
            val pos = Position.fromFen(card.fen)
            val r = com.huanhuynh.covua.coach.Coach(Search(16)).review(pos, card.playedMove, emptyList(), useBook = false)
            r.title + ". " + r.body
        }.getOrNull()
    }

    sealed interface Grade {
        data object Correct : Grade
        data class Wrong(val best: Int) : Grade
    }

    /** Chấm nước trả lời: nằm trong best_moves, hoặc kiểm nhanh (độ sâu 4, ≤ 0,5 s) mất ≤ accept_loss_cp. */
    suspend fun grade(card: ReviewCardEntity, move: Int): Grade = withContext(engine) {
        val best = card.bestMoveList()
        if (move in best) return@withContext Grade.Correct
        val pos = Position.fromFen(card.fen)
        if (!pos.isLegal(move)) return@withContext Grade.Wrong(best.first())
        search.clear()
        val b = search.think(pos, 4, 250)
        pos.make(move)
        val a = search.think(pos, 3, 250)
        pos.unmake()
        val loss = b.score - (-a.score)
        if (loss <= card.acceptLossCp) {
            db.cards().update(card.copy(bestMoves = (best + move).distinct().joinToString(",")))
            Grade.Correct
        } else Grade.Wrong(best.first())
    }

    /** Đòn đáp trả của đối phương sau nước [move] (để vẽ mũi tên đỏ). */
    suspend fun refutation(card: ReviewCardEntity, move: Int): Int = withContext(engine) {
        runCatching {
            val pos = Position.fromFen(card.fen)
            pos.make(move)
            search.think(pos, 3, 300).move
        }.getOrDefault(0)
    }

    /** Ghi kết quả ôn. Trả thẻ mới (GRADUATED = "Đã sửa"). */
    suspend fun answer(card: ReviewCardEntity, correct: Boolean, hintLevel: Int): ReviewCardEntity {
        val ok = correct && hintLevel < 2 // xem đến gợi ý mức 2 thì coi như chưa tự làm được
        val s = scheduler().answer(card.schedule(), ok, clock.today())
        val next = card.withSchedule(s).copy(lastReviewedAt = clock.nowMillis())
        db.cards().update(next)
        val theme = CoachTags.theme(card.themeTags)
        log("review_card_done", mapOf("box" to next.box.toString(), "ok" to ok.toString(), "theme" to theme, "hint" to hintLevel.toString()))
        if (next.status == CardStatusDb.GRADUATED) log("review_card_graduated", mapOf("theme" to theme))
        return next
    }

    /** Thẻ của engine cũ: kiểm lại khi đến lượt; không còn là sai lầm → SUSPENDED. */
    suspend fun revalidate(card: ReviewCardEntity): ReviewCardEntity? {
        if (card.engineVersion >= EngineInfo.VERSION) return card
        val cfg = config()
        val v = withContext(engine) { verify(card.fen, card.playedMove, cfg.verifyDepth, 2000) }
        return if (v == null || v.loss < cfg.minLossCp) {
            db.cards().update(card.copy(status = CardStatusDb.SUSPENDED)); null
        } else card.copy(bestMoves = (listOf(v.best) + card.bestMoveList()).distinct().joinToString(","), engineVersion = EngineInfo.VERSION)
            .also { db.cards().update(it) }
    }

    companion object {
        const val OK_LOSS = 50
        const val MAX_ACTIVE = 300

        fun ReviewCardEntity.schedule() = CardSchedule(box, dueDay, reps, lapses,
            when (status) { CardStatusDb.GRADUATED -> CardStatus.GRADUATED; CardStatusDb.SUSPENDED -> CardStatus.SUSPENDED; else -> CardStatus.ACTIVE })

        fun ReviewCardEntity.withSchedule(s: CardSchedule) = copy(
            box = s.box, dueDay = s.dueDay, reps = s.reps, lapses = s.lapses,
            status = when (s.status) { CardStatus.GRADUATED -> CardStatusDb.GRADUATED; CardStatus.SUSPENDED -> CardStatusDb.SUSPENDED; else -> CardStatusDb.ACTIVE },
        )
    }
}
