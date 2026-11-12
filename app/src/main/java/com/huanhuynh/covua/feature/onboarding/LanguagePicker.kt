package com.huanhuynh.covua.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.i18n.AppLang
import com.huanhuynh.covua.ui.theme.Chip
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Paper
import com.huanhuynh.covua.ui.theme.Red

/** Chọn ngôn ngữ lần đầu mở app (che toàn màn hình, không bỏ qua được). Gợi ý sẵn ngôn ngữ của máy. */
@Composable
fun LanguagePicker(onPick: (String) -> Unit) {
    val suggested = remember { AppLang.suggested() }
    Box(
        Modifier.fillMaxSize().background(Color(0xE6201010)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("♚", fontSize = 40.sp, color = Red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Ngôn ngữ · Language · 语言 · 言語", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(16.dp))
            AppLang.SUPPORTED.forEach { tag ->
                val hi = tag == suggested
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).height(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (hi) Red else Chip).border(1.dp, if (hi) Red else Line, RoundedCornerShape(14.dp))
                        .clickable { onPick(tag) },
                    contentAlignment = Alignment.Center,
                ) { Text(AppLang.label(tag), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (hi) Color.White else Ink) }
            }
        }
    }
}

/** Hàng chọn ngôn ngữ trong Cài đặt. */
@Composable
fun LanguageRow(current: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLang.SUPPORTED.forEach { tag ->
            val sel = tag == current
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (sel) Red else Chip)
                    .clickable { if (!sel) onPick(tag) },
                contentAlignment = Alignment.Center,
            ) { Text(AppLang.shortLabel(tag), fontSize = 12.5.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = if (sel) Color.White else Ink) }
        }
    }
}
