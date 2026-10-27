package com.bladeboard.launcher.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.bladeboard.launcher.data.LauncherSettings

/** Glassy dark panel color shared by every blade body. */
val BladeSurface = Color(0xE6101419)

@Composable
fun BladeboardTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val accent = settings.accentFor(0)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.Black,
        secondary = settings.accentFor(1),
        surface = Color(0xFF15191F),
        onSurface = Color.White,
        background = Color.Transparent,
        onBackground = Color.White,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
