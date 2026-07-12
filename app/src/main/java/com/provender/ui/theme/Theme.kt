package com.provender.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Green90,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Cream40,
    onSecondary = Cream90,
    secondaryContainer = Cream90,
    onSecondaryContainer = Cream10,
    tertiary = Tomato40,
    onTertiaryContainer = Tomato80,
    surface = NeutralSurfaceLight,
    background = NeutralSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green20,
    onPrimaryContainer = Green90,
    secondary = Cream80,
    onSecondary = Cream10,
    secondaryContainer = Cream10,
    onSecondaryContainer = Cream90,
    tertiary = Tomato80,
    surface = NeutralSurfaceDark,
    background = NeutralSurfaceDark,
)

@Composable
fun ProvenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ProvenderTypography,
        content = content,
    )
}
