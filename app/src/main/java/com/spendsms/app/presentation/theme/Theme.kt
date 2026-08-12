package com.spendsms.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpendSmsLightColors = lightColorScheme(
    primary = Color(0xFF0B3D2E),
    onPrimary = Color(0xFFE8F5E9),
    secondary = Color(0xFF2E7D32),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF10231C),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF10231C),
)

@Composable
fun SpendSmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpendSmsLightColors,
        content = content,
    )
}
