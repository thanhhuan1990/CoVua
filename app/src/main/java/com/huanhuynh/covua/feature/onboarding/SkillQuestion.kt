package com.huanhuynh.covua.feature.onboarding

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.engine.Handicap
import com.huanhuynh.covua.ui.TeacherAvatar
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Red

/** Bốn câu trả lời "Bạn chơi cờ vua thế nào?" → cấp máy, chấp quân, bậc bài tập. */
enum class SkillAnswer(val id: Int, private val labels: List<String>, val level: Int, val band: Int, val handicap: Handicap) {
    NEW(1, listOf("Mới tập", "Just learning", "刚入门", "始めたばかり"), 1, 1, Handicap.AI_ROOK),
    KNOWS(2, listOf("Biết đi, hay thua đòn", "I know the moves, often fall for tactics", "会走棋，常中招", "ルールは知っているが、よく戦術にかかる"), 2, 2, Handicap.NONE),
    CLUB(3, listOf("Chơi khá ở câu lạc bộ", "Decent club player", "俱乐部里算不错", "クラブでまずまず"), 4, 3, Handicap.NONE),
    STRONG(4, listOf("Đánh mạnh", "Strong player", "棋力很强", "強い"), 6, 4, Handicap.NONE);

    val label: String get() = t(labels[0], labels[1], labels[2], labels[3])

    companion object { fun of(id: Int?) = entries.firstOrNull { it.id == id } }
}

object Onboarding {
    /** Áp câu trả lời (kể cả "Bỏ qua" = câu 2). Có thể đổi lại ở Cài đặt → "Trình độ của tôi". */
    suspend fun apply(c: AppContainer, a: SkillAnswer, bigText: Boolean, skipped: Boolean = false) {
        val s = c.settings.current()
        c.settings.setNewGamePrefs(a.level, s.sideChoice, s.coachExplain, if (a == SkillAnswer.NEW) true else s.coachWarn, s.coachHint, s.allowUndo)
        c.settings.setHandicap(a.handicap.id)
        c.settings.setBigText(bigText)
        c.settings.setSkillAsked()
        runCatching { c.player.update { it.copy(skillAnswer = a.id, puzzleBand = a.band, bandOkStreak = 0, bandFailStreak = 0) } }
        c.events.log(Ev.ONBOARD_SKILL, mapOf("answer" to a.id.toString(), "skipped" to skipped.toString(), "big_text" to bigText.toString()))
    }
}

/** Chữ to hơn 20% cho toàn app (người lớn tuổi). */
@Composable
fun BigTextScope(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * 1.2f), content = content)
}

@Composable
fun SkillQuestionDialog(initialBigText: Boolean, current: SkillAnswer? = null, onAnswer: (SkillAnswer, Boolean) -> Unit, onSkip: (Boolean) -> Unit) {
    var big by remember { mutableStateOf(initialBigText) }
    Dialog(onDismissRequest = { onSkip(big) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeacherAvatar(44.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(t("Bạn chơi cờ vua thế nào?", "How well do you play chess?", "你的国际象棋水平如何？", "チェスの腕前はどのくらい？"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
                    Text(t("Thầy chọn cấp và bài tập cho vừa sức. Đổi lại lúc nào cũng được.", "Your coach picks a level and exercises to match. You can change it any time.", "老师会选合适的等级和练习，随时可以更改。", "先生がレベルと練習問題を合わせます。いつでも変更できます。"), fontSize = 13.sp, color = Muted)
                }
            }
            SkillAnswer.entries.forEach { a ->
                val on = a == current
                Text(
                    a.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Ink,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) Red else Color(0xFFFBF7F0)).border(1.dp, if (on) Red else Line, RoundedCornerShape(16.dp))
                        .clickable { onAnswer(a, big) }.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
            Row(Modifier.fillMaxWidth().clickable { big = !big }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = big, onCheckedChange = { big = it })
                Text(t("Tôi muốn chữ to hơn", "I'd like larger text", "我想要更大的字", "文字を大きくしたい"), fontSize = 15.sp, color = Ink)
            }
            TextButton(onClick = { onSkip(big) }, modifier = Modifier.align(Alignment.End)) { Text(t("Bỏ qua", "Skip", "跳过", "スキップ"), color = Muted) }
        }
    }
}
