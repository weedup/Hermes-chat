package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

private val HermesDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = NavyDeep,
    primaryContainer = GoldContainer,
    onPrimaryContainer = GoldOnContainer,
    secondary = GoldAccent,
    onSecondary = NavyDeep,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = GoldOnContainer,
    tertiary = GoldSecondary,
    onTertiary = NavyDeep,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder,
    outlineVariant = NavySurfaceVariant
)

private val HermesLightColorScheme = darkColorScheme(
    // Defaulting to sophisticated dark theme as requested for Galaxy S26 Ultra Olympic Gold aesthetic
    primary = GoldPrimary,
    onPrimary = NavyDeep,
    primaryContainer = GoldContainer,
    onPrimaryContainer = GoldOnContainer,
    secondary = GoldAccent,
    onSecondary = NavyDeep,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder
)

@Composable
fun HermesChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiDensityScale: Float = 0.90f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HermesDarkColorScheme else HermesDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity.density, currentDensity.fontScale, uiDensityScale) {
        Density(
            density = currentDensity.density * uiDensityScale,
            fontScale = currentDensity.fontScale * uiDensityScale
        )
    }

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiDensityScale: Float = 0.90f,
    content: @Composable () -> Unit
) {
    HermesChatTheme(darkTheme = darkTheme, uiDensityScale = uiDensityScale, content = content)
}

