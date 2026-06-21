package com.junnz.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun lightScheme() = lightColorScheme(
    primary = JunnzGreen,
    onPrimary = Color.White,
    primaryContainer = JunnzGreenLight,
    onPrimaryContainer = JunnzGreenDark,
    secondary = JunnzCoral,
    onSecondary = Color.White,
    tertiary = JunnzGreen,
    background = LightJunnzColors.background,
    surface = LightJunnzColors.surfaceContainer,
    surfaceVariant = LightJunnzColors.surfaceContainerHigh,
    onBackground = LightJunnzColors.onBackground,
    onSurface = LightJunnzColors.onSurface,
    onSurfaceVariant = LightJunnzColors.onSurfaceVariant,
    outline = LightJunnzColors.outline,
    outlineVariant = LightJunnzColors.outlineVariant,
    error = JunnzCoral,
)

private fun darkScheme() = darkColorScheme(
    primary = JunnzGreenBright,
    onPrimary = Color.White,
    primaryContainer = JunnzGreenDark,
    onPrimaryContainer = JunnzGreenLight,
    secondary = JunnzCoral,
    onSecondary = Color.White,
    tertiary = JunnzGreenBright,
    background = DarkJunnzColors.background,
    surface = DarkJunnzColors.surfaceContainer,
    surfaceVariant = DarkJunnzColors.surfaceContainerHigh,
    onBackground = DarkJunnzColors.onBackground,
    onSurface = DarkJunnzColors.onSurface,
    onSurfaceVariant = DarkJunnzColors.onSurfaceVariant,
    outline = DarkJunnzColors.outline,
    outlineVariant = DarkJunnzColors.outlineVariant,
    error = JunnzCoral,
)

@Composable
fun JunnzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val junnzColors = if (darkTheme) DarkJunnzColors else LightJunnzColors
    CompositionLocalProvider(LocalJunnzColors provides junnzColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme() else lightScheme(),
            typography = JunnzTypography,
            content = content,
        )
    }
}
