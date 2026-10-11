package com.xiaohetv.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap

/** 参考项目的“皮肤”默认色（青绿），取色失败时用它兜底。 */
val SkinDefault = Color(0xFF2FA98C)

/**
 * 从海报里取一个主题色，生成整页渐变背景。
 * 参考项目（fongmi 手机版的“主题-自动”）就是用当前影片海报取色做背景渐变的。
 */
@Composable
fun rememberSkinColor(pic: String?): Color {
    val context = LocalContext.current
    var color by remember(pic) { mutableStateOf(SkinDefault) }
    LaunchedEffect(pic) {
        if (pic.isNullOrBlank()) return@LaunchedEffect
        val value = runCatching {
            val request = ImageRequest.Builder(context)
                .data(pic)
                .size(32)
                .allowHardware(false)
                .build()
            val bitmap = ((context.imageLoader.execute(request)) as? SuccessResult)?.drawable?.toBitmap()
            bitmap?.let { dominantColor(it) }
        }.getOrNull()
        if (value != null) color = value
    }
    return color
}

/** 主题色 → 自上而下的渐变背景。 */
fun skinBrush(color: Color): Brush = Brush.verticalGradient(
    listOf(
        lerp(color, Color.White, 0.42f),
        color,
        lerp(color, Color.Black, 0.30f)
    )
)

/** 取平均色，跳过接近纯黑/纯白的像素（避免取到黑边或白底）。 */
private fun dominantColor(bitmap: Bitmap): Color? {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    val step = (bitmap.width / 16).coerceAtLeast(1)
    for (x in 0 until bitmap.width step step) {
        for (y in 0 until bitmap.height step step) {
            val pixel = bitmap.getPixel(x, y)
            val pr = (pixel shr 16) and 0xFF
            val pg = (pixel shr 8) and 0xFF
            val pb = pixel and 0xFF
            val max = maxOf(pr, pg, pb)
            val min = minOf(pr, pg, pb)
            if (max < 40 || max - min < 12) continue
            r += pr; g += pg; b += pb; count++
        }
    }
    if (count == 0) return null
    return Color(
        red = (r / count).toInt().coerceIn(0, 255),
        green = (g / count).toInt().coerceIn(0, 255),
        blue = (b / count).toInt().coerceIn(0, 255)
    )
}
