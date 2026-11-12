package com.huanhuynh.covua.ui.home

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.engine.Handicap
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.ui.CardBox
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.SectionTitle
import com.huanhuynh.covua.ui.Segmented
import com.huanhuynh.covua.ui.SwitchRow
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red

data class NewGameChoice(val level: Level, val side: Int, val explain: Boolean, val warn: Boolean, val hint: Boolean, val undo: Boolean, val handicap: Handicap = Handicap.NONE)

@Composable
fun NewGameScreen(settings: AppSettings, onBack: () -> Unit, handicapEnabled: Boolean = true, fromFen: String? = null, presetSide: Int? = null, onStart: (NewGameChoice) -> Unit) {
    var handicap by remember { mutableStateOf(Handicap.of(settings.handicap)) }
    var level by remember { mutableIntStateOf(settings.level) }
    var side by remember { mutableIntStateOf(presetSide ?: settings.sideChoice) }
    var explain by remember { mutableStateOf(settings.coachExplain) }
    var warn by remember { mutableStateOf(settings.coachWarn) }
    var hint by remember { mutableStateOf(settings.coachHint) }
    var undo by remember { mutableStateOf(settings.allowUndo) }
    val lv = Level.of(level)

    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(if (fromFen != null) t("Chơi tiếp từ thế này", "Play on from here", "从这里继续下", "ここから続きを指す") else t("Ván mới", "New game", "新棋局", "新しい対局"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (fromFen != null) {
                val pos = remember(fromFen) { com.huanhuynh.covua.engine.Position.fromFen(fromFen) }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally).padding(top = 6.dp)) {
                    com.huanhuynh.covua.ui.board.BoardView(board = pos.board, flipped = settings.flipWhenBlack && side < 0, style = settings.pieceStyle)
                }
                Text(if (pos.side > 0) t("Trắng đi trước. Thầy vẫn kèm từng nước; ván này không tính vào gợi ý cấp.", "White moves first. Your coach still comments on every move; this game doesn't affect level suggestions.", "白方先走。老师照常点评；本局不计入等级建议。", "白が先手です。先生はいつも通り一手ごとに解説します。この対局はレベル提案に含まれません。") else t("Đen đi trước. Thầy vẫn kèm từng nước; ván này không tính vào gợi ý cấp.", "Black moves first. Your coach still comments on every move; this game doesn't affect level suggestions.", "黑方先走。老师照常点评；本局不计入等级建议。", "黒が先手です。先生はいつも通り一手ごとに解説します。この対局はレベル提案に含まれません。"), fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            SectionTitle(t("Cấp độ máy", "Computer level", "电脑等级", "コンピュータのレベル"))
            for (rowStart in listOf(1, 5)) {
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in rowStart until rowStart + 4) {
                        val l = Level.of(i)
                        val on = i == level
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Red else Color.White)
                                .border(1.dp, if (on) Red else Line, RoundedCornerShape(14.dp)).clickable { level = i }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("$i", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Ink)
                            Text(l.title, fontSize = 10.5.sp, color = if (on) Color.White else Muted, maxLines = 1)
                        }
                    }
                }
            }
            Text(
                t("Cấp ${lv.id} – ${lv.title}: ${lv.blurb}", "Level ${lv.id} – ${lv.title}: ${lv.blurb}", "${lv.id}级 – ${lv.title}：${lv.blurb}", "レベル${lv.id} – ${lv.title}：${lv.blurb}"), fontSize = 12.5.sp, color = Muted,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(10.dp),
            )

            SectionTitle(t("Bạn cầm quân", "You play", "你执", "あなたの色"))
            if (fromFen != null) Segmented(listOf(t("Trắng", "White", "白", "白"), t("Đen", "Black", "黑", "黒")), if (side < 0) 1 else 0) { side = if (it == 0) 1 else -1 }
            else Segmented(listOf(t("Trắng (đi trước)", "White (moves first)", "白（先走）", "白（先手）"), t("Đen", "Black", "黑", "黒"), t("Ngẫu nhiên", "Random", "随机", "ランダム")), when (side) { 1 -> 0; -1 -> 1; else -> 2 }) {
                side = when (it) { 0 -> 1; 1 -> -1; else -> 0 }
            }

            if (handicapEnabled && fromFen == null) {
                SectionTitle(t("Chấp quân", "Handicap", "让子", "ハンデ（駒落ち）"))
                // Máy chấp (dễ hơn) / không chấp / bạn chấp (khó hơn) – bỏ quân cánh trái của bên chấp
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HANDICAP_ROW.forEach { h ->
                        val on = h == handicap
                        Text(
                            if (h == Handicap.NONE) t("Không chấp", "No handicap", "不让子", "互先") else h.label,
                            fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Color.White else Ink,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Red else Color.White)
                                .border(1.dp, if (on) Red else Line, RoundedCornerShape(18.dp)).clickable { handicap = h }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                if (handicap != Handicap.NONE) Text(
                    t("${handicap.gameLabel}: thế đầu bớt quân nên thầy không gọi tên khai cuộc.", "${handicap.gameLabel}: pieces are removed, so openings won't be named.", "${handicap.gameLabel}：开局少子，老师不点评开局名称。", "${handicap.gameLabel}：駒を減らした局面なので、定跡名は出しません。"),
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionTitle(t("Thầy kèm", "Coaching", "老师陪练", "先生のサポート"))
            CardBox {
                SwitchRow(t("Giải thích sau mỗi nước", "Explain after each move", "每步讲解", "一手ごとに解説"), t("Hay / chưa chính xác / sai lầm – kèm lý do và nước tốt hơn", "Good / inaccurate / mistake – with reasons and better moves", "好棋/欠准确/失误——附原因和更好着法", "良い手・緩手・悪手 – 理由とより良い手つき"), explain) { explain = it }
                SwitchRow(t("Cảnh báo trước khi đi", "Warn before moving", "走前警告", "着手前に警告"), t("Tô đỏ ô đi vào sẽ mất quân", "Squares where you'd lose a piece turn red", "会丢子的格子标红", "駒を取られるマスを赤く表示"), warn) { warn = it }
                SwitchRow(t("Gợi ý nước đi", "Move hints", "着法提示", "着手のヒント"), t("Không giới hạn · cứ vài lần sẽ mời xem 1 quảng cáo ngắn (có thể từ chối)", "Unlimited · every few hints you're offered a short ad (you can decline)", "不限次数 · 每隔几次会请你看一段短广告（可拒绝）", "回数無制限 · 数回ごとに短い広告のお誘い（断れます）"), hint) { hint = it }
                SwitchRow(t("Cho phép đi lại", "Allow takebacks", "允许悔棋", "待ったを許可"), if (level <= 4) t("Không giới hạn ở cấp 1–4", "Unlimited at levels 1–4", "1–4级不限次数", "レベル1〜4は無制限") else t("3 lần mỗi ván ở cấp 5+, xem quảng cáo để thêm", "3 per game at level 5+, watch an ad for more", "5级以上每盘3次，看广告可增加", "レベル5以上は1局3回、広告で追加"), undo, last = true) { undo = it }
            }
        }
        PillButton(if (fromFen != null) t("Chơi tiếp với máy", "Play vs computer", "和电脑继续下", "コンピュータと対局") else t("Bắt đầu ván", "Start game", "开始对局", "対局開始"), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            onStart(NewGameChoice(lv, side, explain, warn, hint, undo, if (handicapEnabled && fromFen == null) handicap else Handicap.NONE))
        }
    }
}

private val HANDICAP_ROW = listOf(
    Handicap.NONE, Handicap.AI_KNIGHT, Handicap.AI_PAWN, Handicap.AI_ROOK, Handicap.AI_QUEEN, Handicap.YOU_KNIGHT, Handicap.YOU_ROOK,
)
