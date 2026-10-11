package com.xiaohetv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaohetv.data.Store

val IosBlue = Color(0xFF007AFF)
val IosGreen = Color(0xFF34C759)
val IosRed = Color(0xFFFF3B30)
val IosOrange = Color(0xFFFF9500)
val IosGrayBg = Color(0xFFF2F2F7)
val IosGrayCard = Color(0xFFEFEFF4)
val IosSeparator = Color(0xC8C6C6C8)
val IosLabel = Color(0xFF1C1C1E)
val IosSecondary = Color(0xFF8A8A8E)

private fun lightColors(primary: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = primary,
    secondary = IosSecondary,
    onSecondary = Color.White,
    background = IosGrayBg,
    onBackground = IosLabel,
    surface = Color.White,
    onSurface = IosLabel,
    surfaceVariant = IosGrayCard,
    onSurfaceVariant = IosLabel,
    outline = IosSeparator,
    error = IosRed
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** 主题色取自设置：-1 关闭（iOS 蓝）、0 自动、其它为自定义色值。 */
@Composable
fun XiaoheTheme(content: @Composable () -> Unit) {
    val setting = Store.settings.themeColor
    val primary = when {
        setting == -1 || setting == 0 -> IosBlue
        else -> Color(setting)
    }
    MaterialTheme(
        colorScheme = lightColors(primary),
        typography = Typography(),
        shapes = AppShapes,
        content = content
    )
}
