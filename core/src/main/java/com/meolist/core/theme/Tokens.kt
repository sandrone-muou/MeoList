package com.meolist.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One scale for every screen; cards, gaps and page gutters all come from here. */
@Immutable
data class MeoSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screen: Dp = 16.dp,
    val bottomContent: Dp = 96.dp,
)

/** Colours the Material scheme does not carry: priorities, medals and the farm scene. */
@Immutable
data class MeoExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    val gold: Color,
    val silver: Color,
    val bronze: Color,
    val diamond: Color,
    val legend: Color,
    val skyTop: Color,
    val skyBottom: Color,
    val soil: Color,
    val soilDark: Color,
    val leaf: Color,
    val leafDark: Color,
)

val LightExtendedColors = MeoExtendedColors(
    success = Color(0xFF2F7D4A),
    successContainer = Color(0xFFDCEEE3),
    onSuccessContainer = Color(0xFF11380F),
    warning = Color(0xFFB26A00),
    priorityHigh = Color(0xFFD84315),
    priorityMedium = Color(0xFFEE8A00),
    priorityLow = Color(0xFF2C7A9E),
    gold = Color(0xFFD9A521),
    silver = Color(0xFF8E9AA5),
    bronze = Color(0xFFB87333),
    diamond = Color(0xFF2E9CC4),
    legend = Color(0xFF7E57C2),
    skyTop = Color(0xFFDCEFFF),
    skyBottom = Color(0xFFF2F9E8),
    soil = Color(0xFF9C7B5C),
    soilDark = Color(0xFF77573E),
    leaf = Color(0xFF6A9F4B),
    leafDark = Color(0xFF466F31),
)

val DarkExtendedColors = MeoExtendedColors(
    success = Color(0xFF8BD48F),
    successContainer = Color(0xFF1E3D20),
    onSuccessContainer = Color(0xFFC8EFC6),
    warning = Color(0xFFFFC46B),
    priorityHigh = Color(0xFFFF8A65),
    priorityMedium = Color(0xFFFFB74D),
    priorityLow = Color(0xFF6FC3E4),
    gold = Color(0xFFF0C24B),
    silver = Color(0xFFB6C0C9),
    bronze = Color(0xFFD89A63),
    diamond = Color(0xFF7FD5F2),
    legend = Color(0xFFB79CE8),
    skyTop = Color(0xFF15242E),
    skyBottom = Color(0xFF101A13),
    soil = Color(0xFF6B4F3A),
    soilDark = Color(0xFF4A3526),
    leaf = Color(0xFF8BC46A),
    leafDark = Color(0xFF5E8C46),
)

val MeoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val LocalSpacing = staticCompositionLocalOf { MeoSpacing() }
val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Shorthand for the design tokens: `Meo.spacing.lg`, `Meo.colors.gold`. */
object Meo {
    val spacing: MeoSpacing
        @androidx.compose.runtime.Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val layout: MeoLayout
        @androidx.compose.runtime.Composable
        @ReadOnlyComposable
        get() = LocalMeoLayout.current

    val colors: MeoExtendedColors
        @androidx.compose.runtime.Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
