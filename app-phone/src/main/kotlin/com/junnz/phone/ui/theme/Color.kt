package com.junnz.phone.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand — Junnz green (constant across light/dark) ───────────────────────────
val JunnzGreen = Color(0xFF1A8C5A)
val JunnzGreenBright = Color(0xFF1FA265)
val JunnzGreenDark = Color(0xFF0F5C3A)
val JunnzGreenLight = Color(0xFFE6F3EC)

val JunnzAmber = JunnzGreen          // primary accent (legacy name → brand green)
val JunnzAmberLight = JunnzGreenLight
val JunnzAmberDark = JunnzGreenDark
val JunnzCoral = Color(0xFFE8604C)
val JunnzBlue = Color(0xFF2F6BD6)

val StatusOnline = Color(0xFF22C55E)

// Neutral fallback chip (used in non-composable helpers; constant)
val ChipNeutralBg = Color(0xFFE9EAE6)
val ChipNeutralFg = Color(0xFF6B6F6B)

// Category / context badge tokens — soft pastel tints (constant)
val BadgeRedBg = Color(0xFFFCE9E6)
val BadgeRedFg = Color(0xFFC23B28)
val BadgeGreenBg = Color(0xFFE6F3EC)
val BadgeGreenFg = Color(0xFF1A7048)
val BadgeBlueBg = Color(0xFFE7EFFB)
val BadgeBlueFg = Color(0xFF2F6BD6)
val BadgeAmberBg = Color(0xFFE6F3EC)
val BadgeAmberFg = Color(0xFF1A7048)
val BadgePurpleBg = Color(0xFFEFEAFB)
val BadgePurpleFg = Color(0xFF6B3FD1)

// App brand badge colors (constant)
val AppBlinkitBg = Color(0xFFFFF1C2)
val AppBlinkitFg = Color(0xFF8A6A00)
val AppZeptoBg = Color(0xFFEDE7FC)
val AppZeptoFg = Color(0xFF5B2FC9)

// ── Theme-aware structural palette ─────────────────────────────────────────────

/** Surface / text / outline tokens that flip between light and dark. */
data class JunnzColors(
    val background: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val isDark: Boolean,
)

val LightJunnzColors = JunnzColors(
    background = Color(0xFFFAFAF8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F2EF),
    surfaceContainerHighest = Color(0xFFE9EAE6),
    onBackground = Color(0xFF1B1C1A),
    onSurface = Color(0xFF1B1C1A),
    onSurfaceVariant = Color(0xFF6B6F6B),
    outline = Color(0xFFE2E4E0),
    outlineVariant = Color(0xFFEEF0EC),
    isDark = false,
)

val DarkJunnzColors = JunnzColors(
    background = Color(0xFF131418),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF272A30),
    surfaceContainerHighest = Color(0xFF32353C),
    onBackground = Color(0xFFF1F1EF),
    onSurface = Color(0xFFF1F1EF),
    onSurfaceVariant = Color(0xFFA1A5AB),
    outline = Color(0xFF34373E),
    outlineVariant = Color(0xFF2A2D33),
    isDark = true,
)

val LocalJunnzColors = staticCompositionLocalOf { LightJunnzColors }

// Accessor properties keep existing call sites (`Background`, `OnSurface`, …)
// working unchanged — they now read the active theme's palette.
val Background: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.background
val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.surfaceContainer
val SurfaceContainerHigh: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.surfaceContainerHigh
val SurfaceContainerHighest: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.surfaceContainerHighest
val OnBackground: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.onBackground
val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.onSurface
val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.onSurfaceVariant
val Outline: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.outline
val OutlineVariant: Color @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.outlineVariant
val JunnzIsDark: Boolean @Composable @ReadOnlyComposable get() = LocalJunnzColors.current.isDark
