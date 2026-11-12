package com.huanhuynh.covua.share

import com.huanhuynh.covua.i18n.t

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.huanhuynh.covua.R
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.PieceStyle
import com.huanhuynh.covua.engine.Moves
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Nội dung một lần chia sẻ: [moment] để đo (UTM + analytics). */
data class ShareContent(
    val moment: String,
    val title: String,
    val subtitle: String,
    val text: String,
    val board: IntArray,
    val lastMove: Int = 0,
    val flipped: Boolean = false,
    /** Mũi tên gợi ý trên ảnh (0 = không có). */
    val arrow: Int = 0,
)

/**
 * Ảnh chia sẻ 1080×1540: đầu (tiêu đề) + bàn cờ vẽ lại bằng Canvas (không phụ thuộc màn hình đang hiện)
 * + chân (icon, tên app, link tải). Link nằm luôn trong ảnh vì Facebook bỏ qua chữ soạn sẵn.
 */
object ShareImage {
    private const val W = 1080
    private const val HEAD = 250
    private const val FOOT = 210

    fun playUrl(context: Context, moment: String) =
        "https://play.google.com/store/apps/details?id=${context.packageName}&referrer=utm_source%3Dshare%26utm_medium%3D$moment"

    private fun shortUrl(context: Context) = "play.google.com/store/apps/details?id=${context.packageName}"

    fun render(context: Context, c: ShareContent, style: PieceStyle): Bitmap {
        val boardH = W
        val h = HEAD + boardH + FOOT
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Đầu: nền xanh đậm
        p.shader = LinearGradient(0f, 0f, W.toFloat(), HEAD.toFloat(), 0xFF3B6AA8.toInt(), 0xFF15253F.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, W.toFloat(), (HEAD + 40).toFloat(), p)
        p.shader = null
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 62f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6D58E.toInt(); textSize = 40f }
        fit(title, c.title, W - 100f); fit(sub, c.subtitle, W - 100f)
        cv.drawText(c.title, 50f, 118f, title)
        cv.drawText(c.subtitle, 50f, 186f, sub)

        // Bàn cờ
        cv.save()
        cv.translate(0f, HEAD.toFloat())
        drawBoard(cv, W.toFloat(), boardH.toFloat(), c, style)
        cv.restore()

        // Chân
        val top = (HEAD + boardH).toFloat()
        p.color = 0xFF2B2420.toInt()
        cv.drawRect(0f, top, W.toFloat(), h.toFloat(), p)
        val iconSize = FOOT * 0.62f
        val ix = 50f
        val iy = top + (FOOT - iconSize) / 2
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.let { d ->
            d.setBounds(ix.toInt(), iy.toInt(), (ix + iconSize).toInt(), (iy + iconSize).toInt()); d.draw(cv)
        }
        val tx = ix + iconSize + 36f
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 50f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6D58E.toInt(); textSize = 34f }
        val link = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt(); textSize = 28f }
        val maxW = W - tx - 40f
        val linkText = t("Tải miễn phí: ", "Free download: ", "免费下载：", "無料ダウンロード：") + shortUrl(context)
        fit(link, linkText, maxW)
        val tagline = t("Chơi cờ vua với máy – thầy chấm từng nước", "Play chess vs the computer – every move coached", "人机下国际象棋——老师逐步点评", "コンピュータとチェス – 一手ごとに先生が解説")
        cv.drawText(t("Cờ Vua Có Thầy", "Chess Coach", "国际象棋老师", "チェス先生"), tx, top + FOOT * 0.38f, name)
        cv.drawText(tagline, tx, top + FOOT * 0.62f, tag.also { fit(it, tagline, maxW) })
        cv.drawText(linkText, tx, top + FOOT * 0.84f, link)
        return bmp
    }

    private fun fit(p: Paint, text: String, maxW: Float) {
        while (p.measureText(text) > maxW && p.textSize > 14f) p.textSize *= 0.95f
    }

    private fun drawBoard(cv: Canvas, w: Float, h: Float, c: ShareContent, pieceStyle: PieceStyle) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val frame = w * 0.02f
        val cell = (minOf(w, h) - 2 * frame) / 8f
        val left = (w - cell * 8) / 2f
        val top = (h - cell * 8) / 2f
        p.color = 0xFF6B3E1E.toInt()
        cv.drawRect(0f, 0f, w, h, p)
        fun tl(sq: Int): Pair<Float, Float> {
            val col = sq and 7; val row = sq shr 3
            val (vc, vr) = if (c.flipped) (7 - col) to row else col to (7 - row)
            return (left + vc * cell) to (top + vr * cell)
        }
        for (sq in 0 until 64) {
            val (x, y) = tl(sq)
            p.color = if (((sq shr 3) + (sq and 7)) % 2 == 1) 0xFFF0D9B5.toInt() else 0xFFB58863.toInt()
            cv.drawRect(x, y, x + cell, y + cell, p)
        }
        if (c.lastMove != 0) {
            p.color = 0x66F2D33B
            for (sq in intArrayOf(Moves.from(c.lastMove), Moves.to(c.lastMove))) { val (x, y) = tl(sq); cv.drawRect(x, y, x + cell, y + cell, p) }
        }
        fun ctr(sq: Int): Pair<Float, Float> { val (x, y) = tl(sq); return (x + cell / 2) to (y + cell / 2) }
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        for (sq in 0 until 64) {
            val v = c.board.getOrElse(sq) { 0 }
            if (v == 0) continue
            val (cx, cy) = ctr(sq)
            drawPiece(cv, cx, cy, cell, v, pieceStyle, glyph, letter)
        }
        if (c.arrow != 0) {
            val (ax, ay) = ctr(Moves.from(c.arrow)); val (bx, by) = ctr(Moves.to(c.arrow))
            val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD91565C0.toInt(); strokeWidth = cell * 0.14f; strokeCap = Paint.Cap.ROUND }
            val ang = atan2(by - ay, bx - ax)
            val head = cell * 0.38f
            cv.drawLine(ax, ay, bx - cos(ang) * head * 0.8f, by - sin(ang) * head * 0.8f, ap)
            val path = Path().apply {
                moveTo(bx, by)
                lineTo(bx - head * cos(ang) + head * 0.62f * sin(ang), by - head * sin(ang) - head * 0.62f * cos(ang))
                lineTo(bx - head * cos(ang) - head * 0.62f * sin(ang), by - head * sin(ang) + head * 0.62f * cos(ang))
                close()
            }
            cv.drawPath(path, ap.apply { style = Paint.Style.FILL })
        }
    }

    private val SOLID = arrayOf("", "♚", "♛", "♜", "♝", "♞", "♟\uFE0E")
    private val OUTLINE = arrayOf("", "♔", "♕", "♖", "♗", "♘", "♙")
    private val LETTER_VI = arrayOf("", "Vua", "Hậu", "Xe", "Tượng", "Mã", "Tốt")
    private val LETTER_ZH = arrayOf("", "王", "后", "车", "象", "马", "兵")
    private val LETTER_INTL = arrayOf("", "K", "Q", "R", "B", "N", "P")

    private fun drawPiece(cv: Canvas, x: Float, y: Float, cell: Float, v: Int, style: PieceStyle, glyph: Paint, letter: Paint) {
        val white = v > 0
        val t = abs(v)
        if (style == PieceStyle.CLASSIC) {
            glyph.textSize = cell * 0.86f
            val fm = glyph.fontMetrics
            val by = y - (fm.ascent + fm.descent) / 2f
            glyph.style = Paint.Style.FILL
            glyph.color = 0x40000000; cv.drawText(SOLID[t], x + cell * 0.02f, by + cell * 0.035f, glyph)
            if (white) {
                glyph.color = 0xFFFBF7EE.toInt(); cv.drawText(SOLID[t], x, by, glyph)
                glyph.color = 0xFF202020.toInt(); cv.drawText(OUTLINE[t], x, by, glyph)
            } else {
                glyph.style = Paint.Style.STROKE; glyph.strokeWidth = cell * 0.035f; glyph.color = 0x99FFFFFF.toInt()
                cv.drawText(SOLID[t], x, by, glyph)
                glyph.style = Paint.Style.FILL; glyph.color = 0xFF1C1C1C.toInt()
                cv.drawText(SOLID[t], x, by, glyph)
            }
            glyph.style = Paint.Style.FILL
        } else {
            val r = cell * 0.43f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = 0x55000000; cv.drawCircle(x, y + r * 0.12f, r, p)
            p.color = if (white) 0xFFFBF7EE.toInt() else 0xFF1C1C1C.toInt(); cv.drawCircle(x, y, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.07f
            p.color = if (white) 0xFF3A3A3A.toInt() else 0xFFE8E2D4.toInt(); cv.drawCircle(x, y, r * 0.86f, p)
            val s = when (com.huanhuynh.covua.i18n.I18n.lang) {
                com.huanhuynh.covua.i18n.Lang.VI -> LETTER_VI[t]; com.huanhuynh.covua.i18n.Lang.ZH -> LETTER_ZH[t]; else -> LETTER_INTL[t]
            }
            letter.color = if (white) 0xFF1C1C1C.toInt() else 0xFFF5F0E6.toInt()
            letter.textSize = when { s.length <= 1 -> r * 1.05f; s.length <= 3 -> r * 0.62f; else -> r * 0.48f }
            val fm = letter.fontMetrics
            cv.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, letter)
        }
    }
}

object Sharer {
    /** Mở bảng chia sẻ của hệ thống với ảnh + chữ (Zalo, Messenger nhận chữ; Facebook chỉ lấy ảnh). */
    fun share(context: Context, content: ShareContent, style: PieceStyle) {
        runCatching {
            val bmp = ShareImage.render(context, content, style)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "covua.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val text = content.text + "\n" + ShareImage.playUrl(context, content.moment)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, t("Cờ Vua Có Thầy", "Chess Coach", "国际象棋老师", "チェス先生"))
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, t("Chia sẻ", "Share", "分享", "共有")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            FirebaseHelper.logEvent(context, "share", mapOf("moment" to content.moment))
        }
    }

    /** Chỉ gửi chữ + link (giới thiệu app). */
    fun shareText(context: Context, text: String, moment: String) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text + "\n" + ShareImage.playUrl(context, moment))
            }
            context.startActivity(Intent.createChooser(send, t("Giới thiệu cho bạn bè", "Tell your friends", "推荐给好友", "友達に紹介")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            FirebaseHelper.logEvent(context, "share", mapOf("moment" to moment))
        }
    }
}
