package com.meolist.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6382),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EBF3),
    onPrimaryContainer = Color(0xFF182533),
    secondary = Color(0xFF5D6672),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EBEE),
    onSecondaryContainer = Color(0xFF1A1E28),
    tertiary = Color(0xFF7A5C3E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E8D9),
    onTertiaryContainer = Color(0xFF2F1B08),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1B1F24),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFE6E9EF),
    onSurfaceVariant = Color(0xFF626976),
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFFDEE2E9),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFE9EDF3),
    surfaceContainerHigh = Color(0xFFE2E7EE),
    surfaceContainerHighest = Color(0xFFD9E0E9),
    inverseSurface = Color(0xFF2C3038),
    inverseOnSurface = Color(0xFFF1F3F7),
    inversePrimary = Color(0xFFA9BDD4),
    surfaceDim = Color(0xFFDDE1E7),
    surfaceBright = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9BDD4),
    onPrimary = Color(0xFF152430),
    primaryContainer = Color(0xFF2A3B4E),
    onPrimaryContainer = Color(0xFFDCE7F2),
    secondary = Color(0xFFBFC6D6),
    onSecondary = Color(0xFF252B37),
    secondaryContainer = Color(0xFF333A48),
    onSecondaryContainer = Color(0xFFE2E6EF),
    tertiary = Color(0xFFE8C08C),
    onTertiary = Color(0xFF3A2408),
    tertiaryContainer = Color(0xFF4E3315),
    onTertiaryContainer = Color(0xFFF7E3C8),
    background = Color(0xFF101114),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF3A404B),
    onSurfaceVariant = Color(0xFFC3C9D4),
    outline = Color(0xFF8D95A3),
    outlineVariant = Color(0xFF3A404B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0B0C0F),
    surfaceContainerLow = Color(0xFF17191E),
    surfaceContainer = Color(0xFF1C1F25),
    surfaceContainerHigh = Color(0xFF262A32),
    surfaceContainerHighest = Color(0xFF313640),
    inverseSurface = Color(0xFFE7E9EE),
    inverseOnSurface = Color(0xFF2C3038),
    inversePrimary = Color(0xFF4A6382),
    surfaceDim = Color(0xFF101114),
    surfaceBright = Color(0xFF363B45),
)

@Composable
fun MeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
    val configuration = LocalConfiguration.current
    val layout = remember(configuration.screenWidthDp) {
        MeoLayout.forWidth(configuration.screenWidthDp)
    }
    CompositionLocalProvider(
        LocalSpacing provides MeoSpacing(),
        LocalMeoLayout provides layout,
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeoTypography,
            shapes = MeoShapes,
            content = content,
        )
    }
}
