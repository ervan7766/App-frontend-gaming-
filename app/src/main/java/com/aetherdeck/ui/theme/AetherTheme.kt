package com.aetherdeck.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherdeck.core.models.ThemePreset
import com.aetherdeck.core.settings.AetherSettingsState
import com.example.R

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

@Immutable
data class AetherDesignTokens(
    val cornerRadius: Dp = 12.dp,
    val cardBorderWidth: Dp = 1.5.dp,
    val focusBorderColor: Color = Color(0xFF00E5FF),
    val focusGlowIntensity: Float = 0.7f,
    val spacingUnit: Dp = 8.dp,
    val reducedMotion: Boolean = false,
    val artworkSaturation: Float = 1.0f,
    val backgroundDim: Float = 0.65f,
    val isLight: Boolean = false
)

val LocalAetherTokens = staticCompositionLocalOf { AetherDesignTokens() }

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val value = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(value or 0xFF000000L)
            8 -> Color(value)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

private fun buildColorScheme(settings: AetherSettingsState): ColorScheme {
    val cbAccent = if (settings.colorBlindSafeFocus) Color(0xFFFFB300) else null
    return when (settings.themePreset) {
        ThemePreset.AETHER_DARK -> darkColorScheme(
            primary = cbAccent ?: Color(0xFF00E5FF),
            onPrimary = Color(0xFF001F24),
            primaryContainer = Color(0xFF00363F),
            onPrimaryContainer = Color(0xFF9CF0FF),
            secondary = Color(0xFF6366F1),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF1E2640),
            onSecondaryContainer = Color(0xFFDCE1FF),
            tertiary = Color(0xFF10B981),
            background = Color(0xFF0A0E17),
            onBackground = Color(0xFFF3F6FA),
            surface = Color(0xFF121826),
            onSurface = Color(0xFFF3F6FA),
            surfaceVariant = Color(0xFF1C2536),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = if (settings.highContrast) Color(0xFF64748B) else Color(0xFF28344A),
            error = Color(0xFFEF4444)
        )
        ThemePreset.OLED_BLACK -> darkColorScheme(
            primary = cbAccent ?: Color(0xFF00F0FF),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF002B33),
            onPrimaryContainer = Color(0xFFB8F8FF),
            secondary = Color(0xFF38BDF8),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF090B10),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF141821),
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = Color(0xFF2E3748),
            error = Color(0xFFFF5252)
        )
        ThemePreset.GRAPHITE -> darkColorScheme(
            primary = cbAccent ?: Color(0xFFE2E8F0),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF334155),
            onPrimaryContainer = Color(0xFFF8FAFC),
            secondary = Color(0xFF38BDF8),
            background = Color(0xFF121417),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF1A1D22),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF242930),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF38414E),
            error = Color(0xFFEF4444)
        )
        ThemePreset.AURORA -> darkColorScheme(
            primary = cbAccent ?: Color(0xFF34D399),
            onPrimary = Color(0xFF022C22),
            primaryContainer = Color(0xFF064E3B),
            onPrimaryContainer = Color(0xFFA7F3D0),
            secondary = Color(0xFFA78BFA),
            background = Color(0xFF0B111E),
            onBackground = Color(0xFFF0FDF4),
            surface = Color(0xFF131C2E),
            onSurface = Color(0xFFF0FDF4),
            surfaceVariant = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF2D3E5A),
            error = Color(0xFFF87171)
        )
        ThemePreset.CLASSIC_CONSOLE -> darkColorScheme(
            primary = cbAccent ?: Color(0xFF3B82F6),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1D4ED8),
            onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = Color(0xFFF59E0B),
            background = Color(0xFF0F141C),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF171F2C),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF222D40),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF334155),
            error = Color(0xFFEF4444)
        )
        ThemePreset.LIGHT -> lightColorScheme(
            primary = cbAccent ?: Color(0xFF0284C7),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0F2FE),
            onPrimaryContainer = Color(0xFF075985),
            secondary = Color(0xFF4F46E5),
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFDC2626)
        )
        ThemePreset.CUSTOM -> {
            val accent = cbAccent ?: parseHexColor(settings.customAccentHex, Color(0xFF00E5FF))
            val bg = parseHexColor(settings.customBackgroundHex, Color(0xFF0A0E17))
            val surf = parseHexColor(settings.customSurfaceHex, Color(0xFF121826))
            darkColorScheme(
                primary = accent,
                onPrimary = Color(0xFF051014),
                primaryContainer = surf,
                onPrimaryContainer = accent,
                secondary = accent,
                background = bg,
                onBackground = Color(0xFFF3F6FA),
                surface = surf,
                onSurface = Color(0xFFF3F6FA),
                surfaceVariant = surf.copy(alpha = 0.85f),
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = accent.copy(alpha = 0.4f),
                error = Color(0xFFEF4444)
            )
        }
    }
}

@Composable
fun AetherDeckTheme(
    settings: AetherSettingsState,
    content: @Composable () -> Unit
) {
    val colorScheme = buildColorScheme(settings)
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density * settings.interfaceScale.factor,
        fontScale = baseDensity.fontScale * settings.textScale
    )

    val borderDp = if (settings.largerFocusBorders) {
        (settings.cardBorderWidthDp + 1.5f).dp
    } else {
        settings.cardBorderWidthDp.dp
    }

    val tokens = AetherDesignTokens(
        cornerRadius = settings.cornerRadiusDp.dp,
        cardBorderWidth = borderDp,
        focusBorderColor = colorScheme.primary,
        focusGlowIntensity = settings.focusGlowIntensity,
        spacingUnit = (8f * settings.densityPreset.spacingMultiplier).dp,
        reducedMotion = settings.reducedMotion,
        artworkSaturation = settings.artworkSaturation,
        backgroundDim = settings.backgroundDim,
        isLight = settings.themePreset == ThemePreset.LIGHT
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        titleLarge = TextStyle(
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        labelLarge = TextStyle(
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.6.sp
        ),
        labelMedium = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        )
    )

    val shapes = Shapes(
        small = RoundedCornerShape((settings.cornerRadiusDp * 0.6f).dp),
        medium = RoundedCornerShape(settings.cornerRadiusDp.dp),
        large = RoundedCornerShape((settings.cornerRadiusDp * 1.3f).dp)
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAetherTokens provides tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
