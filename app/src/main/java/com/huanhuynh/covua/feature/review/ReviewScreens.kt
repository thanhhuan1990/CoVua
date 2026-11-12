package com.huanhuynh.covua.feature.review

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.coach.CoachTags
import com.huanhuynh.covua.data.AppSettings
import com.huanhuynh.covua.data.db.CardStatusDb
import com.huanhuynh.covua.data.db.ReviewCardEntity
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.feature.train.CoachBox
import com.huanhuynh.covua.feature.train.ReviewTrainer
import com.huanhuynh.covua.ui.CardBox
import com.huanhuynh.covua.ui.PillButton
import com.huanhuynh.covua.ui.SectionTitle
import com.huanhuynh.covua.ui.TopBar
import com.huanhuynh.covua.ui.theme.Good
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

val NOTEBOOK_EMPTY get() = t("Sổ tay còn trống. Đánh vài ván với máy cấp 2 trở lên, thầy sẽ ghi lại những thế đáng ôn.", "Your notebook is empty. Play a few games vs level 2 or higher and your coach will save positions worth reviewing.", "错题本还是空的。和2级以上电脑下几盘，老师会记下值得复习的局面。", "ミスノートはまだ空です。レベル2以上のコンピュータと何局か指すと、先生が復習すべき局面を記録します。")

/**
 * Ôn các thẻ đến hạn (hoặc danh sách [cardIds] cho Buổi tập). Gọi [onFinished] với số thẻ đã ôn.
 * [embedded] = nằm trong Buổi tập (không có thanh trên riêng).
 */
@Composable
fun ReviewSession(
    container: AppContainer,
    settings: AppSettings,
    cardIds: List<Long>?,
    onFinished: (reviewed: Int, correct: Int) -> Unit,
    finishLabel: String = "Xong",
    allowMore: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<ReviewCardEntity>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var reviewed by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var extraMode by remember { mutableStateOf(false) }

    suspend fun load(ids: List<Long>?): List<ReviewCardEntity> {
        val raw = if (ids != null) ids.mapNotNull { container.reviews.card(it) }.filter { it.status == CardStatusDb.ACTIVE } else container.reviews.due()
        // Thẻ của engine cũ: kiểm lại trước khi hỏi
        return raw.mapNotNull { container.reviews.revalidate(it) }
    }
    LaunchedEffect(cardIds) { queue = load(cardIds) }

    fun finish() {
        container.events.log(Ev.REVIEW_SESSION_DONE, mapOf("n" to reviewed.toString(), "ok" to correct.toString()))
        onFinished(reviewed, correct)
    }

    Column(Modifier.fillMaxSize()) {
        val q = queue
        when {
            q == null -> Text(t("Thầy đang mở sổ…", "Opening your notebook…", "正在打开错题本…", "ミスノートを開いています…"), color = Muted, modifier = Modifier.padding(16.dp))
            q.isEmpty() || index >= q.size -> {
                Spacer(Modifier.weight(1f))
                val body = when {
                    q.isEmpty() && reviewed == 0 -> t("Hôm nay chưa có thế nào đến hạn ôn.", "Nothing to review today.", "今天没有待复习的局面。", "今日の復習はありません。")
                    else -> t("Đã ôn $reviewed thế, đúng $correct. Thầy ghi vào sổ rồi – thế nào sai sẽ quay lại sớm hơn.", "Reviewed $reviewed, $correct correct. Noted – the ones you missed will come back sooner.", "复习了${reviewed}个，答对${correct}个。已记录——答错的会更早再出现。", "${reviewed}問復習、${correct}問正解。記録しました – 間違えた局面は早めにまた出ます。")
                }
                var more by remember { mutableIntStateOf(0) }
                LaunchedEffect(reviewed) { more = runCatching { container.reviews.due().size }.getOrDefault(0) }
                CoachBox(t("Sổ tay sai lầm", "Mistake notebook", "错题本", "ミスノート"), body, null) {
                    PillButton(finishLabel) { finish() }
                    if (allowMore && more > 0 && !extraMode) PillButton(t("Ôn thêm ($more)", "Review more ($more)", "再复习（${more}）", "もっと復習（${more}）"), primary = false) {
                        extraMode = true
                        scope.launch { queue = load(null); index = 0 }
                    }
                }
            }
            else -> {
                val card = q[index]
                Text(t("Thế ${index + 1}/${q.size} · ${CoachTags.theme(card.themeTags)} · hộp ${card.box}/5", "${index + 1}/${q.size} · ${CoachTags.theme(card.themeTags)} · box ${card.box}/5", "${index + 1}/${q.size} · ${CoachTags.theme(card.themeTags)} · 第${card.box}/5盒", "${index + 1}/${q.size} · ${CoachTags.theme(card.themeTags)} · ボックス${card.box}/5"), fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 16.dp))
                ReviewTrainer(container, settings, card, onAnswered = { _, ok ->
                    reviewed++
                    if (ok) correct++
                    scope.launch { runCatching { container.activity.recordReview() } }
                }) { _ ->
                    PillButton(if (index + 1 < q.size) t("Thế tiếp ▶", "Next ▶", "下一个 ▶", "次へ ▶") else "Xong") { index++ }
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding()) {
        TopBar(t("Ôn Sổ tay", "Review notebook", "复习错题本", "ミスノートを復習"), onBack)
        ReviewSession(container, settings, null, onFinished = { _, _ -> onBack() }, finishLabel = t("Về Sổ tay", "Back to notebook", "返回错题本", "ミスノートに戻る"))
    }
}

/** Mục Sổ tay trong màn Tiến bộ: đến hạn hôm nay, đã sửa, tổng, chủ đề, Ôn ngay, danh sách thẻ. */
@Composable
fun NotebookSection(container: AppContainer, onReview: () -> Unit) {
    val cards by remember { container.reviews.allFlow() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = container.clock.today()
    val due = cards.count { it.status == CardStatusDb.ACTIVE && it.dueDay <= today }
    val fixed = cards.count { it.status == CardStatusDb.GRADUATED }
    var showAll by remember { mutableStateOf(false) }
    SectionTitle(t("Sổ tay sai lầm", "Mistake notebook", "错题本", "ミスノート"))
    CardBox {
        if (cards.isEmpty()) {
            Text(NOTEBOOK_EMPTY, fontSize = 13.sp, color = Muted, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 10.dp))
            return@CardBox
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Stat("$due", t("đến hạn hôm nay", "due today", "今日到期", "今日が期限"), Red)
            Stat("$fixed", t("đã sửa", "fixed", "已改正", "克服済み"), Good)
            Stat("${cards.size}", t("tổng số thế", "total", "总数", "合計"), Ink)
        }
        val themes = cards.filter { it.status == CardStatusDb.ACTIVE }.flatMap { CoachTags.themes(it.themeTags).take(1) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        if (themes.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            themes.forEach { (t, n) ->
                Text("$t · $n", fontSize = 12.sp, color = Ink, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3EEE6)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        if (due > 0) PillButton(t("Ôn ngay ($due)", "Review now ($due)", "立即复习（${due}）", "今すぐ復習（${due}）"), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onReview)
        val list = if (showAll) cards else cards.take(5)
        list.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(CoachTags.theme(c.themeTags) + (if (c.level > 0) t(" · cấp ${c.level}", " · level ${c.level}", " · ${c.level}级", " · レベル${c.level}") else ""), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        when (c.status) {
                            CardStatusDb.GRADUATED -> t("Đã sửa ✓", "Fixed ✓", "已改正 ✓", "克服済み ✓")
                            else -> if (c.dueDay <= today) t("Đến hạn hôm nay · hộp ${c.box}/5", "Due today · box ${c.box}/5", "今日到期 · 第${c.box}/5盒", "今日が期限 · ボックス${c.box}/5") else t("Ôn sau ${c.dueDay - today} ngày · hộp ${c.box}/5", "Review in ${c.dueDay - today} days · box ${c.box}/5", "${c.dueDay - today}天后复习 · 第${c.box}/5盒", "${c.dueDay - today}日後に復習 · ボックス${c.box}/5")
                        },
                        fontSize = 12.sp, color = if (c.status == CardStatusDb.GRADUATED) Good else Muted,
                    )
                }
                if (c.lapses > 0) Text(t("sai lại ${c.lapses}", "missed again ${c.lapses}×", "再错${c.lapses}次", "また間違えた ${c.lapses}回"), fontSize = 11.sp, color = Red)
            }
        }
        if (cards.size > 5) Text(
            if (showAll) t("Thu gọn", "Show less", "收起", "閉じる") else t("Xem tất cả ${cards.size} thế", "Show all ${cards.size}", "查看全部${cards.size}个", "全${cards.size}件を表示"), color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showAll = !showAll }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.5.sp, color = Muted)
    }
}
