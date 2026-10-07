package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DeepTealNavyLight,
    onPrimary = KenyanWhite,
    primaryContainer = DeepTealNavyDark,
    secondary = TealAccent,
    tertiary = SuccessPaidGreen,
    error = AlertSosRed,
    background = DeepTealNavyDark,
    surface = DeepTealNavy,
    onSurface = KenyanWhite
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTealNavy,
    onPrimary = KenyanWhite,
    primaryContainer = DeepTealNavyLight,
    secondary = TealAccent,
    tertiary = SuccessPaidGreen,
    error = AlertSosRed,
    background = SurfaceSoft,
    surface = CardBackgroundWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve bespoke eBoma teal/navy visual identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
