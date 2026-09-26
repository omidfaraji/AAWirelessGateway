package com.nisargjhaveri.aagateway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF00639B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCDE5FF),
        onPrimaryContainer = Color(0xFF001D32),
        secondary = Color(0xFF50606F),
        secondaryContainer = Color(0xFFD3E5F5),
        surface = Color(0xFFF8F9FF),
        surfaceVariant = Color(0xFFDEE3EB),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF96CCFF),
        onPrimary = Color(0xFF003352),
        primaryContainer = Color(0xFF004A76),
        onPrimaryContainer = Color(0xFFCDE5FF),
        secondary = Color(0xFFB7C9D9),
        secondaryContainer = Color(0xFF384956),
        surface = Color(0xFF101418),
        surfaceVariant = Color(0xFF42474E),
    )

@Composable
fun AAGatewayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
