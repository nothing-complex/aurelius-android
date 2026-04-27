package com.greyloop.aurelius.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Aurelius Earthy Palette — warm terracotta, parchment, organic
// Primary: Terracotta amber #C4A484 — warmth, earthiness
// Secondary: Warm stone #A69080 — grounding
// Tertiary: Deep sage #4A6358 — calm authority
// Background: Warm cream #FAF8F5 (light), Charcoal #1C1A18 (dark)
// Surface: Parchment #F5F0E8 (light), Deep charcoal #252320 (dark)

private val Terracotta = Color(0xFFC4A484)           // Primary — warm terracotta
private val TerracottaLight = Color(0xFFD9BCA8)      // Primary light
private val TerracottaDark = Color(0xFF9A7B5C)      // Primary dark
private val WarmStone = Color(0xFFA69080)           // Secondary — warm stone
private val DeepSage = Color(0xFF4A6358)             // Tertiary — deep sage
private val WarmCream = Color(0xFFFAF8F5)           // Background light
private val Parchment = Color(0xFFF5F0E8)           // Surface light
private val DeepCharcoal = Color(0xFF1C1A18)       // Background dark
private val DarkSurface = Color(0xFF252320)        // Surface dark

// Concept B Terracotta Scholar — semantic bubble colors
private val UserBubble = Terracotta                    // User message background
private val UserBubbleDark = TerracottaDark            // User bubble dark variant
private val AIBubble = DeepSage                        // AI message background (sage)
private val TerracottaBorder = Color(0xFFB8956E)      // Subtle terracotta left-border accent

// Concept A — The Parchment Scroll
// Single-column centered conversation, aged paper feel, letter-exchange aesthetic
val AgedParchment = Color(0xFFF5EDE0)        // Scroll/aged paper background
val ParchmentInk = Color(0xFF3D2E1C)          // Deep ink for text
val LetterSage = Color(0xFF4A6358)            // AI message sage (same family)
val ScrollBorder = Color(0xFFD9C4A8)          // Subtle aged paper edge

private val EarthyDarkColorScheme = darkColorScheme(
    primary = TerracottaLight,
    secondary = WarmStone,
    tertiary = DeepSage,
    background = DeepCharcoal,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2E2B28),
    onPrimary = Color(0xFF1C1A18),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE8E0D5),
    onSurface = Color(0xFFE8E0D5),
    onSurfaceVariant = Color(0xFFB0A89E),
    primaryContainer = Color(0xFF3D2E1C),
    onPrimaryContainer = Color(0xFFD9BCA8),
    secondaryContainer = Color(0xFF3D3530),
    onSecondaryContainer = Color(0xFFD4C4B8),
    tertiaryContainer = Color(0xFF2A3830),
    onTertiaryContainer = Color(0xFF9DCFAF)
)

private val EarthyLightColorScheme = lightColorScheme(
    primary = Terracotta,
    secondary = WarmStone,
    tertiary = DeepSage,
    background = AgedParchment,      // Parchment Scroll: aged paper background
    surface = Parchment,
    surfaceVariant = ScrollBorder,   // Parchment Scroll: subtle aged edge
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ParchmentInk,     // Parchment Scroll: deep ink text
    onSurface = ParchmentInk,         // Parchment Scroll: deep ink text
    onSurfaceVariant = Color(0xFF5C5347),
    primaryContainer = Terracotta,
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFFF0E6DA),
    onSecondaryContainer = Color(0xFF3D3530),
    tertiaryContainer = Color(0xFFD4E8D8),
    onTertiaryContainer = Color(0xFF1E3328)
)

// Typography is defined in Type.kt — Serif (Libre Baskerville) for display/headlines,
// Sans-serif (Source Sans 3) for body/labels. Using AureliusTypography from Type.kt.

@Composable
fun AureliusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Use stoic palette (not dynamic colors) for brand consistency
    val finalColorScheme = if (darkTheme) EarthyDarkColorScheme else EarthyLightColorScheme
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

/**
 * Subtle noise/grain texture overlay for organic paper-like feel.
 * Applies sparse dots at 1-5% opacity for texture without visible pattern.
 */
private fun Modifier.surfaceGrain(): Modifier = this.drawBehind {
    val random = Random(42) // deterministic seed for consistent grain
    val density = (size.width * size.height / 2000).toInt()
    for (i in 0 until density) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val alpha = random.nextFloat() * 0.04f + 0.01f
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = random.nextFloat() * 1.5f + 0.5f,
            center = Offset(x, y)
        )
    }
}

@Composable
fun AureliusSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.surfaceGrain(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        content()
    }
}
