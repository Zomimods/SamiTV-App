package com.sami.livetv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Exact design tokens of the V20 website (:root and [data-theme='light']). */
data class SamiPalette(
    val bg: Color,
    val bgElev: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accent2: Color,
    val accentSoft: Color,
    val accentLine: Color,
    val live: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val star: Color,
    val onAccent: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val DarkPalette = SamiPalette(
    bg = Color(0xFF071016),
    bgElev = Color(0xFF0B171F),
    surface = Color(0xFF0D1B24),
    surface2 = Color(0xFF11232D),
    surface3 = Color(0xFF17303B),
    line = Color(0xFF1B333E),
    lineStrong = Color(0xFF294550),
    text = Color(0xFFF2F7F8),
    text2 = Color(0xFFB8C7CC),
    text3 = Color(0xFF7F949C),
    accent = Color(0xFF23C9A8),
    accent2 = Color(0xFF67E2C9),
    accentSoft = Color(0x1C23C9A8),
    accentLine = Color(0x7023C9A8),
    live = Color(0xFFFF4D68),
    good = Color(0xFF33D18A),
    warn = Color(0xFFF0B84F),
    bad = Color(0xFFFF6F78),
    star = Color(0xFFFFC857),
    onAccent = Color(0xFF062019),
    scrim = Color(0xB801070A),
    isDark = true,
)

val LightPalette = SamiPalette(
    bg = Color(0xFFF3F7F8),
    bgElev = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0F6F7),
    surface3 = Color(0xFFE6F0F2),
    line = Color(0xFFDBE7E9),
    lineStrong = Color(0xFFC2D4D8),
    text = Color(0xFF10222A),
    text2 = Color(0xFF51676E),
    text3 = Color(0xFF768B92),
    accent = Color(0xFF087F70),
    accent2 = Color(0xFF0F9F8A),
    accentSoft = Color(0x1A087F70),
    accentLine = Color(0x5C087F70),
    live = Color(0xFFD91F45),
    good = Color(0xFF087F55),
    warn = Color(0xFFA96B06),
    bad = Color(0xFFC83A45),
    star = Color(0xFFB77700),
    onAccent = Color(0xFFFFFFFF),
    scrim = Color(0x850D1E25),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
