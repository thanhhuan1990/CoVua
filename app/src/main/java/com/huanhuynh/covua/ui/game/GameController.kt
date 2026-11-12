package com.huanhuynh.covua.ui.game

import com.huanhuynh.covua.i18n.t

import com.huanhuynh.covua.coach.Coach
import com.huanhuynh.covua.coach.Grade
import com.huanhuynh.covua.coach.Hint
import com.huanhuynh.covua.coach.MoveReview
import com.huanhuynh.covua.coach.TurnAnalysis
import com.huanhuynh.covua.coach.Warning
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.data.GameArchiveRepository
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.data.SettingsRepository
import com.huanhuynh.covua.data.db.EndReason
import com.huanhuynh.covua.data.db.GameEntity
import com.huanhuynh.covua.data.db.GameMode
import com.huanhuynh.covua.data.db.GameStatus
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.engine.AiPlayer
import com.huanhuynh.covua.engine.Game
import com.huanhuynh.covua.engine.Handicap
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.Outcome
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.engine.Search
import com.huanhuynh.covua.training.DrawPolicy
import com.huanhuynh.covua.training.FinishedGame
import com.huanhuynh.covua.training.LevelAdvisor
import com.huanhuynh.covua.training.LevelSuggestion
import com.huanhuynh.covua.training.PlayerMoveEval
import com.huanhuynh.covua.training.RetryKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Thẻ thầy đang hiện dưới bàn cờ. */
sealed interface CoachCard {
    data object YourTurn : CoachCard
    data object Thinking : CoachCard
    data class Review(val review: MoveReview, val opponentNote: String? = null) : CoachCard
    data class Danger(val warning: Warning, val move: Int) : CoachCard
    data class HintCard(val hint: Hint, val revealed: Boolean) : CoachCard
    data class Info(val title: String, val body: String) : CoachCard
}

/** Hộp thoại "Có nước tốt hơn": thế trước nước của người chơi + lời thầy. */
data class BetterPrompt(val review: MoveReview, val boardBefore: IntArray, val ply: Int)

data class GameUiState(
    val prompt: BetterPrompt? = null,
    val fen: String = "",
    val board: IntArray = IntArray(64),
    val humanSide: Int = 1,
    val level: Level = Level.L4,
    val codes: List<String> = emptyList(),
    val lastMove: Int = 0,
    val selected: Int = -1,
    val targets: Set<Int> = emptySet(),
    val danger: Map<Int, Warning> = emptyMap(),
    val arrows: List<Arrow> = emptyList(),
    val checkSquare: Int = -1,
    val aiThinking: Boolean = false,
    val card: CoachCard = CoachCard.YourTurn,
    val undosLeft: Int = FREE_UNDOS,
    val outcome: Outcome = Outcome.NONE,
    val outcomeText: String = "",
    /** Lời mời khoe thành tích (chỉ khi đáng khoe: thắng lần đầu ở cấp này, cấp cao, chính xác cao, chuỗi thắng). */
    val shareNote: String? = null,
    val explain: Boolean = true,
    val warn: Boolean = true,
    val hintsOn: Boolean = true,
    val undoOn: Boolean = true,
    /** Chỉ đánh cờ: không hiện thẻ thầy (lời thầy, cảnh báo, gợi ý, hộp thoại nước tốt hơn). */
    val playOnly: Boolean = false,
    /** Số lần thay đổi bàn cờ – để Compose biết vẽ lại / chạy hoạt ảnh. */
    val version: Int = 0,
    /** Bên đi trước ở thế đầu (ván từ thế cờ có thể Đen đi trước). */
    val startSide: Int = 1,
    val handicap: Handicap = Handicap.NONE,
    val mode: String = GameMode.PRACTICE,
    /** Thầy gợi ý cấp sau ván (người chơi tự chọn). */
    val suggestion: LevelSuggestion? = null,
    /** Vừa lên danh hiệu mới. */
    val titleUp: String? = null,
    /** Vừa đạt mốc Lịch tập (3, 7, 14…). */
    val streakMilestone: Int? = null,
    /** Lời đáp khi xin hoà (bị từ chối / còn sớm). */
    val drawNote: String? = null,
    /** Tốt sắp phong cấp: nước (from, to) đang chờ người chơi chọn quân (0 = không). */
    val promotion: Int = 0,
) {
    val humanToMove get() = outcome == Outcome.NONE && !aiThinking && sideToMove == humanSide
    val sideToMove get() = if (codes.size % 2 == 0) startSide else -startSide
    val undoUnlimited get() = level.id <= 4
    val gameLabel get() = when {
        mode == GameMode.FROM_POSITION -> t("Chơi tiếp từ thế cờ", "Play from position", "从局面续下", "局面から続ける")
        handicap != Handicap.NONE -> handicap.gameLabel
        else -> ""
    }

    companion object {
        const val FREE_UNDOS = 3
    }
}

data class Arrow(val from: Int, val to: Int, val kind: ArrowKind)
enum class ArrowKind { HINT, THREAT, BETTER }

/** Một mốc trong ván để phân tích: nước của người chơi kèm lời thầy (+ thế trước nước, số nước đạt – cho Sổ tay). */
data class ReviewedMove(val ply: Int, val review: MoveReview, val fenBefore: String = "", val okMoveCount: Int = 0)

/**
 * Điều khiển một ván với máy. Sống trong AppContainer (qua được xoay màn / chuyển màn).
 * Mọi tính toán engine chạy tuần tự trên [engineDispatcher] (một luồng) với bản sao thế cờ.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameController(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val app: AppContainer,
) {
    private val archive: GameArchiveRepository get() = app.archive
    /** id ván trong Room (0 = chưa tạo / Room lỗi → chỉ dùng DataStore). */
    private var gameId = 0L
    private var gameIdJob: Job? = null
    private var undoCount = 0
    private var hintCount = 0
    /** Nước sai đã được đi lại (ply → cách đi lại) – vẫn có thể thành thẻ Sổ tay. */
    private val retried = LinkedHashMap<Int, PlayerMoveEval>()
    private var drawDeclinedAtPly: Int? = null
    private val drawPolicy = DrawPolicy()

    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val ai = AiPlayer(Search(18))
    private val coach = Coach(Search(16))

    private var game = Game()
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    /** Lời thầy cho từng nước của người chơi (để phân tích ván). */
    val reviews = ArrayList<ReviewedMove>()
    /** Điểm thế (góc Trắng) ở đầu mỗi lượt người chơi, để vẽ biểu đồ lợi thế. */
    val evalCurve = ArrayList<Int>()

    private var turnAnalysis: TurnAnalysis? = null
    private var analysisJob: Job? = null
    private var aiJob: Job? = null
    private var pendingDanger = -1
    private var hintStage = 0
    var lastOpponentNote: String? = null
        private set

    val hasGame get() = game.moves.isNotEmpty() && _state.value.outcome == Outcome.NONE
    fun currentGame(): Game = game

    // ---------------- Bắt đầu / tiếp tục ----------------

    /**
     * Ván mới. [handicap] = chấp quân; [fromFen] = chơi tiếp từ một thế cờ (Replay / bài tập).
     * Thế đầu không chuẩn → không dùng sách khai cuộc và không gọi tên khai cuộc.
     */
    fun newGame(
        level: Level, humanSide: Int, explain: Boolean, warn: Boolean, hints: Boolean, undo: Boolean,
        handicap: Handicap = Handicap.NONE, fromFen: String? = null, playOnly: Boolean = false,
    ) {
        cancelJobs()
        app.reviews.cancelVerification()
        val startFen = fromFen ?: handicap.startFen(humanSide)
        game = Game(startFen)
        reviews.clear(); evalCurve.clear(); retried.clear()
        undoCount = 0; hintCount = 0; drawDeclinedAtPly = null
        lastOpponentNote = null
        val mode = when {
            fromFen != null -> GameMode.FROM_POSITION
            handicap != Handicap.NONE -> GameMode.HANDICAP
            else -> GameMode.PRACTICE
        }
        _state.value = GameUiState(
            humanSide = humanSide, level = level, explain = explain && !playOnly, warn = warn && !playOnly, hintsOn = hints && !playOnly, undoOn = undo, playOnly = playOnly,
            startSide = game.pos.side, handicap = if (fromFen != null) Handicap.NONE else handicap, mode = mode,
            card = if (mode == GameMode.PRACTICE || playOnly) CoachCard.YourTurn else CoachCard.Info(
                if (mode == GameMode.HANDICAP) handicap.gameLabel else t("Chơi tiếp từ thế này", "Play on from here", "从这里继续下", "ここから続きを指す"),
                if (mode == GameMode.HANDICAP) t("Thế đầu đã bớt quân nên thầy không gọi tên khai cuộc. Cứ đánh thoải mái nhé.", "Pieces have been removed, so openings won't be named. Enjoy the game.", "开局少了棋子，老师不点评开局名称。放心下吧。", "駒を減らした局面なので、定跡名は出しません。気軽に楽しみましょう。")
                else t("Thầy vẫn kèm từng nước như ván thường.", "Your coach still comments on every move.", "老师照常点评每一步。", "先生は引き続き一手ごとに解説します。"),
            ),
        ).let(::withBoard)
        gameId = 0
        gameIdJob = scope.launch {
            gameId = runCatching { archive.start(mode, level.id, handicap.id.takeIf { handicap != Handicap.NONE && fromFen == null }, humanSide, startFen) }.getOrDefault(0L)
        }
        app.events.log(Ev.GAME_START, mapOf("level" to level.id.toString(), "handicap" to handicap.id, "mode" to mode, "side" to humanSide.toString()))
        afterPositionChanged()
    }

    /**
     * Bật/tắt "Chỉ đánh cờ" giữa ván. Bật lại thầy → dùng các lựa chọn thầy kèm đã lưu
     * (nếu đã tắt cả ba thì bật cả ba, vì người chơi vừa bấm "Bật thầy kèm").
     */
    fun setPlayOnly(on: Boolean, explain: Boolean, warn: Boolean, hints: Boolean) {
        val any = explain || warn || hints
        _state.update {
            if (on) it.copy(playOnly = true, explain = false, warn = false, hintsOn = false, danger = emptyMap(), prompt = null,
                arrows = emptyList(), card = CoachCard.YourTurn)
            else it.copy(playOnly = false, explain = !any || explain, warn = !any || warn, hintsOn = !any || hints,
                card = CoachCard.Info(t("Thầy kèm đã bật", "Coaching is on", "老师陪练已开启", "先生のサポートをオンにしました"),
                    t("Từ nước tiếp theo thầy sẽ nhận xét, cảnh báo và gợi ý.", "From your next move your coach will comment, warn and give hints.", "从下一步起老师会点评、警告并提示。", "次の手から先生が解説・警告・ヒントを出します。")))
        }
        if (on) { pendingDanger = -1; if (hintStage != 0) hintStage = 0 }
        else _state.value.selected.takeIf { it >= 0 }?.let { computeWarnings(it) }
    }

    private val standardStart get() = game.startFen == Position.START_FEN

    fun resume(serialized: String, level: Level, humanSide: Int, explain: Boolean, warn: Boolean, hints: Boolean, undo: Boolean, playOnly: Boolean = false): Boolean {
        val g = Game.deserialize(serialized) ?: return false
        cancelJobs()
        game = g
        gameId = 0
        reviews.clear(); evalCurve.clear(); retried.clear()
        _state.value = GameUiState(
            humanSide = humanSide, level = level, explain = explain && !playOnly, warn = warn && !playOnly, hintsOn = hints && !playOnly, undoOn = undo, playOnly = playOnly,
            startSide = Position.fromFen(g.startFen).side,
            card = CoachCard.Info(t("Tiếp tục ván", "Resume game", "继续棋局", "対局を再開"), if (game.sideToMove == humanSide) t("Thầy đã sẵn sàng. Đến lượt bạn.", "Your coach is ready. Your turn.", "老师准备好了。轮到你。", "先生の準備ができました。あなたの番です。") else t("Thầy đã sẵn sàng. Máy đang đi.", "Your coach is ready. The computer is moving.", "老师准备好了。电脑正在走。", "先生の準備ができました。コンピュータが考えています。")),
        ).let(::withBoard)
        afterPositionChanged()
        return true
    }

    /** Tiếp tục ván từ Room (có cả lời thầy từng nước). false → gọi [resume] với DataStore. */
    fun resumeFrom(g: GameEntity, evals: List<com.huanhuynh.covua.data.db.MoveEvalEntity>, explain: Boolean, warn: Boolean, hints: Boolean, undo: Boolean, playOnly: Boolean = false): Boolean {
        if (g.status != GameStatus.IN_PROGRESS) return false
        val restored = runCatching {
            Game(g.startFen).also { gm -> g.moveList().forEach { m -> check(gm.play(m)) } }
        }.getOrNull() ?: return false
        cancelJobs()
        game = restored
        gameId = g.id
        undoCount = g.undoCount; hintCount = g.hintCount
        reviews.clear(); evalCurve.clear(); retried.clear()
        evals.filter { it.ply < restored.moves.size }.forEach { e ->
            reviews += ReviewedMove(e.ply, GameArchiveRepository.toReview(e), restored.positionAt(e.ply).toFen(), 0)
        }
        val h = Handicap.of(g.handicap)
        _state.value = GameUiState(
            humanSide = g.playerSide, level = Level.of(g.level), explain = explain && !playOnly, warn = warn && !playOnly, hintsOn = hints && !playOnly, undoOn = undo, playOnly = playOnly,
            startSide = Position.fromFen(g.startFen).side, handicap = h, mode = g.mode,
            card = CoachCard.Info(t("Tiếp tục ván", "Resume game", "继续棋局", "対局を再開"), if (game.sideToMove == g.playerSide) t("Thầy đã sẵn sàng. Đến lượt bạn.", "Your coach is ready. Your turn.", "老师准备好了。轮到你。", "先生の準備ができました。あなたの番です。") else t("Thầy đã sẵn sàng. Máy đang đi.", "Your coach is ready. The computer is moving.", "老师准备好了。电脑正在走。", "先生の準備ができました。コンピュータが考えています。")),
        ).let(::withBoard)
        afterPositionChanged()
        return true
    }

    private fun withBoard(s: GameUiState): GameUiState {
        val pos = game.pos
        val inCheck = pos.inCheck()
        return s.copy(
            fen = pos.toFen(),
            board = pos.board.copyOf(),
            codes = game.shownAll(),
            lastMove = game.moves.lastOrNull() ?: 0,
            checkSquare = if (inCheck) pos.kingSquare(pos.side) else -1,
            version = s.version + 1,
        )
    }

    private fun cancelJobs() {
        analysisJob?.cancel(); aiJob?.cancel()
        ai.cancel(); coach.cancel()
        turnAnalysis = null
        pendingDanger = -1
        hintStage = 0
    }

    private fun afterPositionChanged() {
        val outcome = game.outcome()
        if (outcome != Outcome.NONE) {
            finish(outcome)
            return
        }
        val st = _state.value
        if (game.sideToMove == st.humanSide) startHumanTurn() else startAiTurn()
        persist()
    }

    // ---------------- Lượt người chơi ----------------

    private fun startHumanTurn() {
        turnAnalysis = null
        hintStage = 0
        val copy = game.positionCopy()
        analysisJob = scope.launch {
            val ta = withContext(engineDispatcher) { coach.analyzeTurn(copy, 3) }
            turnAnalysis = ta
            val white = if (Search.isMate(ta.bestScore)) (if (ta.bestScore > 0) 1500 else -1500) else ta.bestScore.coerceIn(-1500, 1500)
            evalCurve += white * game.sideToMove
            // Nếu người chơi đang cầm quân thì cập nhật cảnh báo
            val sel = _state.value.selected
            if (sel >= 0) computeWarnings(sel)
        }
    }

    fun onTap(sq: Int) {
        val st = _state.value
        if (!st.humanToMove) return
        val p = game.pos[sq]
        val mine = p != 0 && (p > 0) == (st.humanSide > 0)
        if (st.selected >= 0 && sq in st.targets) {
            val legal = game.legalMoves().filter { Moves.from(it) == st.selected && Moves.to(it) == sq }
            if (legal.size > 1) { // phong cấp: hỏi chọn quân
                _state.update { it.copy(promotion = Moves.encode(st.selected, sq)) }
                return
            }
            val m = legal.firstOrNull() ?: Moves.encode(st.selected, sq)
            val w = st.danger[sq]
            if (w != null && st.warn && pendingDanger != sq) {
                pendingDanger = sq
                _state.update { it.copy(card = CoachCard.Danger(w, m)) }
                return
            }
            playHuman(m)
            return
        }
        if (mine) {
            if (st.selected == sq) { deselect(); return }
            pendingDanger = -1
            val targets = game.legalMoves().filter { Moves.from(it) == sq }.map { Moves.to(it) }.toSet()
            _state.update {
                it.copy(selected = sq, targets = targets, danger = emptyMap(), arrows = it.arrows.filter { a -> a.kind == ArrowKind.HINT },
                    card = if (it.card is CoachCard.Danger) CoachCard.YourTurn else it.card)
            }
            if (st.warn) computeWarnings(sq)
        } else deselect()
    }

    private fun deselect() {
        pendingDanger = -1
        _state.update { it.copy(selected = -1, targets = emptySet(), danger = emptyMap(), card = if (it.card is CoachCard.Danger) CoachCard.YourTurn else it.card) }
    }

    private fun computeWarnings(sq: Int) {
        val ta = turnAnalysis ?: return
        val copy = game.positionCopy()
        scope.launch {
            val w = withContext(engineDispatcher) { coach.warningsFor(copy, sq, ta) }
            if (_state.value.selected == sq) _state.update { it.copy(danger = w) }
        }
    }

    /** Vẫn đi nước nguy hiểm (sau khi đã đọc cảnh báo). */
    fun confirmDanger(m: Int) = playHuman(m)

    /** Chọn quân phong cấp ([type] = Position.QUEEN/ROOK/BISHOP/KNIGHT) cho nước đang chờ. */
    fun choosePromotion(type: Int) {
        val base = _state.value.promotion
        if (base == 0) return
        _state.update { it.copy(promotion = 0) }
        val m = Moves.encode(Moves.from(base), Moves.to(base), type)
        if (!game.pos.isLegal(m)) return
        val w = _state.value.danger[Moves.to(m)]
        if (w != null && _state.value.warn && pendingDanger != Moves.to(m) && type == Position.QUEEN) {
            pendingDanger = Moves.to(m)
            _state.update { it.copy(card = CoachCard.Danger(w, m)) }
            return
        }
        playHuman(m)
    }

    fun cancelPromotion() = _state.update { it.copy(promotion = 0) }

    fun cancelDanger() {
        pendingDanger = -1
        _state.update { it.copy(card = CoachCard.YourTurn, arrows = emptyList()) }
    }

    /** Bật/tắt hộp thoại nước tốt hơn (theo Cài đặt). */
    var betterDialog = true

    companion object {
        /** Hiện hộp thoại khi nước đi kém nước tốt nhất từ ~nửa Tốt trở lên (chênh nhỏ hơn chỉ hiện nút trong thẻ thầy). */
        const val BETTER_DIALOG_LOSS = 50
    }

    private fun playHuman(m: Int) {
        val before = game.positionCopy()
        val boardBefore = game.pos.board.copyOf()
        val history = game.codes.toList()
        val ta = turnAnalysis
        analysisJob?.cancel()
        val captured = game.pos[Moves.to(m)] != 0
        if (!game.play(m)) return
        moveSound(captured)
        pendingDanger = -1
        val ply = game.moves.size - 1
        _state.update {
            withBoard(it).copy(selected = -1, targets = emptySet(), danger = emptyMap(), arrows = emptyList(),
                card = if (it.explain) CoachCard.Thinking else CoachCard.YourTurn)
        }
        val fenBefore = before.toFen()
        val scoreBefore = ta?.bestScore ?: 0
        scope.launch {
            val review = withContext(engineDispatcher) { coach.review(before, m, history, ta, useBook = standardStart) }
            reviews += ReviewedMove(ply, review, fenBefore, review.okMoves.size)
            // Đi lại tự tìm: ghi nhận lần này có đạt không
            retried[ply]?.let { r -> if (r.retried == RetryKind.SELF && r.fenBefore == fenBefore) retried[ply] = r.copy(retryFoundGood = review.loss <= 50) }
            gameIdJob?.join()
            if (gameId != 0L) runCatching { archive.saveEval(gameId, ply, review, scoreBefore) }
            if (_state.value.explain) {
                val arrows = buildList {
                    if (review.grade.isBad && review.threat != 0) add(Arrow(Moves.from(review.threat), Moves.to(review.threat), ArrowKind.THREAT))
                    if (review.grade.isBad && review.better != 0) add(Arrow(Moves.from(review.better), Moves.to(review.better), ArrowKind.BETTER))
                }
                val prompt = if (betterDialog && review.loss >= BETTER_DIALOG_LOSS && review.betterDetails.isNotEmpty() && review.better != 0 && review.grade != Grade.BOOK &&
                    game.moves.size > ply && _state.value.outcome == Outcome.NONE
                ) BetterPrompt(review, boardBefore, ply) else null
                _state.update {
                    it.copy(card = CoachCard.Review(review), arrows = if (game.moves.size - 1 == ply) arrows else it.arrows, prompt = prompt)
                }
            }
            persist()
        }
        afterPositionChanged()
    }

    // ---------------- Lượt máy ----------------

    private fun startAiTurn() {
        val st = _state.value
        val copy = game.positionCopy()
        val history = game.codes.toList()
        _state.update { it.copy(aiThinking = true) }
        aiJob = scope.launch {
            val t0 = System.currentTimeMillis()
            val uci = game.moves.map(Moves::uci)
            val m = withContext(engineDispatcher) { ai.chooseMove(copy, st.level, history, useBook = standardStart, startFen = game.startFen, uciMoves = uci) }
            val wait = 450 - (System.currentTimeMillis() - t0)
            if (wait > 0) delay(wait)
            // Chờ người chơi đóng hộp thoại "nước tốt hơn" (có thể họ chọn đi lại)
            _state.first { it.prompt == null }
            if (m == 0) { finish(game.outcome().takeIf { it != Outcome.NONE } ?: Outcome.DRAW); return@launch }
            val before = game.positionCopy()
            val note = withContext(engineDispatcher) { coach.describeOpponent(before, m) }
            val captured = game.pos[Moves.to(m)] != 0
            game.play(m)
            moveSound(captured)
            lastOpponentNote = note
            _state.update { s ->
                val card = when (val c = s.card) {
                    is CoachCard.Review -> c.copy(opponentNote = note)
                    is CoachCard.Thinking -> CoachCard.Info(t("Lượt của bạn", "Your turn", "轮到你", "あなたの番"), note)
                    else -> if (s.explain) CoachCard.Info(t("Lượt của bạn", "Your turn", "轮到你", "あなたの番"), note) else CoachCard.YourTurn
                }
                withBoard(s).copy(aiThinking = false, card = card)
            }
            afterPositionChanged()
        }
    }

    // ---------------- Hộp thoại nước tốt hơn ----------------

    fun dismissPrompt() = _state.update { it.copy(prompt = null) }

    /** Mở lại hộp thoại cho lời thầy đang hiện (nếu nước đó vẫn còn đi lại được). */
    fun reopenPrompt() {
        val st = _state.value
        val r = (st.card as? CoachCard.Review)?.review ?: return
        val rm = reviews.lastOrNull { it.review === r } ?: return
        if (r.better == 0 || st.outcome != Outcome.NONE) return
        val boardBefore = game.positionAt(rm.ply).board.copyOf()
        _state.update { it.copy(prompt = BetterPrompt(r, boardBefore, rm.ply)) }
    }

    /** Lùi về trước nước [BetterPrompt.ply]; [playBetter] = đi luôn nước tốt hơn. Không tính vào số lần đi lại. */
    fun retryFromPrompt(playBetter: Boolean) {
        val pr = _state.value.prompt ?: return
        retriesSinceAd++
        cancelJobs()
        rememberRetried(pr.ply, if (playBetter) RetryKind.FOLLOWED_HINT else RetryKind.SELF)
        while (game.moves.size > pr.ply) game.undo()
        reviews.removeAll { it.ply >= pr.ply }
        truncateArchive(pr.ply)
        while (evalCurve.size > reviews.size) evalCurve.removeAt(evalCurve.lastIndex)
        _state.update {
            withBoard(it).copy(
                prompt = null, aiThinking = false, outcome = Outcome.NONE, outcomeText = "", shareNote = null,
                selected = -1, targets = emptySet(), danger = emptyMap(), arrows = emptyList(),
                card = CoachCard.Info(t("Đã đi lại", "Move taken back", "已悔棋", "待ったしました"), if (playBetter) t("Đi theo nước thầy gợi ý.", "Playing the suggested move.", "按老师建议走。", "先生のおすすめの手を指します。") else t("Thử tìm nước tốt hơn nhé – thầy vẫn bật cảnh báo.", "Try to find a better move – warnings are still on.", "试着找更好的着法——警告依然开启。", "もっと良い手を探してみましょう – 警告はオンのままです。")),
            )
        }
        if (playBetter && game.pos.isLegal(pr.review.better)) playHuman(pr.review.better) else afterPositionChanged()
    }

    // ---------------- Gợi ý / đi lại ----------------

    /** Số gợi ý mới / lần đi lại từ hộp thoại kể từ lần xem quảng cáo gần nhất (cả phiên, không reset theo ván). */
    var hintsSinceAd = 0
        private set
    var retriesSinceAd = 0
        private set
    fun resetHintAdCounter() { hintsSinceAd = 0 }
    /** Gợi ý ở bài tập / Sổ tay dùng chung bộ đếm "hint_free_between_ads" với ván cờ. */
    fun countSharedHint() { hintsSinceAd++ }
    fun resetRetryAdCounter() { retriesSinceAd = 0 }

    /** Lần bấm này là xin gợi ý MỚI (không phải bước 2 "Xem nước đi" của gợi ý đang hiện). */
    fun isNewHintRequest(): Boolean = !(hintStage == 1 && _state.value.card is CoachCard.HintCard)

    fun requestHint() {
        val st = _state.value
        if (!st.humanToMove) return
        if (!isNewHintRequest()) { revealHint(); return }
        hintsSinceAd++
        hintCount++
        val copy = game.positionCopy()
        val ta = turnAnalysis
        _state.update { it.copy(card = CoachCard.Thinking) }
        scope.launch {
            val h = withContext(engineDispatcher) { coach.hint(copy, ta, depth = maxOf(4, st.level.depth.coerceAtMost(6)), timeMs = 1200) }
            if (h == null) { _state.update { it.copy(card = CoachCard.YourTurn) }; return@launch }
            hintStage = 1
            _state.update {
                it.copy(card = CoachCard.HintCard(h, revealed = false),
                    selected = Moves.from(h.move), targets = emptySet(), arrows = emptyList())
            }
        }
    }

    private fun revealHint() {
        val c = _state.value.card as? CoachCard.HintCard ?: return
        hintStage = 2
        _state.update { it.copy(card = c.copy(revealed = true), arrows = listOf(Arrow(Moves.from(c.hint.move), Moves.to(c.hint.move), ArrowKind.HINT))) }
    }

    fun playHint() {
        val c = _state.value.card as? CoachCard.HintCard ?: return
        playHuman(c.hint.move)
    }

    fun addUndos(n: Int) = _state.update { it.copy(undosLeft = it.undosLeft + n) }

    fun canUndo(): Boolean {
        val st = _state.value
        return st.undoOn && st.outcome == Outcome.NONE && game.moves.isNotEmpty() && (st.undoUnlimited || st.undosLeft > 0) &&
            humanMovesCount() > 0
    }

    private fun humanMovesCount(): Int = game.moves.indices.count { i -> (if (i % 2 == 0) _state.value.startSide else -_state.value.startSide) == _state.value.humanSide }

    /** Đi lại: lùi đến trước nước gần nhất của người chơi. */
    fun undo() {
        if (!canUndo()) return
        cancelJobs()
        val human = _state.value.humanSide
        // lùi nước máy (nếu có) rồi nước người
        while (game.moves.isNotEmpty() && game.sideToMove == human) game.undo()
        if (game.moves.isNotEmpty()) game.undo()
        val ply = game.moves.size
        rememberRetried(ply, RetryKind.SELF)
        undoCount++
        reviews.removeAll { it.ply >= ply }
        truncateArchive(ply)
        while (evalCurve.size > reviews.size) evalCurve.removeAt(evalCurve.lastIndex)
        _state.update {
            withBoard(it).copy(
                aiThinking = false, selected = -1, targets = emptySet(), danger = emptyMap(), arrows = emptyList(),
                undosLeft = if (it.undoUnlimited) it.undosLeft else it.undosLeft - 1,
                card = CoachCard.Info(t("Đã đi lại", "Move taken back", "已悔棋", "待ったしました"), t("Thầy đã lùi về trước nước vừa rồi. Thử tìm nước tốt hơn nhé.", "Back to before your last move. Try to find a better one.", "已退回到上一步之前。试着找更好的着法。", "直前の手の前に戻りました。もっと良い手を探しましょう。")),
            )
        }
        afterPositionChanged()
    }

    /** Tiếng nước đi; nước kết thúc ván thì để tiếng thắng/thua/hoà (finish) phát. */
    private fun moveSound(captured: Boolean) {
        if (game.outcome() != Outcome.NONE) { if (captured) app.sounds.play(com.huanhuynh.covua.Sfx.CAPTURE) else app.sounds.play(com.huanhuynh.covua.Sfx.MOVE); return }
        app.sounds.move(captured, game.pos.inCheck())
    }

    private fun rememberRetried(ply: Int, kind: RetryKind) {
        val r = reviews.firstOrNull { it.ply == ply } ?: return
        retried[ply] = PlayerMoveEval(ply, r.fenBefore, r.review.move, r.review.loss, r.okMoveCount, r.review.tags, kind)
    }

    private fun truncateArchive(fromPly: Int) {
        val id = gameId
        if (id != 0L) scope.launch { runCatching { archive.truncate(id, fromPly) } }
    }

    fun resign() {
        if (_state.value.outcome != Outcome.NONE) return
        cancelJobs()
        finish(if (_state.value.humanSide > 0) Outcome.BLACK_WIN else Outcome.WHITE_WIN, resigned = true)
    }

    // ---------------- Xin hoà ----------------

    fun canOfferDraw(): Boolean {
        val st = _state.value
        return st.humanToMove && game.moves.isNotEmpty() && drawPolicy.canOffer(game.moves.size, drawDeclinedAtPly)
    }

    /** Máy nhận hoà khi đã qua nước 30 và máy không hơn quá nửa Tốt. */
    fun offerDraw() {
        if (!canOfferDraw()) return
        val copy = game.positionCopy()
        scope.launch {
            val humanScore = turnAnalysis?.bestScore ?: withContext(engineDispatcher) { coach.analyzeTurn(copy, 3).bestScore }
            val aiScore = -humanScore
            val d = drawPolicy.decide(game.moves.size, if (Search.isMate(aiScore)) 0 else aiScore, Search.isMate(aiScore) && aiScore > 0)
            app.events.log(Ev.DRAW_OFFER, mapOf("ply" to game.moves.size.toString(), "accepted" to (d is DrawPolicy.Decision.Accept).toString()))
            when (d) {
                is DrawPolicy.Decision.Accept -> { cancelJobs(); finish(Outcome.DRAW, agreed = true) }
                is DrawPolicy.Decision.Decline -> { drawDeclinedAtPly = game.moves.size; _state.update { it.copy(drawNote = d.reason + t(" Thử lại sau 10 nước nhé.", " Try again in 10 moves.", "10步后再试吧。", "10手後にもう一度どうぞ。")) } }
                is DrawPolicy.Decision.TooEarly -> _state.update { it.copy(drawNote = d.reason) }
            }
        }
    }

    fun clearDrawNote() = _state.update { it.copy(drawNote = null) }

    /** Người chơi chọn một gợi ý cấp của thầy (áp dụng cho ván mới). */
    fun ddaChoice(option: LevelSuggestion.Option?) {
        val st = _state.value
        val sug = st.suggestion ?: return
        app.events.log(Ev.DDA_CHOICE, mapOf("choice" to (option?.let { "level_${it.level}_${it.handicap.id}" } ?: "keep"), "type" to sug.type.name.lowercase()))
        _state.update { it.copy(suggestion = null) }
        scope.launch {
            if (option == null) {
                val n = runCatching { app.db.games().finishedCount() }.getOrDefault(0)
                app.player.update { it.copy(ddaDeclinedUntilGame = n) }
            } else {
                settings.setLevel(option.level)
                settings.setHandicap(option.handicap.id)
            }
        }
    }

    // ---------------- Kết thúc ----------------

    private fun finish(outcome: Outcome, resigned: Boolean = false, agreed: Boolean = false) {
        val human = _state.value.humanSide
        val result = when (outcome) {
            Outcome.WHITE_WIN -> if (human > 0) 1 else -1
            Outcome.BLACK_WIN -> if (human < 0) 1 else -1
            else -> 0
        }
        val mated = game.pos.legalMoves().isEmpty() && game.pos.inCheck()
        val text = when {
            resigned -> t("Bạn xin thua", "You resigned", "你认输了", "投了しました")
            agreed -> t("Hoà – thế cờ cân bằng, máy đồng ý hoà", "Draw – the position is equal, the computer agreed", "和棋——局面均势，电脑同意和棋", "引き分け – 互角なのでコンピュータが同意しました")
            result == 1 -> if (mated) t("Bạn thắng – chiếu hết!", "You win – checkmate!", "你赢了——将死！", "あなたの勝ち – チェックメイト！") else t("Bạn thắng!", "You win!", "你赢了！", "あなたの勝ち！")
            result == -1 -> if (mated) t("Máy thắng – bạn bị chiếu hết", "Computer wins – you're checkmated", "电脑赢了——你被将死", "コンピュータの勝ち – チェックメイトされました") else t("Máy thắng", "Computer wins", "电脑赢了", "コンピュータの勝ち")
            else -> when (game.drawReason()) {
                com.huanhuynh.covua.engine.DrawReason.STALEMATE -> t("Hoà – hết nước đi (Vua không bị chiếu)", "Draw – stalemate", "和棋——逼和", "引き分け – ステイルメイト")
                com.huanhuynh.covua.engine.DrawReason.REPETITION -> t("Hoà – lặp lại thế cờ 3 lần", "Draw – threefold repetition", "和棋——三次重复局面", "引き分け – 同一局面3回")
                com.huanhuynh.covua.engine.DrawReason.FIFTY_MOVES -> t("Hoà – 50 nước không ăn quân, không đi Tốt", "Draw – fifty-move rule", "和棋——五十回合规则", "引き分け – 50手ルール")
                com.huanhuynh.covua.engine.DrawReason.MATERIAL -> t("Hoà – không đủ quân để chiếu hết", "Draw – insufficient material", "和棋——子力不足", "引き分け – メイトに必要な駒が足りません")
                else -> t("Hoà", "Draw", "和棋", "引き分け")
            }
        }
        _state.update { withBoard(it).copy(outcome = outcome, outcomeText = text, aiThinking = false, selected = -1, targets = emptySet(), danger = emptyMap()) }
        app.sounds.play(when (result) { 1 -> com.huanhuynh.covua.Sfx.WIN; -1 -> com.huanhuynh.covua.Sfx.LOSE; else -> com.huanhuynh.covua.Sfx.DRAW })
        val acc = accuracy()
        val level = _state.value.level
        val reason = when {
            resigned -> EndReason.RESIGN
            agreed -> EndReason.AGREED
            mated -> EndReason.MATE
            game.pos.repetitionCount() >= 2 -> EndReason.REPETITION
            game.pos.halfmove >= 100 -> EndReason.NO_CAPTURE
            game.pos.insufficientMaterial() -> EndReason.MATERIAL
            outcome == Outcome.DRAW -> EndReason.OTHER
            else -> EndReason.OTHER
        }
        val moves = game.moves.toList()
        val candidates = reviews.map { PlayerMoveEval(it.ply, it.fenBefore, it.review.move, it.review.loss, it.okMoveCount, it.review.tags) } +
            retried.values.filter { r -> reviews.none { it.fenBefore == r.fenBefore && it.review.move == r.move } }
        scope.launch { afterGame(result, reason, acc, moves, candidates) }
        val owesAd = result == -1 && _state.value.playOnly
        if (owesAd) app.interstitial.preload() // tải sẵn để hiện ngay khi bấm "Ván mới"
        scope.launch {
            val before = settings.current()
            // Thua ở chế độ "Chỉ đánh cờ" → lần bấm "Ván mới" kế tiếp hiện 1 quảng cáo
            if (owesAd) settings.setLossAdOwed(true)
            settings.recordGame(result, acc, level.id)
            val note = when {
                result != 1 -> if (result == 0 && level.id >= 6) t("Cầm hoà máy cấp ${level.id} – ${level.title} là rất giỏi rồi!", "Drawing level ${level.id} – ${level.title} is a great result!", "和${level.id}级电脑（${level.title}）已经很厉害了！", "レベル${level.id}（${level.title}）と引き分けるのは素晴らしい結果です！") else null
                level.id.toString() !in before.wonLevels && level.id >= 2 -> t("Lần đầu bạn thắng máy cấp ${level.id} – ${level.title}!", "Your first win against level ${level.id} – ${level.title}!", "第一次战胜${level.id}级电脑（${level.title}）！", "レベル${level.id}（${level.title}）に初勝利！")
                before.winStreak + 1 in setOf(3, 5, 10, 20) -> t("Chuỗi ${before.winStreak + 1} ván thắng liên tiếp!", "${before.winStreak + 1} wins in a row!", "${before.winStreak + 1}连胜！", "${before.winStreak + 1}連勝！")
                acc != null && acc >= 85 -> t("Ván thắng chính xác tới $acc%!", "A win with $acc% accuracy!", "准确率高达$acc%的胜局！", "正確さ${acc}%の勝利！")
                level.id >= 5 -> t("Thắng máy cấp ${level.id} – ${level.title} không hề dễ!", "Beating level ${level.id} – ${level.title} is no easy feat!", "战胜${level.id}级电脑（${level.title}）可不容易！", "レベル${level.id}（${level.title}）に勝つのは簡単ではありません！")
                else -> null
            }
            _state.update { it.copy(shareNote = note) }
        }
        lastResult = result
    }

    var lastResult: Int = 0
        private set

    /** Lưu ván vào Room rồi chạy các việc sau ván: Sổ tay, Lịch tập, gợi ý cấp, danh hiệu. */
    private suspend fun afterGame(result: Int, reason: String, acc: Int?, moves: List<Int>, candidates: List<PlayerMoveEval>) {
        val st = _state.value
        gameIdJob?.join()
        val id = gameId
        app.events.log(Ev.GAME_END, mapOf("level" to st.level.id.toString(), "handicap" to st.handicap.id, "mode" to st.mode,
            "result" to result.toString(), "reason" to reason, "moves" to ((moves.size + 1) / 2).toString(), "accuracy" to (acc ?: -1).toString()))
        val finished = if (id != 0L) runCatching { archive.finish(id, moves, result, reason, acc, undoCount, hintCount) }.getOrNull() else null
        if (finished != null) app.reviews.processFinishedGame(finished, candidates)
        runCatching { app.activity.recordGame(moves.size) }.getOrNull()?.let { m -> _state.update { it.copy(streakMilestone = m) } }
        // Danh hiệu (chỉ lên)
        if (app.flag(RcKeys.TITLES_ENABLED)) runCatching { app.player.updateTitle(settings.current().wonLevels) }.getOrNull()?.let { t ->
            app.events.log(Ev.TITLE_UP, mapOf("title" to t))
            _state.update { it.copy(titleUp = t) }
        }
        // Gợi ý cấp (không tự đổi – người chơi bấm mới áp dụng)
        if (finished != null && app.v11(RcKeys.DDA_ENABLED) && st.mode != GameMode.FROM_POSITION) runCatching {
            val recent = app.archive.recentFinished(8).map {
                FinishedGame(it.id, it.result ?: 0, it.level, Handicap.of(it.handicap), it.mode == GameMode.FROM_POSITION, it.legacy,
                    earlyResign = it.endReason == EndReason.RESIGN && it.plies < 20)
            }
            val ps = app.player.get()
            val total = app.db.games().finishedCount()
            val since = ps.ddaDeclinedUntilGame?.let { total - it }
            val sug = LevelAdvisor(app.rcInt(RcKeys.DDA_LOSS_STREAK), app.rcInt(RcKeys.DDA_WIN_STREAK), app.rcInt(RcKeys.DDA_COOLDOWN_GAMES))
                .advise(recent, since)
            if (sug != null && ps.ddaLastPromptGameId != id) {
                app.player.update { it.copy(ddaLastPromptGameId = id) }
                app.events.log(Ev.DDA_PROMPT, mapOf("type" to sug.type.name, "level" to st.level.id.toString(), "handicap" to st.handicap.id))
                _state.update { it.copy(suggestion = sug) }
            }
        }
    }

    fun accuracy(): Int? = if (reviews.isEmpty()) null else reviews.map { it.review.accuracy }.average().toInt()

    fun gradeCounts(): Map<Grade, Int> = reviews.groupingBy { it.review.grade }.eachCount()

    private fun persist() {
        val st = _state.value
        if (st.outcome != Outcome.NONE || game.moves.isEmpty()) return
        val ser = game.serialize()
        val acc = accuracy() ?: -1
        val moves = game.moves.toList()
        scope.launch {
            settings.saveGame(ser, st.level.id, st.humanSide, acc)
            gameIdJob?.join()
            if (gameId != 0L) runCatching { archive.saveProgress(gameId, moves, accuracy(), undoCount, hintCount) }
        }
    }

    /** Lời thầy cho nước thứ [ply] (nếu là nước của người chơi). */
    fun reviewAt(ply: Int): MoveReview? = reviews.firstOrNull { it.ply == ply }?.review

    @Suppress("unused")
    private fun codeOf(m: Int) = Notation.of(game.pos, m)
}
