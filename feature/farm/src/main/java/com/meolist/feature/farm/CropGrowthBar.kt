package com.meolist.feature.farm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meolist.core.theme.Meo

/**
 * Farm-specific growth meter: a leaf-to-primary gradient fill, a soft marker at the growing tip and
 * faint ticks at the stage boundaries (seed, sprout, growing, budding, almost ripe). The numbers live
 * outside the bar so it stays readable at small sizes.
 */
@Composable
fun CropGrowthBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    showTip: Boolean = true,
    showTicks: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "cropGrowth",
    )
    val leaf = Meo.colors.leaf
    val track = color.copy(alpha = 0.14f)
    val tickColor = color.copy(alpha = 0.20f)
    val canvasHeight = height + if (showTip) 10.dp else 0.dp

    Canvas(modifier.fillMaxWidth().height(canvasHeight)) {
        val barHeight = height.toPx()
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        val centerY = size.height / 2f
        val top = centerY - barHeight / 2f

        drawRoundRect(
            color = track,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius,
        )
        if (showTicks) {
            // Same thresholds as FarmRules.stage.
            listOf(0.05f, 0.20f, 0.50f, 0.80f).forEach { fraction ->
                val x = size.width * fraction
                drawRoundRect(
                    color = tickColor,
                    topLeft = Offset(x - 0.75.dp.toPx(), top),
                    size = Size(1.5.dp.toPx(), barHeight),
                    cornerRadius = CornerRadius(0.75.dp.toPx(), 0.75.dp.toPx()),
                )
            }
        }
        val fillWidth = size.width * animated
        if (fillWidth > 1f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(leaf, color),
                    startX = 0f,
                    endX = fillWidth.coerceAtLeast(2f),
                ),
                topLeft = Offset(0f, top),
                size = Size(fillWidth, barHeight),
                cornerRadius = radius,
            )
        }
        if (showTip && animated > 0.01f) {
            val tipX = fillWidth.coerceIn(barHeight * 0.55f, size.width - barHeight * 0.55f)
            drawCircle(leaf.copy(alpha = 0.18f), radius = barHeight * 1.05f, center = Offset(tipX, centerY))
            drawCircle(Color.White, radius = barHeight * 0.62f, center = Offset(tipX, centerY))
            drawCircle(leaf, radius = barHeight * 0.34f, center = Offset(tipX, centerY))
        }
    }
}
