/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same key colour as the Android app's DefaultThemeColor. */
val ExhaleAccent = Color(0xFFED5564)

private val ExhaleDark = darkColorScheme(
    primary = ExhaleAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1A22),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFE7C08A),
    tertiary = Color(0xFFB6A4E4),
    background = Color(0xFF0B0909),
    onBackground = Color(0xFFECE0E1),
    surface = Color(0xFF0B0909),
    onSurface = Color(0xFFF2E9EA),
    surfaceVariant = Color(0xFF2A2224),
    onSurfaceVariant = Color(0xFFB9AAAC),
    surfaceContainer = Color(0xFF1A1516),
    surfaceContainerHigh = Color(0xFF231D1E),
    outline = Color(0xFF514446),
    outlineVariant = Color(0x33FFFFFF),
)

/** The Android app's font. */
private val Linotte: FontFamily by lazy {
    val bytes = object {}.javaClass.getResourceAsStream("/font/linotte.ttf")!!.use { it.readAllBytes() }
    FontFamily(Font(identity = "linotte", data = bytes))
}

/**
 * The Android app's type scale: Material roles, but with the iOS large-title convention on the
 * display/headline/title band — bold, tracked tighter as the size grows.
 */
private fun exhaleTypography(font: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = font)
    return Typography(
        displayLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.0).sp),
        displayMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.8).sp),
        displaySmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp),
        headlineLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
    )
}

private val ExhaleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ExhaleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExhaleDark,
        typography = exhaleTypography(Linotte),
        shapes = ExhaleShapes,
        content = content,
    )
}
