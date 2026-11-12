package com.huanhuynh.covua.ui

import com.huanhuynh.covua.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanhuynh.covua.ui.theme.Chip
import com.huanhuynh.covua.ui.theme.Gold
import com.huanhuynh.covua.ui.theme.Ink
import com.huanhuynh.covua.ui.theme.Line
import com.huanhuynh.covua.ui.theme.Muted
import com.huanhuynh.covua.ui.theme.Red
import com.huanhuynh.covua.ui.theme.RedDark

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Quay lại", "Back", "返回", "戻る"), tint = Ink) }
        } else Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Ấn "師" của thầy. */
@Composable
fun TeacherAvatar(size: Dp = 38.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(RedDark).border(2.dp, Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("師", color = Gold, fontSize = (size.value * 0.5f).sp, fontFamily = FontFamily.Serif) }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun PillButton(text: String, primary: Boolean = true, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when { !enabled -> Chip.copy(alpha = 0.6f); primary -> Red; else -> Chip }
    val fg = when { !enabled -> Muted; primary -> Color.White; else -> Ink }
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(bg).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, textAlign = TextAlign.Center) }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        content = content,
    )
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Red))
    }
    if (!last) HorizontalDivider(color = Line)
}

@Composable
fun NavRow(title: String, sub: String? = null, icon: ImageVector? = null, last: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Muted)
        }
        Text("›", color = Muted, fontSize = 18.sp)
    }
    if (!last) HorizontalDivider(color = Line)
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(Chip).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if (i == selected) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center) }
        }
    }
}
