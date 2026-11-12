package com.huanhuynh.covua.ui.game

import com.huanhuynh.covua.i18n.t

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.SoundEffectConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.coach.Grade
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.share.ShareContent
import com.huanhuynh.covua.share.Sharer
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.engine.Outcome
import com.huanhuynh.covua.training.LevelSuggestion
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.Tag
import com.huanhuynh.covua.ui.TeacherAvatar
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.board.BoardView
import com.huanhuynh.covua.ui.board.drawPiece
import com.huanhuynh.covua.ui.board.PieceBlack
import com.huanhuynh.covua.ui.board.PieceWhite
import com.huanhuynh.covua.ui.theme.Bad
import com.huanhuynh.covua.ui.theme.Best
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import com.huanhuynh.covua.ui.theme.Warn
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun gradeColor(g: Grade): Color = when (g) {
    Grade.BRILLIANT -> Good
    Grade.BOOK -> Best
    Grade.GOOD -> Color(0xFF558B2F)
    Grade.INACCURACY -> Warn
    Grade.MISTAKE -> Color(0xFFE65100)
    Grade.BLUNDER -> Bad
}

@Composable
fun GameScreen(
    container: AppContainer,
    settings: AppSettings,
    onBack: () -> Unit,
    onAnalysis: () -> Unit,
    onNewGame: () -> Unit,
    onOpenReview: () -> Unit = {},
    onStartWith: (LevelSuggestion.Option) -> Unit = {},
) {
    val ctrl = container.game
    val st by ctrl.state.collectAsStateWithLifecycle()
    val cardsNote by container.reviews.lastGameCards.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    val flipped = settings.flipWhenBlack && st.humanSide < 0

    LaunchedEffect(settings.betterDialog) { ctrl.betterDialog = settings.betterDialog }

    // ---- Quảng cáo có thưởng ở gợi ý / đi lại: cứ N lần miễn phí thì mời xem 1 quảng cáo (người chơi tự chọn) ----
    var adGate by remember { mutableStateOf<AdGate?>(null) }
    fun canGate(freeKey: String, used: Int): Boolean {
        if (settings.adsRemoved) return false
        val free = FirebaseHelper.getLong(context, freeKey).toInt()
        if (free <= 0 || used < free) return false
        if (!container.rewarded.isReady) { container.rewarded.preload(); return false } // chưa có quảng cáo: không bắt chờ
        return true
    }
    fun gatedHint() {
        if (ctrl.isNewHintRequest() && canGate(RcKeys.HINT_FREE_BETWEEN_ADS, ctrl.hintsSinceAd)) {
            adGate = AdGate.Hint(FirebaseHelper.getLong(context, RcKeys.HINT_FREE_BETWEEN_ADS).toInt())
        } else ctrl.requestHint()
    }
    fun gatedRetry(playBetter: Boolean) {
        if (canGate(RcKeys.RETRY_FREE_BETWEEN_ADS, ctrl.retriesSinceAd)) {
            adGate = AdGate.Retry(FirebaseHelper.getLong(context, RcKeys.RETRY_FREE_BETWEEN_ADS).toInt(), playBetter)
        } else ctrl.retryFromPrompt(playBetter)
    }
    adGate?.let { gate ->
        AlertDialog(
            onDismissRequest = { adGate = null },
            title = { Text(if (gate is AdGate.Hint) t("Mở gợi ý bằng quảng cáo ngắn", "Unlock a hint with a short ad", "看短广告解锁提示", "短い広告でヒントを開く") else t("Đi lại bằng quảng cáo ngắn", "Take back with a short ad", "看短广告悔棋", "短い広告で待った")) },
            text = {
                Text(
                    run {
                        val n = gate.free
                        val what = if (gate is AdGate.Hint) t("nhận gợi ý này", "get this hint", "获得这个提示", "このヒントを見る") else t("đi lại nước vừa rồi", "take back your last move", "悔掉刚才那步", "直前の手を待ったする")
                        t("Bạn đã dùng $n lần miễn phí. Xem một quảng cáo ngắn để $what, sau đó lại có $n lần miễn phí.",
                            "You've used your $n free ones. Watch a short ad to $what, then you get $n more free.",
                            "你已用完${n}次免费机会。看一段短广告即可${what}，之后又有${n}次免费机会。", "無料分の${n}回を使い切りました。短い広告を見ると${what}ことができ、その後また${n}回無料になります。")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    adGate = null
                    val a = context.findActivity() ?: return@TextButton
                    val shown = container.rewarded.show(a, onReward = {
                        when (gate) {
                            is AdGate.Hint -> { ctrl.resetHintAdCounter(); ctrl.requestHint() }
                            is AdGate.Retry -> { ctrl.resetRetryAdCounter(); ctrl.retryFromPrompt(gate.playBetter) }
                        }
                        FirebaseHelper.logEvent(context, "rewarded_" + if (gate is AdGate.Hint) "hint" else "retry")
                    })
                    if (!shown) when (gate) { // quảng cáo vừa hết hạn: cho dùng luôn
                        is AdGate.Hint -> ctrl.requestHint()
                        is AdGate.Retry -> ctrl.retryFromPrompt(gate.playBetter)
                    }
                }) { Text(t("Xem quảng cáo", "Watch ad", "看广告", "広告を見る"), color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { adGate = null }) { Text(t("Không, cảm ơn", "No, thanks", "不用了，谢谢", "いいえ")) } },
        )
    }
    st.prompt?.let { pr ->
        BetterMoveDialog(
            prompt = pr, style = settings.pieceStyle, flipped = flipped,
            onPlayBetter = { gatedRetry(true) },
            onRetry = { gatedRetry(false) },
            onKeep = { ctrl.dismissPrompt() },
        )
    }

    if (st.promotion != 0) {
        PromotionDialog(white = st.humanSide > 0, style = settings.pieceStyle, onPick = { ctrl.choosePromotion(it) }, onDismiss = { ctrl.cancelPromotion() })
    }

    LaunchedEffect(st.version) {
        if (st.version > 1) haptic.performHapticFeedback(if (st.checkSquare >= 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
    }
    // Đọc lời thầy (có "Chiếu!" trước nếu nước vừa rồi là nước chiếu)
    var announcedCheck by remember { mutableStateOf(0) }
    LaunchedEffect(st.card, st.version, settings.tts) {
        if (!settings.tts) return@LaunchedEffect
        val check = st.checkSquare >= 0 && st.outcome == Outcome.NONE && announcedCheck != st.version
        if (check) announcedCheck = st.version
        val prefix = if (check) t("Chiếu! ", "Check! ", "将军！", "チェック！") else ""
        val text = when (val c = st.card) {
            is CoachCard.Review -> c.review.title.substringAfter("– ") + ". " + c.review.body
            is CoachCard.Danger -> t("Cẩn thận. ", "Careful. ", "小心。", "注意。") + c.warning.text
            is CoachCard.HintCard -> if (c.revealed) c.hint.body else c.hint.pieceText
            else -> null
        }
        if (text != null) container.tts.speak(prefix + text) else if (check) container.tts.speak(t("Chiếu!", "Check!", "将军！", "チェック！"))
    }

    fun rewardedThen(onReward: () -> Unit) {
        val a = context.findActivity() ?: return
        if (!container.rewarded.show(a, onReward = onReward)) {
            Toast.makeText(context, t("Quảng cáo đang tải, thử lại sau ít giây", "The ad is loading, try again in a few seconds", "广告加载中，请稍后再试", "広告を読み込み中です。数秒後にもう一度お試しください"), Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Cấp ${st.level.id} · ${st.level.title}", "Level ${st.level.id} · ${st.level.title}", "${st.level.id}级 · ${st.level.title}", "レベル${st.level.id} · ${st.level.title}") + (st.gameLabel.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), onBack) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, t("Thêm", "More", "更多", "その他"), tint = Ink) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t("Ván mới", "New game", "新棋局", "新しい対局")) }, onClick = { menu = false; onNewGame() })
                if (st.outcome == Outcome.NONE && container.flag(RcKeys.DRAW_OFFER_ENABLED)) DropdownMenuItem(
                    text = { Text(t("Xin hoà", "Offer draw", "提和", "引き分けを提案")) }, enabled = ctrl.canOfferDraw(),
                    onClick = { menu = false; ctrl.offerDraw() },
                )
                if (st.outcome == Outcome.NONE) DropdownMenuItem(text = { Text(t("Xin thua", "Resign", "认输", "投了")) }, onClick = { menu = false; confirmResign = true })
                if (st.outcome != Outcome.NONE) DropdownMenuItem(text = { Text(t("Phân tích ván", "Game analysis", "棋局分析", "対局の分析")) }, onClick = { menu = false; onAnalysis() })
            }
        }
        PlayerRow(name = t("Máy · ${st.level.title}", "Computer · ${st.level.title}", "电脑 · ${st.level.title}", "コンピュータ · ${st.level.title}"), sub = if (st.aiThinking) t("đang nghĩ…", "thinking…", "思考中…", "考え中…") else sideName(-st.humanSide), side = -st.humanSide, active = st.aiThinking)
        Box(Modifier.padding(horizontal = 8.dp)) {
            BoardView(
                board = st.board,
                flipped = flipped,
                style = settings.pieceStyle,
                selected = st.selected,
                targets = st.targets,
                danger = st.danger.keys,
                lastMove = st.lastMove,
                checkSquare = st.checkSquare,
                arrows = st.arrows,
                version = st.version,
                showTargets = settings.showTargets || st.danger.isNotEmpty(),
                onTap = { ctrl.onTap(it) },
            )
        }
        PlayerRow(name = t("Bạn", "You", "你", "あなた"), sub = sideName(st.humanSide), side = st.humanSide, active = st.humanToMove)
        MovesStrip(st.codes)
        Spacer(Modifier.weight(1f))

        // Thẻ thầy
        Column(
            Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                .border(1.dp, Line, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(min = 120.dp),
        ) {
            if (st.outcome != Outcome.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeacherAvatar()
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Tag(t("Kết thúc", "Game over", "对局结束", "終局"), Red)
                        Text(st.outcomeText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                ctrl.accuracy()?.let { Text(t("Độ chính xác của bạn: $it%", "Your accuracy: $it%", "你的准确率：$it%", "あなたの正確さ：${it}%"), fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
                // Sổ tay: "Thầy đã ghi N thế…"
                cardsNote?.second?.takeIf { it > 0 }?.let { n ->
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3EEE6))
                            .clickable(onClick = onOpenReview).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📒", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Thầy đã ghi $n thế vào Sổ tay – mai mình ôn nhé.", "Your coach saved $n positions to your notebook – let's review them tomorrow.", "老师把${n}个局面记进了错题本——明天一起复习吧。", "先生が${n}局面をミスノートに記録しました – 明日一緒に復習しましょう。"), fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                        Text(t("Xem sổ", "Open notebook", "查看错题本", "ミスノートを開く"), color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                st.titleUp?.let { tt ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("🎖 Thầy chúc mừng – danh hiệu mới: $tt", "🎖 Congratulations – new title: $tt", "🎖 恭喜——新称号：$tt", "🎖 おめでとう – 新しい称号：${tt}") + " (${com.huanhuynh.covua.training.Titles.SUBTITLE})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        Text(t("Khoe", "Share", "分享", "シェア"), color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable {
                            Sharer.shareText(context, t("Mình vừa lên danh hiệu \"$tt\" trong Cờ Vua Có Thầy (thắng máy cấp ${st.level.id}). Thử sức không?", "I just earned the title \"$tt\" in Chess Coach (beat level ${st.level.id}). Up for it?", "我在「国际象棋老师」获得了新称号「${tt}」（战胜${st.level.id}级电脑）。来试试吗？", "「チェス先生」で新しい称号「${tt}」を獲得しました（レベル${st.level.id}に勝利）。挑戦してみる？"), "title")
                            container.events.log(com.huanhuynh.covua.domain.Ev.SHARE, mapOf("moment" to "title"))
                        }.padding(6.dp))
                    }
                }
                st.streakMilestone?.takeIf { !settings.hideStreak }?.let { d ->
                    Text(t("📅 Lịch tập: $d ngày liền!", "📅 Practice streak: $d days!", "📅 连续练习${d}天！", "📅 連続練習：${d}日！"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 6.dp))
                }
                // Thầy gợi ý cấp – người chơi tự chọn, không tự đổi
                st.suggestion?.let { sug ->
                    Column(
                        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF2FB))
                            .border(1.dp, Best.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(10.dp),
                    ) {
                        Text(sug.text, fontSize = 13.sp, color = Ink, lineHeight = 18.sp)
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sug.options.forEach { o -> PillButton(o.label) { ctrl.ddaChoice(o); onStartWith(o) } }
                            PillButton(t("Giữ nguyên", "Keep as is", "保持不变", "このまま"), primary = false) { ctrl.ddaChoice(null) }
                        }
                    }
                }
                st.shareNote?.let { note ->
                    Row(
                        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4E0))
                            .border(1.dp, Color(0xFFF2D9A8), RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🏆", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(note, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Ink)
                            Text(t("Khoe với bạn bè – ảnh bàn cờ kèm link tải app", "Show your friends – a board image with a download link", "分享给好友——棋盘图片附下载链接", "友達に自慢 – ダウンロードリンク付きの盤面画像"), fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(t("Xem phân tích ván", "See game analysis", "查看棋局分析", "対局の分析を見る"), primary = st.suggestion == null, onClick = onAnalysis)
                    if (ctrl.lastResult == 1 || st.shareNote != null) {
                        PillButton(t("📤 Khoe", "📤 Share", "📤 分享", "📤 シェア"), primary = st.shareNote != null) {
                            Sharer.share(context, gameShareContent(ctrl, st, flipped), settings.pieceStyle)
                        }
                    }
                    PillButton(t("Ván mới", "New game", "新棋局", "新しい対局"), primary = false, onClick = onNewGame)
                }
            } else {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    AnimatedContent(st.card, label = "coach") { card -> CoachCardContent(card, settings.coachFull) }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (val c = st.card) {
                        is CoachCard.Danger -> {
                            PillButton(t("Chọn nước khác", "Choose another move", "换一步", "別の手を選ぶ"), onClick = { ctrl.cancelDanger() })
                            PillButton(t("Vẫn đi", "Play anyway", "仍然走", "それでも指す"), primary = false, onClick = { ctrl.confirmDanger(c.move) })
                        }
                        is CoachCard.HintCard -> {
                            if (!c.revealed) PillButton(t("Xem nước đi", "Show the move", "显示着法", "手を表示"), onClick = { gatedHint() })
                            else PillButton(t("Đi theo gợi ý", "Play the hint", "按提示走", "ヒントの手を指す"), onClick = { ctrl.playHint() })
                            PillButton(t("Tự đi", "I'll do it", "我自己走", "自分で指す"), primary = false, onClick = { ctrl.cancelDanger() })
                        }
                        else -> {
                            val rv = (c as? CoachCard.Review)?.review
                            val bad = rv?.grade?.isBad == true
                            if (rv != null && rv.better != 0 && rv.betterDetails.isNotEmpty()) {
                                PillButton(t("Nước tốt hơn", "Better move", "更好的着法", "より良い手"), primary = bad, onClick = { ctrl.reopenPrompt() })
                            }
                            if (st.undoOn) {
                                val label = if (st.undoUnlimited || st.undosLeft > 0) t("↶ Đi lại", "↶ Take back", "↶ 悔棋", "↶ 待った") else t("↶ Đi lại (xem QC)", "↶ Take back (ad)", "↶ 悔棋（看广告）", "↶ 待った（広告）")
                                PillButton(label, primary = bad, enabled = ctrl.canUndo() || (!st.undoUnlimited && st.undosLeft <= 0 && st.codes.isNotEmpty())) {
                                    if (!st.undoUnlimited && st.undosLeft <= 0) rewardedThen { ctrl.addUndos(3); ctrl.undo() } else ctrl.undo()
                                }
                            }
                            if (st.hintsOn) {
                                PillButton(t("💡 Gợi ý", "💡 Hint", "💡 提示", "💡 ヒント"), primary = false, enabled = st.humanToMove) { gatedHint() }
                            }
                        }
                    }
                }
            }
        }
    }

    st.drawNote?.let { note ->
        AlertDialog(
            onDismissRequest = { ctrl.clearDrawNote() },
            title = { Text(t("Máy chưa nhận hoà", "Draw declined", "电脑不同意和棋", "引き分けは断られました")) },
            text = { Text(note) },
            confirmButton = { TextButton(onClick = { ctrl.clearDrawNote() }) { Text(t("Đánh tiếp", "Play on", "继续下", "続ける")) } },
        )
    }
    // Hỏi đánh giá sau ván thắng đáng nhớ (không sau quảng cáo; Google tự quyết hiện hay không)
    LaunchedEffect(st.shareNote, st.outcome) {
        if (st.outcome != Outcome.NONE && ctrl.lastResult == 1 && st.shareNote != null) {
            context.findActivity()?.let { com.huanhuynh.covua.feature.iar.InAppReview.maybeRequest(it, container) }
        }
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(t("Xin thua ván này?", "Resign this game?", "认输这盘？", "この対局を投了しますか？")) },
            text = { Text(t("Ván sẽ kết thúc và được tính là thua. Bạn vẫn xem được phân tích ván.", "The game ends as a loss. You can still see the analysis.", "本局将判负，你仍可查看棋局分析。", "負けとして終局します。分析は引き続き見られます。")) },
            confirmButton = { TextButton(onClick = { confirmResign = false; ctrl.resign() }) { Text(t("Xin thua", "Resign", "认输", "投了"), color = Red) } },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(t("Chơi tiếp", "Keep playing", "继续下", "続ける")) } },
        )
    }
    LaunchedEffect(Unit) { scope.launch { container.rewarded.preload() } }
}

/** Ảnh khoe kết quả ván (thế cờ cuối). */
fun gameShareContent(ctrl: GameController, st: GameUiState, flipped: Boolean): ShareContent {
    val acc = ctrl.accuracy()
    val n = (st.codes.size + 1) / 2
    val win = ctrl.lastResult == 1
    val title = if (win) t("Mình vừa thắng máy cấp ${st.level.id} – ${st.level.title}!", "I just beat level ${st.level.id} – ${st.level.title}!", "我刚战胜了${st.level.id}级电脑（${st.level.title}）！", "レベル${st.level.id}（${st.level.title}）に勝ちました！") else t("Cầm hoà máy cấp ${st.level.id} – ${st.level.title}!", "Drew with level ${st.level.id} – ${st.level.title}!", "和${st.level.id}级电脑（${st.level.title}）下成和棋！", "レベル${st.level.id}（${st.level.title}）と引き分け！")
    val sub = (if (st.outcomeText.contains(t("chiếu hết", "checkmate", "将死", "チェックメイト"))) t("Chiếu hết sau $n nước", "Checkmate after $n moves", "${n}步将死", "${n}手でチェックメイト") else t("Sau $n nước", "After $n moves", "${n}步", "${n}手後")) + (acc?.let { t(" · độ chính xác $it%", " · $it% accuracy", " · 准确率$it%", " · 正確さ${it}%") } ?: "")
    val text = (if (win) t("Mình vừa thắng máy cấp ${st.level.id} (${st.level.title})", "I just beat level ${st.level.id} (${st.level.title})", "我刚战胜了${st.level.id}级电脑（${st.level.title}）", "レベル${st.level.id}（${st.level.title}）に勝ちました") else t("Mình vừa cầm hoà máy cấp ${st.level.id} (${st.level.title})", "I just drew with level ${st.level.id} (${st.level.title})", "我刚和${st.level.id}级电脑（${st.level.title}）下和", "レベル${st.level.id}（${st.level.title}）と引き分けました")) +
        t(" trong Cờ Vua Có Thầy", " in Chess Coach", "（国际象棋老师）", "（チェス先生）") + (acc?.let { t(", độ chính xác $it%", ", $it% accuracy", "，准确率$it%", "、正確さ${it}%") } ?: "") + t(". Bạn thử xem có thắng được không?", ". Think you can win?", "。你也来试试能不能赢？", "。あなたも勝てるか試してみて！")
    return ShareContent(if (win) "win" else "draw", title, sub, text, st.board, st.lastMove, flipped)
}

private sealed interface AdGate {
    val free: Int
    data class Hint(override val free: Int) : AdGate
    data class Retry(override val free: Int, val playBetter: Boolean) : AdGate
}

private fun sideName(s: Int) = if (s > 0) t("Trắng", "White", "白方", "白") else t("Đen", "Black", "黑方", "黒")

@Composable
private fun PlayerRow(name: String, sub: String, side: Int, active: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (side > 0) PieceWhite else PieceBlack).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("♚", color = if (side > 0) PieceBlack else PieceWhite, fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (active) Red else Ink)
            Text(sub, fontSize = 11.5.sp, color = Muted)
        }
    }
}

@Composable
private fun MovesStrip(codes: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(codes.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (codes.isEmpty()) Text(t("Trắng đi trước", "White moves first", "白方先走", "白が先手"), fontSize = 12.5.sp, color = Muted)
        codes.forEachIndexed { i, c ->
            val label = if (i % 2 == 0) "${i / 2 + 1}. $c" else c
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (i == codes.lastIndex) Ink else Muted)
        }
    }
}

/** Chọn quân phong cấp: Hậu, Xe, Tượng, Mã. */
@Composable
fun PromotionDialog(white: Boolean, style: com.huanhuynh.covua.data.PieceStyle, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val types = listOf(
        com.huanhuynh.covua.engine.Position.QUEEN, com.huanhuynh.covua.engine.Position.ROOK,
        com.huanhuynh.covua.engine.Position.BISHOP, com.huanhuynh.covua.engine.Position.KNIGHT,
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
            Text(t("Phong cấp thành quân nào?", "Promote to…", "升变为…", "何に昇格しますか？"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { ty ->
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF0D9B5)).clickable { onPick(ty) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(52.dp)) {
                            BoardPieceIcon(if (white) ty else -ty, style)
                        }
                        Text(com.huanhuynh.covua.engine.Notation.pieceName(ty), fontSize = 11.sp, color = Ink)
                    }
                }
            }
        }
    }
}

/** Một quân vẽ riêng (dùng trong hộp thoại phong cấp). */
@Composable
fun BoardPieceIcon(p: Int, style: com.huanhuynh.covua.data.PieceStyle) {
    val glyph = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    val letter = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD } }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawPiece(androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2), size.minDimension, p, style, glyph, letter)
    }
}

/** Hộp thoại "Có nước tốt hơn": bàn cờ trước nước đi (đỏ = nước bạn đi, xanh = nước tốt hơn) + lời thầy + đi lại. */
@Composable
private fun BetterMoveDialog(
    prompt: BetterPrompt,
    style: com.huanhuynh.covua.data.PieceStyle,
    flipped: Boolean,
    onPlayBetter: () -> Unit,
    onRetry: () -> Unit,
    onKeep: () -> Unit,
) {
    val r = prompt.review
    androidx.compose.ui.window.Dialog(onDismissRequest = onKeep) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeacherAvatar()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Tag(r.grade.label, gradeColor(r.grade))
                    Text(if (r.grade.isBad) t("Có nước tốt hơn!", "There's a better move!", "有更好的着法！", "もっと良い手があります！") else t("Còn nước tốt hơn một chút", "There's a slightly better move", "还有稍好的着法", "少し良い手があります"), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth(0.82f).align(Alignment.CenterHorizontally)) {
                BoardView(
                    board = prompt.boardBefore, flipped = flipped, style = style,
                    arrows = listOf(
                        Arrow(com.huanhuynh.covua.engine.Moves.from(r.move), com.huanhuynh.covua.engine.Moves.to(r.move), ArrowKind.THREAT),
                        Arrow(com.huanhuynh.covua.engine.Moves.from(r.better), com.huanhuynh.covua.engine.Moves.to(r.better), ArrowKind.BETTER),
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(t("● Bạn đi: ${r.code}", "● You played: ${r.code}", "● 你走：${r.code}", "● あなたの手：${r.code}"), color = Bad, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(t("● Nên đi: ${r.betterCode}", "● Better: ${r.betterCode}", "● 应走：${r.betterCode}", "● 良い手：${r.betterCode}"), color = Good, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 10.dp))
            Text(r.body, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
            Column(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF5EE))
                    .border(1.dp, Good.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(10.dp),
            ) {
                Text(t("Nước tốt hơn: ${r.betterCode}", "Better move: ${r.betterCode}", "更好的着法：${r.betterCode}", "より良い手：${r.betterCode}"), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Good)
                r.betterDetails.forEach { Bullet(it) }
            }
            PillButton(t("↶ Đi lại theo nước ${r.betterCode}", "↶ Take back and play ${r.betterCode}", "↶ 悔棋改走${r.betterCode}", "↶ 待ったして${r.betterCode}に指す"), modifier = Modifier.fillMaxWidth().padding(top = 14.dp), onClick = onPlayBetter)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(t("Đi lại, tự tìm", "Take back, find it myself", "悔棋，自己找", "待ったして自分で探す"), primary = false, modifier = Modifier.weight(1f), onClick = onRetry)
                PillButton(t("Giữ nước của tôi", "Keep my move", "保留我的着法", "自分の手のまま"), primary = false, modifier = Modifier.weight(1f), onClick = onKeep)
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("•", fontSize = 13.sp, color = Muted, modifier = Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp)
    }
}

@Composable
private fun CoachCardContent(card: CoachCard, full: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        TeacherAvatar()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            when (card) {
                CoachCard.YourTurn -> {
                    Text(t("Đến lượt bạn", "Your turn", "轮到你了", "あなたの番"), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
                    Text(t("Chạm vào quân để xem ô đi được. Ô có dấu đỏ là chỗ đi vào sẽ mất quân.", "Tap a piece to see where it can go. Red-marked squares lose material.", "点棋子查看可走位置。红色标记的格子会丢子。", "駒をタップすると動ける場所が表示されます。赤い印のマスに行くと駒を取られます。"), fontSize = 13.sp, color = Muted, lineHeight = 18.sp)
                }
                CoachCard.Thinking -> {
                    Text(t("Thầy đang xem nước của bạn…", "Your coach is looking at your move…", "老师正在看你的着法…", "先生があなたの手を見ています…"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Muted)
                }
                is CoachCard.Review -> {
                    val r = card.review
                    Tag(r.grade.label, gradeColor(r.grade))
                    Text(r.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink, modifier = Modifier.padding(top = 3.dp))
                    if (full || r.grade.isBad) Text(r.body, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    if (full) r.details.forEach { Bullet(it) }
                    card.opponentNote?.let { Text(it, fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
                }
                is CoachCard.Danger -> {
                    Tag(t("Cảnh báo", "Warning", "警告", "警告"), Warn)
                    Text(t("Cẩn thận!", "Careful!", "小心！", "注意！"), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink, modifier = Modifier.padding(top = 3.dp))
                    Text(card.warning.text, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(t("Chạm lại ô đó (hoặc bấm \"Vẫn đi\") nếu bạn vẫn muốn đi.", "Tap that square again (or \"Play anyway\") if you still want to go there.", "如果仍想走，请再点一次（或点\"仍然走\"）。", "それでも指したい場合は、もう一度そのマスをタップ（または「それでも指す」）。"), fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                }
                is CoachCard.HintCard -> {
                    Tag(t("Gợi ý", "Hint", "提示", "ヒント"), Best)
                    if (!card.revealed) {
                        Text(card.hint.pieceText, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink, modifier = Modifier.padding(top = 3.dp))
                        Text(t("Quân trên ô tô xanh là quân nên đi. Tự tìm ô đích, hoặc bấm \"Xem nước đi\".", "The piece on the blue square is the one to move. Find the target square, or tap \"Show the move\".", "蓝色格子上的棋子是该走的。自己找目标格，或点\"显示着法\"。", "青いマスの駒が動かすべき駒です。行き先を自分で探すか、「手を表示」をタップ。"), fontSize = 13.sp, color = Muted, lineHeight = 18.sp)
                    } else {
                        Text(card.hint.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink, modifier = Modifier.padding(top = 3.dp))
                        Text(card.hint.body, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                is CoachCard.Info -> {
                    Text(card.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
                    Text(card.body, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}
