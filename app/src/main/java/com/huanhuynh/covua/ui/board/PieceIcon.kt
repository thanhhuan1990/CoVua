package com.huanhuynh.covua.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * Quân cờ vẽ bằng vector để dùng ngoài bàn cờ (thay ký tự ♚♛♜…, vốn tuỳ font máy).
 * [piece] > 0 = Trắng, < 0 = Đen (1 Vua, 2 Hậu, 3 Xe, 4 Tượng, 5 Mã, 6 Tốt).
 * Có [tint] → vẽ bóng phẳng một màu (hình chìm, biểu tượng).
 */
@Composable
fun PieceIcon(piece: Int, modifier: Modifier, tint: Color? = null) {
    Canvas(modifier) {
        val c = drawContext.canvas.nativeCanvas
        val d = size.minDimension
        if (tint != null) PieceArt.silhouette(c, size.width / 2, size.height / 2, d, kotlin.math.abs(piece), tint.toArgb())
        else PieceArt.draw(c, size.width / 2, size.height / 2, d, piece, shadow = false)
    }
}
