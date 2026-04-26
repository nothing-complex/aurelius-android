package com.greyloop.aurelius.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

// Google Fonts provider — authority for downloadable fonts
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.greyloop.aurelius.R.array.com_google_android_gms_fonts_certs
)

// Google Fonts for elegant serif + readable sans-serif hierarchy
private val googleFont = GoogleFont("Libre Baskerville")

private val serifFontFamily = FontFamily(
    Font(googleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(googleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold, style = FontStyle.Normal)
)

// Source Sans 3 - highly readable sans-serif for body
private val sourceSansFont = GoogleFont("Source Sans 3")

private val sansSerifFontFamily = FontFamily(
    Font(sourceSansFont, fontProvider = googleFontProvider, weight = FontWeight.Light, style = FontStyle.Normal),
    Font(sourceSansFont, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(sourceSansFont, fontProvider = googleFontProvider, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(sourceSansFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(sourceSansFont, fontProvider = googleFontProvider, weight = FontWeight.Bold, style = FontStyle.Normal)
)

// Aurelius Typography — Serif for display/headers, Sans for body
// Elegant hierarchy for stoic authority
val AureliusTypography = Typography(
    // Display — Serif Bold (authority, gravitas)
    displayLarge = TextStyle(
        fontFamily = serifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = serifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = serifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    // Headlines — Serif SemiBold (refined, readable)
    headlineLarge = TextStyle(
        fontFamily = serifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = serifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = serifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // Titles — Sans-serif Medium (clean, modern)
    titleLarge = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // Body — Sans-serif Normal (readable, accessible)
    bodyLarge = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // Labels — Sans-serif Medium (clear, functional)
    labelLarge = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = sansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
