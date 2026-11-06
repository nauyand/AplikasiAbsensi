package com.teknosolvo.personalhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teknosolvo.personalhub.data.Accent
import com.teknosolvo.personalhub.data.ThemeMode

// Charcoal palette
val Ink = Color(0xFF0B0B10)
val Charcoal = Color(0xFF121218)
val Graphite = Color(0xFF1A1A22)
val Slate = Color(0xFF23232D)
val Steel = Color(0xFF30303C)
val Mist = Color(0xFF9B9BB0)
val Snow = Color(0xFFEDEDF5)

val Violet = Color(0xFF8B7CFF)
val VioletDeep = Color(0xFF5B45E0)
val Electric = Color(0xFF3DA9FC)
val ElectricDeep = Color(0xFF1C6FE8)
val Cyan = Color(0xFF22D3EE)

val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)

@Immutable
data class HubExtras(
    val heroGradient: Brush,
    val accentGradient: Brush,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val muted: Color,
    val cardBorder: Color,
    val isDark: Boolean,
)

val LocalHubExtras = staticCompositionLocalOf {
    HubExtras(Brush.linearGradient(listOf(VioletDeep, ElectricDeep)), Brush.linearGradient(listOf(Violet, Electric)),
        Success, Warning, Danger, Mist, Steel, true)
}

object Hub {
    val extras: HubExtras @Composable get() = LocalHubExtras.current
}

private fun darkScheme(primary: Color, secondary: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF0B0B10),
    primaryContainer = primary.copy(alpha = 0.18f),
    onPrimaryContainer = Snow,
    secondary = secondary,
    onSecondary = Color(0xFF0B0B10),
    secondaryContainer = secondary.copy(alpha = 0.16f),
    onSecondaryContainer = Snow,
    tertiary = Cyan,
    background = Ink,
    onBackground = Snow,
    surface = Ink,
    onSurface = Snow,
    surfaceVariant = Slate,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Charcoal,
    surfaceContainer = Graphite,
    surfaceContainerHigh = Slate,
    surfaceContainerHighest = Steel,
    outline = Steel,
    outlineVariant = Color(0xFF262630),
    error = Danger,
    onError = Color(0xFF0B0B10),
)

private fun lightScheme(primary: Color, secondary: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primary.copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF1A1030),
    secondary = secondary,
    secondaryContainer = secondary.copy(alpha = 0.14f),
    background = Color(0xFFF6F6FA),
    surface = Color(0xFFF6F6FA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEFEFF5),
    surfaceContainerHigh = Color(0xFFE7E7EF),
    surfaceContainerHighest = Color(0xFFDDDDE8),
    onSurfaceVariant = Color(0xFF5A5A6E),
    outline = Color(0xFFD0D0DC),
    outlineVariant = Color(0xFFE2E2EA),
    error = Color(0xFFDC2626),
)

private val HubTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    )
}

private val HubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun HubTheme(themeMode: String = ThemeMode.DARK, accent: String = Accent.VIOLET, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    val (primary, secondary) = if (accent == Accent.BLUE) Electric to Violet else Violet to Electric
    val scheme = if (dark) darkScheme(primary, secondary) else lightScheme(
        if (accent == Accent.BLUE) ElectricDeep else VioletDeep, if (accent == Accent.BLUE) VioletDeep else ElectricDeep,
    )
    val extras = HubExtras(
        heroGradient = if (dark) Brush.linearGradient(listOf(Color(0xFF2A1F6B), Color(0xFF12305E)))
        else Brush.linearGradient(listOf(Color(0xFF6D5BF0), Color(0xFF2F86F0))),
        accentGradient = Brush.linearGradient(listOf(primary, secondary)),
        success = if (dark) Success else Color(0xFF059669),
        warning = if (dark) Warning else Color(0xFFD97706),
        danger = if (dark) Danger else Color(0xFFDC2626),
        muted = scheme.onSurfaceVariant,
        cardBorder = scheme.outlineVariant,
        isDark = dark,
    )
    CompositionLocalProvider(LocalHubExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = HubTypography, shapes = HubShapes) {
            // Screens drawn outside a Surface (dashboard, lock screen) must not fall back to black text.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
