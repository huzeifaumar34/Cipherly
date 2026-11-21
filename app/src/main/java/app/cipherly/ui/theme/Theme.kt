package app.cipherly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CipherlyBlue,
    onPrimary = CipherlyInk,
    primaryContainer = CipherlyBlueDark,
    onPrimaryContainer = CipherlyText,
    secondary = CipherlyMuted,
    background = CipherlyInk,
    onBackground = CipherlyText,
    surface = CipherlySurface,
    onSurface = CipherlyText,
)

private val LightColors = lightColorScheme(
    primary = CipherlyBlueDark,
    onPrimary = CipherlyLightSurface,
    primaryContainer = CipherlyBlue,
    onPrimaryContainer = CipherlyInk,
    secondary = CipherlyBlueDark,
    background = CipherlyLightBackground,
    onBackground = CipherlyLightText,
    surface = CipherlyLightSurface,
    onSurface = CipherlyLightText,
)

@Composable
fun CipherlyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CipherlyTypography,
        content = content,
    )
}
