package com.greyloop.aurelius.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Aurelius Stoic Palette — earthy, refined, authoritative
// Primary: Deep amber/ochre — wisdom, timelessness
// Secondary: Warm stone — stability, grounding
// Tertiary: Deep forest green — depth, calm authority

private val StoicAmber = Color(0xFFC77B2B)           // Primary — warm ochre
private val StoicAmberLight = Color(0xFFE8A654)      // Primary light
private val StoicStone = Color(0xFF8B7355)           // Secondary — warm stone
private val StoicForest = Color(0xFF2D4739)          // Tertiary — deep forest
private val StoicParchment = Color(0xFFF5F0E8)       // Surface light
private val StoicCharcoal = Color(0xFF1A1A1A)       // Background dark

private val StoicDarkColorScheme = darkColorScheme(
    primary = StoicAmberLight,
    secondary = StoicStone,
    tertiary = Color(0xFF4A7C59),                    // Muted forest
    background = StoicCharcoal,
    surface = Color(0xFF242424),
    surfaceVariant = Color(0xFF2E2E2E),
    onPrimary = Color(0xFF1A1A1A),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE8E0D5),
    onSurface = Color(0xFFE8E0D5),
    onSurfaceVariant = Color(0xFFB0A89E),
    primaryContainer = Color(0xFF3D2E1C),
    onPrimaryContainer = Color(0xFFE8C89A),
    tertiaryContainer = Color(0xFF1E3328),
    onTertiaryContainer = Color(0xFF9DCFAF)
)

private val StoicLightColorScheme = lightColorScheme(
    primary = StoicAmber,
    secondary = StoicStone,
    tertiary = Color(0xFF3D5A4A),                    // Deep sage
    background = StoicParchment,
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFF0EBE0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF2C2416),
    onSurface = Color(0xFF2C2416),
    onSurfaceVariant = Color(0xFF5C5347),
    primaryContainer = Color(0xFFFFE8C2),
    onPrimaryContainer = Color(0xFF3D2E1C),
    tertiaryContainer = Color(0xFFD4E8D8),
    onTertiaryContainer = Color(0xFF1E3328)
)

// Typography scale — weighted hierarchy for authority and readability
private val AureliusTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

@Composable
fun AureliusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Use stoic palette (not dynamic colors) for brand consistency
    val finalColorScheme = if (darkTheme) StoicDarkColorScheme else StoicLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = finalColorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = AureliusTypography,
        content = content
    )
}

fun darkThemeFromMode(themeMode: String): Boolean? {
    return when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> null // system default
    }
}
