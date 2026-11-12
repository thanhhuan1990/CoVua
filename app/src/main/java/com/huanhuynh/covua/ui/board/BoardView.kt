package com.huanhuynh.covua.ui.board

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.huanhuynh.covua.data.PieceStyle
import com.huanhuynh.covua.engine.Moves
import com.huanhuynh.covua.i18n.I18n
import com.huanhuynh.covua.i18n.Lang
import com.huanhuynh.covua.ui.game.Arrow
import com.huanhuynh.covua.ui.game.ArrowKind
import com.huanhuynh.covua.ui.theme.Bad
import com.huanhuynh.covua.ui.theme.Best
import com.huanhuynh.covua.ui.theme.Good
import kotlin.math.abs
import kotlin.math.atan2

/** Hình quân (bộ đặc = quân Đen; bộ rỗng = nét chi tiết vẽ lên quân Trắng). ︎: ép kiểu chữ, không phải emoji. */
private val SOLID = arrayOf("", "♚", "♛", "♜", "♝", "♞", "♟︎")
private val OUTLINE = arrayOf("", "♔", "♕", "♖", "♗", "♘", "♙")
private val LETTER_VI = arrayOf("", "Vua", "Hậu", "Xe", "Tượng", "Mã", "Tốt")
private val LETTER_ZH = arrayOf("", "王", "后", "车", "象", "马", "兵")
private val LETTER_INTL = arrayOf("", "K", "Q", "R", "B", "N", "P")
private val LETTERS get() = when (I18n.lang) { Lang.VI -> LETTER_VI; Lang.ZH -> LETTER_ZH; else -> LETTER_INTL }

val PieceWhite = Color(0xFFFBF7EE)
val PieceBlack = Color(0xFF1C1C1C)

// Bàn gỗ: ô sáng màu kem, ô tối màu gỗ nâu, khung gỗ sẫm
private val Frame = Color(0xFF6B3E1E)
private val LightSq = Color(0xFFF0D9B5)
private val DarkSq = Color(0xFFB58863)
private val LastMoveTint = Color(0x66F2D33B)

/**
 * Bàn cờ vua vẽ bằng Canvas. [flipped] = lật (Đen ở dưới). Chạm vào ô → [onTap](ô, 0–63).
 * [lastMove] được làm hoạt ảnh trượt quân khi [version] đổi.
 */
@Composable
fun BoardView(
    board: IntArray,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    style: PieceStyle = PieceStyle.CLASSIC,
    selected: Int = -1,
    targets: Set<Int> = emptySet(),
    danger: Set<Int> = emptySet(),
    lastMove: Int = 0,
    checkSquare: Int = -1,
    arrows: List<Arrow> = emptyList(),
    version: Int = 0,
    showTargets: Boolean = true,
    showCoords: Boolean = true,
    onTap: ((Int) -> Unit)? = null,
) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(version) {
        if (lastMove != 0) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(200))
        }
    }
    val glyphPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    }
    val letterPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    }
    val coordPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(flipped, onTap) {
                if (onTap == null) return@pointerInput
                detectTapGestures { o ->
                    val g = geometry(size.width.toFloat(), size.height.toFloat())
                    val vc = ((o.x - g.left) / g.cell).toInt()
                    val vr = ((o.y - g.top) / g.cell).toInt()
                    if (o.x < g.left || o.y < g.top || vc !in 0..7 || vr !in 0..7) return@detectTapGestures
                    val (c, r) = if (flipped) (7 - vc) to vr else vc to (7 - vr)
                    onTap(r * 8 + c)
                }
            },
    ) {
        val g = geometry(size.width, size.height)
        fun topLeft(sq: Int): Offset {
            val c = sq and 7
            val r = sq shr 3
            val (vc, vr) = if (flipped) (7 - c) to r else c to (7 - r)
            return Offset(g.left + vc * g.cell, g.top + vr * g.cell)
        }
        fun center(sq: Int): Offset = topLeft(sq) + Offset(g.cell / 2, g.cell / 2)

        // Khung + ô
        drawRoundRect(Frame, cornerRadius = CornerRadius(size.minDimension * 0.02f))
        for (sq in 0 until 64) {
            val light = ((sq shr 3) + (sq and 7)) % 2 == 1
            drawRect(if (light) LightSq else DarkSq, topLeft(sq), Size(g.cell, g.cell))
        }
        // Vân gỗ nhẹ
        drawRect(
            Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x00FFFFFF), Color(0x1A5A2D0C)), Offset(g.left, g.top), Offset(g.left + 8 * g.cell, g.top + 8 * g.cell)),
            Offset(g.left, g.top), Size(8 * g.cell, 8 * g.cell),
        )
        // Ô nước vừa đi
        if (lastMove != 0) for (sq in intArrayOf(Moves.from(lastMove), Moves.to(lastMove))) drawRect(LastMoveTint, topLeft(sq), Size(g.cell, g.cell))
        if (selected >= 0) drawRect(Best.copy(alpha = 0.35f), topLeft(selected), Size(g.cell, g.cell))
        if (checkSquare >= 0) drawCircle(
            Brush.radialGradient(0f to Bad, 0.6f to Bad.copy(alpha = 0.5f), 1f to Color.Transparent, center = center(checkSquare), radius = g.cell * 0.62f),
            g.cell * 0.62f, center(checkSquare),
        )
        // Toạ độ (chữ cột ở hàng dưới, số hàng ở cột trái – nằm trong ô như bàn cờ thật)
        if (showCoords) {
            coordPaint.textSize = g.cell * 0.2f
            for (i in 0..7) {
                val file = if (flipped) 7 - i else i
                val rank = if (flipped) i else 7 - i
                val sqBottom = (if (flipped) 7 else 0) * 8 + file
                coordPaint.color = (if (((sqBottom shr 3) + (sqBottom and 7)) % 2 == 1) DarkSq else LightSq).toArgb()
                coordPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText("${'a' + file}", g.left + (i + 1) * g.cell - g.cell * 0.06f, g.top + 8 * g.cell - g.cell * 0.07f, coordPaint)
                val sqLeft = rank * 8 + (if (flipped) 7 else 0)
                coordPaint.color = (if (((sqLeft shr 3) + (sqLeft and 7)) % 2 == 1) DarkSq else LightSq).toArgb()
                coordPaint.textAlign = Paint.Align.LEFT
                drawContext.canvas.nativeCanvas.drawText("${rank + 1}", g.left + g.cell * 0.06f, g.top + i * g.cell + g.cell * 0.24f, coordPaint)
            }
        }
        // Quân (quân vừa đi trượt theo hoạt ảnh)
        val movingTo = if (lastMove != 0 && anim.value < 1f) Moves.to(lastMove) else -1
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0 || sq == movingTo) continue
            drawPiece(center(sq), g.cell, p, style, glyphPaint, letterPaint)
        }
        if (movingTo >= 0 && board[movingTo] != 0) {
            val a = center(Moves.from(lastMove)); val b = center(movingTo)
            drawPiece(Offset(a.x + (b.x - a.x) * anim.value, a.y + (b.y - a.y) * anim.value), g.cell, board[movingTo], style, glyphPaint, letterPaint)
        }
        // Ô đi được / nguy hiểm
        if (showTargets) for (t in targets) {
            val o = center(t)
            val col = if (t in danger) Bad else Good
            if (board[t] != 0) {
                drawCircle(col.copy(alpha = 0.85f), g.cell * 0.47f, o, style = Stroke(g.cell * 0.07f))
            } else {
                drawCircle(col.copy(alpha = 0.25f), g.cell * 0.3f, o)
                drawCircle(col.copy(alpha = 0.9f), g.cell * 0.13f, o)
            }
        }
        // Mũi tên
        for (a in arrows) {
            val col = when (a.kind) { ArrowKind.HINT -> Best; ArrowKind.THREAT -> Bad; ArrowKind.BETTER -> Good }
            drawArrow(center(a.from), center(a.to), col, g.cell * 0.14f)
        }
    }
}

private data class Geo(val left: Float, val top: Float, val cell: Float)

private fun geometry(w: Float, h: Float): Geo {
    val frame = minOf(w, h) * 0.015f
    val cell = (minOf(w, h) - 2 * frame) / 8f
    return Geo((w - cell * 8) / 2f, (h - cell * 8) / 2f, cell)
}

/** Vẽ một quân ở tâm [o], ô cạnh [cell]. Dùng chung cho ảnh chia sẻ. */
fun DrawScope.drawPiece(o: Offset, cell: Float, p: Int, style: PieceStyle, glyph: Paint, letter: Paint) {
    val white = p > 0
    val t = abs(p)
    val canvas = drawContext.canvas.nativeCanvas
    if (style == PieceStyle.CLASSIC) {
        glyph.textSize = cell * 0.86f
        val fm = glyph.fontMetrics
        val y = o.y - (fm.ascent + fm.descent) / 2f
        // Bóng
        glyph.style = Paint.Style.FILL
        glyph.color = 0x40000000
        canvas.drawText(SOLID[t], o.x + cell * 0.02f, y + cell * 0.035f, glyph)
        if (white) {
            // Thân trắng (hình đặc tô trắng) + nét chi tiết (hình rỗng tô đen)
            glyph.color = PieceWhite.toArgb()
            canvas.drawText(SOLID[t], o.x, y, glyph)
            glyph.color = 0xFF202020.toInt()
            canvas.drawText(OUTLINE[t], o.x, y, glyph)
        } else {
            // Viền sáng mảnh để quân Đen nổi trên ô tối
            glyph.style = Paint.Style.STROKE
            glyph.strokeWidth = cell * 0.035f
            glyph.color = 0x99FFFFFF.toInt()
            canvas.drawText(SOLID[t], o.x, y, glyph)
            glyph.style = Paint.Style.FILL
            glyph.color = PieceBlack.toArgb()
            canvas.drawText(SOLID[t], o.x, y, glyph)
        }
        glyph.style = Paint.Style.FILL
    } else {
        val r = cell * 0.43f
        drawCircle(Color(0x55000000), r, o + Offset(0f, r * 0.12f))
        drawCircle(if (white) PieceWhite else PieceBlack, r, o)
        drawCircle(if (white) Color(0xFF3A3A3A) else Color(0xFFE8E2D4), r * 0.86f, o, style = Stroke(r * 0.07f))
        val text = LETTERS[t]
        letter.color = (if (white) Color(0xFF1C1C1C) else Color(0xFFF5F0E6)).toArgb()
        letter.textSize = when {
            text.length <= 1 -> r * 1.05f
            text.length <= 3 -> r * 0.62f
            else -> r * 0.48f
        }
        val fm = letter.fontMetrics
        canvas.drawText(text, o.x, o.y - (fm.ascent + fm.descent) / 2f, letter)
    }
}

private fun DrawScope.drawArrow(a: Offset, b: Offset, color: Color, width: Float) {
    val ang = atan2(b.y - a.y, b.x - a.x)
    val head = width * 2.6f
    val dx = kotlin.math.cos(ang); val dy = kotlin.math.sin(ang)
    val end = Offset(b.x - dx * head * 0.8f, b.y - dy * head * 0.8f)
    drawLine(color.copy(alpha = 0.8f), a, end, width, cap = StrokeCap.Round)
    rotate(Math.toDegrees(ang.toDouble()).toFloat(), pivot = b) {
        val path = Path().apply {
            moveTo(b.x, b.y)
            lineTo(b.x - head, b.y - head * 0.62f)
            lineTo(b.x - head, b.y + head * 0.62f)
            close()
        }
        drawPath(path, color.copy(alpha = 0.9f))
    }
}
