package com.junnz.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

private val JunnzWearColors = Colors(
    primary = JunnzAmber,
    primaryVariant = JunnzAmberDim,
    secondary = JunnzCoral,
    secondaryVariant = JunnzCoral,
    background = WearBackground,
    surface = WearSurface,
    error = JunnzCoral,
    onPrimary = WearBackground,
    onSecondary = WearBackground,
    onBackground = WearOnSurface,
    onSurface = WearOnSurface,
    onError = WearBackground,
)

@Composable
fun JunnzWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = JunnzWearColors,
        content = content,
    )
}
