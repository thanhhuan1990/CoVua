package com.huanhuynh.covua.ui.board

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs

/**
 * Hình quân cờ vua vẽ bằng vector (không phụ thuộc font máy – font thiếu ký tự ♚♛♜ hoặc hiện emoji sẽ không còn ảnh hưởng).
 * Toạ độ thiết kế 0..100, tâm (50, 50). Dùng chung cho bàn cờ (Compose → nativeCanvas), ảnh chia sẻ và ô chọn phong cấp.
 * Sinh từ tools/pieces/gen.py (cùng nguồn với ảnh xem trước tools/pieces/preview.png) – sửa hình ở đó rồi sinh lại.
 */
object PieceArt {
    private const val WHITE_FILL = 0xFFFBF7EE.toInt()
    private const val WHITE_LINE = 0xFF2A2420.toInt()
    private const val BLACK_FILL = 0xFF2A2A2D.toInt()
    private const val BLACK_LINE = 0xFF0B0B0B.toInt()
    private const val BLACK_DETAIL = 0xFFE3DCCD.toInt()
    private const val SHADOW = 0x40000000

    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun circle(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
    private fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) = Path().apply { addRoundRect(RectF(l, t, r, b), rad, rad, Path.Direction.CW) }

    /** Các mảng hình theo thứ tự vẽ (mảng sau đè mảng trước), chỉ số = loại quân (1 Vua … 6 Tốt). */
    private val SHAPES: Array<List<Path>> = Array(7) { t ->
        when (t) {
        1 -> listOf(
            path { moveTo(32f, 77f); lineTo(38f, 52f); lineTo(62f, 52f); lineTo(68f, 77f); close() },
            path { moveTo(31f, 49f); cubicTo(21f, 37f, 28f, 23f, 41f, 28f); cubicTo(45f, 22f, 55f, 22f, 59f, 28f); cubicTo(72f, 23f, 79f, 37f, 69f, 49f); close() },
            rrect(34f, 46f, 66f, 54f, 3f),
            rrect(47f, 5f, 53f, 27f, 1.5f),
            rrect(41f, 11f, 59f, 17f, 1.5f),
            rrect(22f, 76f, 78f, 88f, 4f),
        )
        2 -> listOf(
            path { moveTo(32f, 77f); lineTo(38f, 52f); lineTo(62f, 52f); lineTo(68f, 77f); close() },
            path { moveTo(24f, 27f); lineTo(36f, 44f); lineTo(38f, 21f); lineTo(46f, 42f); lineTo(50f, 16f); lineTo(54f, 42f); lineTo(62f, 21f); lineTo(64f, 44f); lineTo(76f, 27f); lineTo(67f, 49f); lineTo(33f, 49f); close() },
            rrect(34f, 46f, 66f, 54f, 3f),
            circle(24f, 25f, 4.2f),
            circle(38f, 19f, 4.2f),
            circle(50f, 13f, 4.2f),
            circle(62f, 19f, 4.2f),
            circle(76f, 25f, 4.2f),
            rrect(22f, 76f, 78f, 88f, 4f),
        )
        3 -> listOf(
            path { moveTo(34f, 77f); lineTo(37f, 42f); lineTo(63f, 42f); lineTo(66f, 77f); close() },
            path { moveTo(28f, 42f); lineTo(28f, 17f); lineTo(38f, 17f); lineTo(38f, 25f); lineTo(45f, 25f); lineTo(45f, 17f); lineTo(55f, 17f); lineTo(55f, 25f); lineTo(62f, 25f); lineTo(62f, 17f); lineTo(72f, 17f); lineTo(72f, 42f); close() },
            rrect(24f, 76f, 76f, 88f, 4f),
        )
        4 -> listOf(
            path { moveTo(38f, 77f); quadTo(42f, 66f, 44f, 60f); lineTo(56f, 60f); quadTo(58f, 66f, 62f, 77f); close() },
            rrect(35f, 54f, 65f, 61f, 3f),
            path { moveTo(50f, 15f); cubicTo(66f, 26f, 69f, 42f, 60f, 55f); lineTo(40f, 55f); cubicTo(31f, 42f, 34f, 26f, 50f, 15f); close() },
            circle(50f, 11f, 5f),
            rrect(24f, 76f, 76f, 88f, 4f),
        )
        5 -> listOf(
            path { moveTo(30f, 77f); lineTo(33f, 63f); cubicTo(36f, 55f, 42f, 51f, 45f, 46f); cubicTo(38f, 49f, 32f, 51f, 27f, 48f); cubicTo(21f, 45f, 21f, 38f, 26f, 34f); lineTo(39f, 21f); lineTo(41f, 10f); lineTo(50f, 17f); cubicTo(65f, 20f, 75f, 34f, 73f, 52f); cubicTo(72f, 62f, 69f, 70f, 69f, 77f); close() },
            rrect(24f, 76f, 76f, 88f, 4f),
        )
        6 -> listOf(
            path { moveTo(37f, 77f); quadTo(39f, 60f, 44f, 47f); lineTo(56f, 47f); quadTo(61f, 60f, 63f, 77f); close() },
            rrect(37f, 40f, 63f, 48f, 3f),
            circle(50f, 28f, 12f),
            rrect(26f, 76f, 74f, 88f, 4f),
        )
            else -> emptyList()
        }
    }

    /** Nét chi tiết (không tô). */
    private val DETAILS: Array<List<Path>> = Array(7) { t ->
        when (t) {
        1 -> listOf(path { moveTo(50f, 30f); lineTo(50f, 46f) })
        3 -> listOf(path { moveTo(36f, 70f); lineTo(64f, 70f) })
        4 -> listOf(path { moveTo(54f, 27f); lineTo(45f, 41f) })
        5 -> listOf(path { moveTo(55f, 22f); cubicTo(64f, 30f, 68f, 44f, 64f, 72f) })
            else -> emptyList()
        }
    }

    /** Chấm (x, y, r): mắt, mũi Mã. */
    private val DOTS: Array<List<FloatArray>> = Array(7) { t ->
        when (t) {
        5 -> listOf(floatArrayOf(41f, 29f, 2.9f), floatArrayOf(29f, 40f, 1.7f))
            else -> emptyList()
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    /** Bóng phẳng một màu (hình chìm trang trí, biểu tượng nhỏ). [t] = loại quân 1..6. */
    fun silhouette(cv: Canvas, cx: Float, cy: Float, size: Float, t: Int, color: Int) {
        if (t !in 1..6) return
        val s = size / 100f
        cv.save()
        cv.translate(cx - 50f * s, cy - 47f * s)
        cv.scale(s, s)
        paint.style = Paint.Style.FILL; paint.color = color
        SHAPES[t].forEach { cv.drawPath(it, paint) }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.4f
        SHAPES[t].forEach { cv.drawPath(it, paint) }
        paint.style = Paint.Style.FILL
        cv.restore()
    }

    /** Vẽ quân [p] (dương = Trắng) có tâm (cx, cy) trong ô cạnh [cell]. Chỉ gọi trên luồng UI / một luồng (dùng chung Paint). */
    fun draw(cv: Canvas, cx: Float, cy: Float, cell: Float, p: Int, shadow: Boolean = true) {
        val t = abs(p)
        if (t !in 1..6) return
        val white = p > 0
        val s = cell / 100f
        cv.save()
        cv.translate(cx - 50f * s, cy - 47f * s) // hình thiết kế nằm trong y 5..88 → tâm thị giác ~47
        cv.scale(s, s)
        val shapes = SHAPES[t]
        if (shadow) {
            paint.style = Paint.Style.FILL; paint.color = SHADOW
            cv.save(); cv.translate(1.5f, 3f)
            shapes.forEach { cv.drawPath(it, paint) }
            cv.restore()
        }
        val fill = if (white) WHITE_FILL else BLACK_FILL
        val line = if (white) WHITE_LINE else BLACK_LINE
        val detail = if (white) WHITE_LINE else BLACK_DETAIL
        for (sh in shapes) {
            paint.style = Paint.Style.FILL; paint.color = fill; cv.drawPath(sh, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.4f; paint.color = line; cv.drawPath(sh, paint)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = detail
        DETAILS[t].forEach { cv.drawPath(it, paint) }
        paint.style = Paint.Style.FILL
        DOTS[t].forEach { d -> cv.drawCircle(d[0], d[1], d[2], paint) }
        cv.restore()
    }
}
