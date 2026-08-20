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

// Dark blue accent palette, clean neutral surfaces. EVERY color role below
// is set explicitly — Compose's lightColorScheme()/darkColorScheme() quietly
// fill in any role you don't specify with Material3's own default purple
// baseline, which is why things like the nav bar's selected-tab pill
// (secondaryContainer) kept showing purple even after primary was changed.
private val DeepBlue = Color(0xFF1B4B91)
private val DeepBlueLight = Color(0xFF3E6BAE)
private val DeepBlueContainer = Color(0xFFD6E3F7)
private val OnDeepBlueContainer = Color(0xFF0A2A54)
private val SoftBackground = Color(0xFFF7F8FA)
private val SoftSurface = Color(0xFFFFFFFF)
private val NeutralText = Color(0xFF1C1C1E)
private val NeutralOutline = Color(0xFFB8BCC2)

private val LoneAILightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = DeepBlueContainer,
    onPrimaryContainer = OnDeepBlueContainer,
    secondary = DeepBlueLight,
    onSecondary = Color.White,
    secondaryContainer = DeepBlueContainer,
    onSecondaryContainer = OnDeepBlueContainer,
    tertiary = DeepBlueLight,
    onTertiary = Color.White,
    tertiaryContainer = DeepBlueContainer,
    onTertiaryContainer = OnDeepBlueContainer,
    background = SoftBackground,
    onBackground = NeutralText,
    surface = SoftSurface,
    onSurface = NeutralText,
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF4A4A4E),
    outline = NeutralOutline,
    outlineVariant = Color(0xFFDCDFE4),
    inverseSurface = Color(0xFF2E3033),
    inverseOnSurface = Color(0xFFF1F2F4),
    inversePrimary = Color(0xFFA9C6F0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val LoneAIDarkColors = darkColorScheme(
    primary = Color(0xFF6C9BE0),
    onPrimary = Color(0xFF00204D),
    primaryContainer = Color(0xFF16407C),
    onPrimaryContainer = Color(0xFFD6E3F7),
    secondary = Color(0xFF9FC1EE),
    onSecondary = Color(0xFF00204D),
    secondaryContainer = Color(0xFF16407C),
    onSecondaryContainer = Color(0xFFD6E3F7),
    tertiary = Color(0xFF9FC1EE),
    onTertiary = Color(0xFF00204D),
    tertiaryContainer = Color(0xFF16407C),
    onTertiaryContainer = Color(0xFFD6E3F7),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E4E6),
    surface = Color(0xFF1B1C1F),
    onSurface = Color(0xFFE4E4E6),
    surfaceVariant = Color(0xFF2A2B2E),
    onSurfaceVariant = Color(0xFFC5C6C9),
    outline = Color(0xFF8C9099),
    outlineVariant = Color(0xFF44474A),
    inverseSurface = Color(0xFFE4E4E6),
    inverseOnSurface = Color(0xFF1B1C1F),
    inversePrimary = DeepBlue,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Requests the generic "sans-serif" family. NOTE: if your device has a
// system-wide custom font applied (via a font-changer app or OEM display
// setting), Android may substitute that font for ANY app requesting
// "sans-serif" — that substitution happens below the app level and can't
// be overridden from here. The only way to truly force a specific look
// regardless of that setting is to bundle an actual embedded font file as
// an app resource (not just request a generic family name).
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
