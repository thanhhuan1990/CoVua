package com.huanhuynh.covua.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huanhuynh.covua.i18n.t
import com.huanhuynh.covua.ui.TeacherAvatar
import com.huanhuynh.covua.ui.theme.Chip
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red

/** Kết quả hướng dẫn: giữ thầy kèm, chuyển sang "Chỉ đánh cờ", hoặc bỏ qua (không đổi gì). */
enum class CoachTutorialChoice(val id: String) { COACH("coach"), PLAY_ONLY("play_only"), SKIP("skip") }

private fun tx(vi: String, en: String, zh: String, ja: String) = t(vi, en, zh, ja)

/**
 * Hướng dẫn 2 trang về "thầy": thầy làm gì trong ván, và 3 cách tắt thầy (Chỉ đánh cờ).
 * Hiện 1 lần sau khi chọn ngôn ngữ lần đầu; xem lại được ở Cài đặt → Thầy kèm.
 */
@Composable
fun CoachTutorialDialog(playOnly: Boolean, onDone: (CoachTutorialChoice) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = { if (page > 0) page-- else onDone(CoachTutorialChoice.SKIP) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dots(page)
                Spacer(Modifier.weight(1f))
                if (page == 0) TextButton(onClick = { onDone(CoachTutorialChoice.SKIP) }) {
                    Text(tx("Bỏ qua", "Skip", "跳过", "スキップ"), color = Muted)
                } else TextButton(onClick = { page = 0 }) {
                    Text(tx("‹ Quay lại", "‹ Back", "‹ 返回", "‹ 戻る"), color = Muted)
                }
            }
            if (page == 0) MeetCoachPage(onNext = { page = 1 }) else TurnOffPage(playOnly, onDone)
        }
    }
}

@Composable
private fun Dots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) { i ->
            Box(Modifier.size(width = if (i == page) 20.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i == page) Red else Line))
        }
    }
}

@Composable
private fun MeetCoachPage(onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        TeacherAvatar(72.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            tx("Gặp thầy của bạn", "Meet your coach", "认识你的老师", "先生を紹介します"),
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tx("Khi bạn chơi với máy, thầy ngồi cạnh kèm từng ván:", "When you play the computer, your coach sits beside you:", "和电脑下棋时，老师会在旁边陪练：", "コンピュータと対局するとき、先生がそばでサポートします："),
            fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(14.dp))
    Feature("💬", tx("Nhận xét từng nước", "Comments on every move", "点评每一步", "一手ごとに解説"),
        tx("Nước nào tốt, nước nào chưa tốt và vì sao.", "Which moves are good, which aren't, and why.", "哪步好、哪步不好，以及原因。", "どの手が良いか悪いか、その理由も。"))
    Feature("⚠️", tx("Cảnh báo trước khi đi", "Warns before you move", "走前警告", "着手前に警告"), tx("Tô đỏ ô đi vào sẽ mất quân.", "Squares where you'd lose a piece turn red.", "会丢子的格子标红。", "駒を取られるマスを赤く表示します。"))
    Feature("💡", tx("Gợi ý khi bạn bí", "Hints when you're stuck", "卡住时给提示", "迷ったらヒント"),
        tx("Thầy chỉ hướng đi, bạn tự quyết định.", "Your coach points the way, you make the call.", "老师指方向，由你决定。", "先生が方向を示し、決めるのはあなた。"))
    Feature("📊", tx("Phân tích sau ván", "Post-game analysis", "赛后分析", "終局後の分析"),
        tx("Xem lại những nước quan trọng nhất của ván.", "Review the key moments of the game.", "回顾本局最关键的几步。", "対局の大事な場面を振り返ります。"))
    Spacer(Modifier.height(14.dp))
    BigButton(tx("Tiếp: cách tắt thầy ›", "Next: turning the coach off ›", "下一步：如何关闭老师 ›", "次へ：先生をオフにする方法 ›"), primary = true, onClick = onNext)
}

@Composable
private fun TurnOffPage(playOnly: Boolean, onDone: (CoachTutorialChoice) -> Unit) {
    Text(
        tx("Muốn tự đánh? Tắt thầy", "Want to play on your own? Turn the coach off", "想自己下？关闭老师", "自分だけで指したい？先生をオフに"),
        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        tx("Chế độ \"Chỉ đánh cờ\": không lời thầy, không cảnh báo, không gợi ý – chỉ bạn và máy. Sau ván vẫn xem được phân tích.",
            "\"Just play\" mode: no comments, warnings or hints – just you and the computer. Analysis is still available afterwards.",
            "\"只下棋\"模式：没有点评、警告和提示——只有你和电脑。赛后仍可查看分析。",
            "「対局のみ」モード：解説・警告・ヒントなし – あなたとコンピュータだけ。終局後の分析は見られます。"),
        fontSize = 13.5.sp, color = Muted,
    )
    Spacer(Modifier.height(12.dp))
    Step(1, tx("Khi tạo ván mới", "When starting a new game", "开新局时", "新しい対局を始めるとき"),
        tx("Chọn \"Chỉ đánh cờ\" thay vì \"Có thầy kèm\".", "Pick \"Just play\" instead of \"With coach\".", "选\"只下棋\"而不是\"老师陪练\"。", "「先生あり」ではなく「対局のみ」を選びます。")) {
        MockSegmented(tx("Có thầy kèm", "With coach", "老师陪练", "先生あり"), tx("Chỉ đánh cờ", "Just play", "只下棋", "対局のみ"))
    }
    Step(2, tx("Đang chơi giữa ván", "In the middle of a game", "对局中", "対局の途中で"),
        tx("Bấm ⋮ ở góc trên → \"Chỉ đánh cờ (tắt thầy kèm)\". Muốn thầy quay lại cũng bấm ở đó.",
            "Tap ⋮ at the top → \"Just play (coaching off)\". Tap there again to bring the coach back.",
            "点右上角 ⋮ →\"只下棋（关闭老师）\"。想让老师回来也在这里。",
            "上の ⋮ →「対局のみ（先生オフ）」。先生を戻すときも同じ場所から。")) {
        MockChip("⋮  " + tx("Chỉ đánh cờ (tắt thầy kèm)", "Just play (coaching off)", "只下棋（关闭老师）", "対局のみ（先生オフ）"))
    }
    Step(3, tx("Tắt cho mọi ván", "Off for every game", "所有对局都关闭", "すべての対局でオフ"),
        tx("Cài đặt ⚙ → Thầy kèm → bật \"Chỉ đánh cờ với máy\".", "Settings ⚙ → Coaching → turn on \"Just play the computer\".", "设置 ⚙ → 老师陪练 → 开启\"只和电脑下棋\"。", "設定 ⚙ → 先生のサポート →「コンピュータと対局のみ」をオン。")) {
        MockChip("⚙  " + tx("Chỉ đánh cờ với máy", "Just play the computer", "只和电脑下棋", "コンピュータと対局のみ") + "  ●")
    }
    Spacer(Modifier.height(14.dp))
    Text(tx("Bạn muốn bắt đầu thế nào?", "How would you like to start?", "你想怎么开始？", "どちらで始めますか？"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(8.dp))
    BigButton("🎓  " + tx("Học cùng thầy", "Learn with the coach", "跟老师学", "先生と一緒に学ぶ"), primary = !playOnly) { onDone(CoachTutorialChoice.COACH) }
    Spacer(Modifier.height(8.dp))
    BigButton("♟  " + tx("Chỉ đánh cờ (tắt thầy)", "Just play (coach off)", "只下棋（关闭老师）", "対局のみ（先生オフ）"), primary = playOnly) { onDone(CoachTutorialChoice.PLAY_ONLY) }
    Spacer(Modifier.height(6.dp))
    Text(
        tx("Đổi lại lúc nào cũng được.", "You can change this any time.", "随时可以更改。", "いつでも変更できます。"),
        fontSize = 12.sp, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
    )
}

@Composable
private fun Feature(icon: String, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Chip), contentAlignment = Alignment.Center) { Text(icon, fontSize = 18.sp) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(sub, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, sub: String, mock: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(sub, fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            mock()
        }
    }
}

/** Ảnh minh hoạ nút chọn "Có thầy kèm | Chỉ đánh cờ" (không bấm được). */
@Composable
private fun MockSegmented(left: String, right: String) {
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Chip).padding(3.dp)) {
        Text(left, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        Text(right, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(Red).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun MockChip(text: String) {
    Text(text, fontSize = 12.sp, color = Ink,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun BigButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (primary) Red else Color.White).border(1.dp, if (primary) Red else Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.White else Ink, textAlign = TextAlign.Center) }
}
