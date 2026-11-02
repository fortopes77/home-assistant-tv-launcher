package com.fortopes.tvlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** The purple-to-cyan accent from the Home Assistant dashboard's TV screen. */
object TvColors {
    val Purple = Color(0xFF8B5CF6)
    val Blue = Color(0xFF3B82F6)
    val Cyan = Color(0xFF06B6D4)
    val Background = Color(0xFF07070B)
    val Card = Color(0xFF1A1B22)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFFA1A1AA)

    // The home screen's background: deep purple into deep blue.
    val BgPurple = Color(0xFF1E1048)
    val BgIndigo = Color(0xFF121547)
    val BgBlue = Color(0xFF0A2150)

    /** The settings flyout. */
    val Panel = Color(0xFF16152B)
}

@Composable
fun TvHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvColors.Purple,
            secondary = TvColors.Blue,
            tertiary = TvColors.Cyan,
            background = TvColors.Background,
            surface = TvColors.Card,
            onSurface = TvColors.Text,
            onBackground = TvColors.Text,
        ),
        content = content,
    )
}
