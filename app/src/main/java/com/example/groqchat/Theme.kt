package com.example.groqchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Dark blue accent palette, clean neutral surfaces — similar spirit to a
// minimal chat-app look (soft background, rounded bubbles, quiet accent
// color) without copying any specific product's exact branded design.
private val DeepBlue = Color(0xFF1B4B91)
private val DeepBlueDark = Color(0xFF0E2E5C)
private val SoftBackground = Color(0xFFF7F8FA)
private val SoftSurface = Color(0xFFFFFFFF)
private val NeutralText = Color(0xFF1C1C1E)

private val LoneAILightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    secondary = DeepBlueDark,
    onSecondary = Color.White,
    background = SoftBackground,
    onBackground = NeutralText,
    surface = SoftSurface,
    onSurface = NeutralText,
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF4A4A4E),
)

private val LoneAIDarkColors = darkColorScheme(
    primary = Color(0xFF6C9BE0),
    onPrimary = Color(0xFF00204D),
    secondary = Color(0xFF9FC1EE),
    onSecondary = Color(0xFF00204D),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E4E6),
    surface = Color(0xFF1B1C1F),
    onSurface = Color(0xFFE4E4E6),
    surfaceVariant = Color(0xFF2A2B2E),
    onSurfaceVariant = Color(0xFFC5C6C9),
)

// Force a clean default sans-serif regardless of any custom system-wide
// font the device has set, so the app always looks the same.
private val LoneAITypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun LoneAITheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) LoneAIDarkColors else LoneAILightColors
    MaterialTheme(
        colorScheme = colors,
        typography = LoneAITypography,
        content = content
    )
}
