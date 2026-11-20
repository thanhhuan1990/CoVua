package com.huanhuynh.covua.ui.game

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.coach.Grade
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.ui.CardBox
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.SectionTitle
import com.huanhuynh.covua.ui.Tag
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.board.BoardView
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import com.huanhuynh.xpromo.XPromo
import com.huanhuynh.xpromo.XPromoCard

@Composable
fun AnalysisScreen(container: AppContainer, settings: AppSettings, onClose: () -> Unit, onReplay: (Int) -> Unit, onNewGame: () -> Unit) {
    val ctrl = container.game
    val st = ctrl.state.value
    val acc = ctrl.accuracy()
    val counts = ctrl.gradeCounts()
    val curve = remember { ctrl.evalCurve.toList() }
    val moments = remember {
        ctrl.reviews.filter { it.review.grade == Grade.BLUNDER || it.review.grade == Grade.MISTAKE || it.review.grade == Grade.BRILLIANT && it.review.title.contains("!") }
            .sortedByDescending { if (it.review.grade.isBad) it.review.loss else 50 }.take(5).sortedBy { it.ply }
    }

    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Phân tích ván", "Game analysis", "棋局分析", "対局の分析") + (container.game.state.value.gameLabel.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                when (ctrl.lastResult) { 1 -> "🏆 "; -1 -> ""; else -> "🤝 " } + st.outcomeText,
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 4.dp),
            )
            Text(t("${st.codes.size} nửa nước · Cấp ${st.level.id} – ${st.level.title}", "${st.codes.size} plies · Level ${st.level.id} – ${st.level.title}", "${st.codes.size}步 · ${st.level.id}级 – ${st.level.title}", "${st.codes.size}手 · レベル${st.level.id} – ${st.level.title}"), fontSize = 12.5.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            CardBox {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("Độ chính xác", "Accuracy", "准确率", "正確さ"), fontSize = 12.sp, color = Muted)
                    Text(if (acc != null) "$acc%" else "—", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Good)
                    if (curve.size >= 2) {
                        EvalChart(curve, Modifier.fillMaxWidth().height(90.dp).padding(top = 6.dp))
                        Text(t("Lợi thế theo từng lượt (trên: Trắng hơn, dưới: Đen hơn)", "Advantage by turn (up: White better, down: Black better)", "每回合优势（上：白优，下：黑优）", "手番ごとの優勢（上：白有利、下：黒有利）"), fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatBox("${(counts[Grade.BRILLIANT] ?: 0) + (counts[Grade.BOOK] ?: 0)}", t("Hay / đúng bài", "Brilliant / book", "妙着/定式", "好手 / 定跡"), Good, Modifier.weight(1f))
                StatBox("${counts[Grade.GOOD] ?: 0}", t("Tốt", "Good", "好棋", "良い手"), Color(0xFF558B2F), Modifier.weight(1f))
                StatBox("${counts[Grade.INACCURACY] ?: 0}", t("Chưa chính xác", "Inaccuracy", "欠准确", "緩手"), gradeColor(Grade.INACCURACY), Modifier.weight(1f))
                StatBox("${(counts[Grade.MISTAKE] ?: 0) + (counts[Grade.BLUNDER] ?: 0)}", t("Sai lầm", "Mistake", "失误", "悪手"), gradeColor(Grade.BLUNDER), Modifier.weight(1f))
            }
            // Quảng cáo native giữa nội dung tĩnh (sau phần thống kê), cách xa các nút bấm ở cuối màn.
            if (!settings.adsRemoved) com.huanhuynh.covua.ads.NativeAdCard(container.adsManager, Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (moments.isNotEmpty()) {
                SectionTitle(t("Khoảnh khắc quan trọng", "Key moments", "关键时刻", "重要な場面"))
                CardBox {
                    moments.forEachIndexed { i, m ->
                        Column(Modifier.fillMaxWidth().clickable { onReplay(m.ply + 1) }.padding(vertical = 11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Tag(m.review.grade.label, gradeColor(m.review.grade))
                                Text(t("  Nước ${m.ply / 2 + 1}", "  Move ${m.ply / 2 + 1}", "  第${m.ply / 2 + 1}步", "  ${m.ply / 2 + 1}手目"), fontSize = 12.sp, color = Muted)
                            }
                            Text(m.review.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
                            Text(m.review.body, fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp, maxLines = 3)
                        }
                        if (i < moments.lastIndex) HorizontalDivider(color = Line)
                    }
                }
            } else if (ctrl.reviews.isNotEmpty()) {
                Text(t("Ván này bạn không mắc sai lầm lớn nào. Rất tốt!", "No big mistakes this game. Well done!", "这盘没有大的失误，很好！", "この対局では大きなミスはありませんでした。お見事！"), fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 14.dp))
            }
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(t("Xem lại ván", "Replay game", "复盘", "対局を再生"), primary = false, modifier = Modifier.weight(1f)) { onReplay(0) }
                if (ctrl.lastResult == 1 || st.shareNote != null) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    PillButton(t("📤 Khoe", "📤 Share", "📤 分享", "📤 シェア"), primary = false, modifier = Modifier.weight(1f)) {
                        com.huanhuynh.covua.share.Sharer.share(ctx, gameShareContent(ctrl, st, settings.flipWhenBlack && st.humanSide < 0), settings.pieceStyle)
                    }
                }
                PillButton(t("Ván mới", "New game", "新棋局", "新しい対局"), modifier = Modifier.weight(1f), onClick = onNewGame)
            }
            if (!settings.adsRemoved) XPromo.get()?.let { XPromoCard(it, placement = "analysis", modifier = Modifier.padding(bottom = 16.dp)) }
        }
        // Không đặt banner ở đáy màn phân tích: ngay trên là các nút Xem lại / Khoe / Ván mới (dễ bấm nhầm).
    }
}

@Composable
private fun StatBox(n: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(n, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.5.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

@Composable
fun EvalChart(values: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        val mid = size.height / 2
        drawRect(Color(0xFFFBE9E7), Offset.Zero, Size(size.width, mid))
        drawRect(Color(0xFFECEFF1), Offset(0f, mid), Size(size.width, mid))
        val n = values.size
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = if (n == 1) 0f else i * size.width / (n - 1)
            val y = mid - (v.coerceIn(-1000, 1000) / 1000f) * mid
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Red, style = Stroke(width = 5f))
    }
}

/** Xem lại ván từng nước, kèm lời thầy cho nước của người chơi. */
@Composable
fun ReplayScreen(container: AppContainer, settings: AppSettings, startPly: Int, onBack: () -> Unit, onPlayFrom: ((fen: String, humanSide: Int) -> Unit)? = null) {
    val ctrl = container.game
    val game = ctrl.currentGame()
    val total = game.moves.size
    var ply by remember { mutableIntStateOf(startPly.coerceIn(0, total)) }
    val pos = remember(ply) { game.positionAt(ply) }
    val review = if (ply > 0) ctrl.reviewAt(ply - 1) else null
    val human = ctrl.state.value.humanSide
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Xem lại ván", "Replay game", "复盘", "対局を再生") + (container.game.state.value.gameLabel.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), onBack)
        Column(Modifier.padding(horizontal = 8.dp)) {
            BoardView(
                board = pos.board, flipped = settings.flipWhenBlack && human < 0, style = settings.pieceStyle,
                lastMove = if (ply > 0) game.moves[ply - 1] else 0, version = ply,
            )
        }
        Text(
            if (ply == 0) t("Thế đầu", "Start position", "初始局面", "初期局面") else t("Nước", "Move", "第", "手") + " ${(ply - 1) / 2 + 1}${if ((ply - 1) % 2 == 0) "" else "…"} ${game.shown(ply - 1)}",
            fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Slider(
            value = ply.toFloat(), onValueChange = { ply = it.toInt() }, valueRange = 0f..total.toFloat().coerceAtLeast(1f),
            steps = (total - 1).coerceAtLeast(0), modifier = Modifier.padding(horizontal = 16.dp),
            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(t("◀ Lùi", "◀ Back", "◀ 后退", "◀ 戻る"), primary = false, modifier = Modifier.weight(1f), enabled = ply > 0) { ply-- }
            PillButton(t("Tiến ▶", "Forward ▶", "前进 ▶", "進む ▶"), modifier = Modifier.weight(1f), enabled = ply < total) { ply++ }
        }
        // Chơi tiếp từ thế này (ván FROM_POSITION: không tính gợi ý cấp, vẫn ghi Sổ tay)
        if (onPlayFrom != null && container.flag(com.huanhuynh.covua.data.RcKeys.PLAY_FROM_POSITION_ENABLED) && pos.legalMoves().isNotEmpty()) {
            PillButton(t("♟ Chơi tiếp với máy từ thế này", "♟ Play on from here vs computer", "♟ 从这里继续和电脑下", "♟ この局面からコンピュータと続ける"), primary = false, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                onPlayFrom(pos.toFen(), human)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (review != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(12.dp)) {
                    Tag(review.grade.label, gradeColor(review.grade))
                    Text(review.title, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 3.dp))
                    Text(review.body, fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    review.details.forEach { Text("• $it", fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
                    if (review.betterDetails.isNotEmpty()) {
                        Text(if (review.grade.isBad) t("Nước tốt hơn", "Better move", "更好的着法", "より良い手") else t("Còn nước tốt hơn một chút", "There's a slightly better move", "还有稍好的着法", "少し良い手があります"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Good, modifier = Modifier.padding(top = 8.dp))
                        review.betterDetails.forEach { Text("• $it", fontSize = 13.sp, color = Color(0xFF3E3631), lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp)) }
                    }
                }
            } else if (ply > 0) {
                Text(t("Nước của máy.", "Computer's move.", "电脑的着法。", "コンピュータの手。"), fontSize = 13.sp, color = Muted)
            }
        }
    }
}
