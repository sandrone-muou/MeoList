package com.meolist.core.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** One coloured share of a chart. */
data class ChartSlice(val key: Long, val value: Float, val color: Color)

/**
 * Donut chart drawn with Canvas. The slice with [selectedKey] is drawn thicker and the others are
 * faded; tapping a slice reports its key (tapping the hole reports null).
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    selectedKey: Long? = null,
    onSelect: (Long?) -> Unit = {},
    description: String = "",
    thickness: Dp = 28.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(600)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val gapColor = MaterialTheme.colorScheme.surface

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = description }
                .pointerInput(slices, total) {
                    detectTapGestures { pos ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val outer = min(size.width, size.height) / 2f
                        val inner = outer - thickness.toPx() * 1.25f
                        val dist = hypot(pos.x - c.x, pos.y - c.y)
                        if (dist < inner || dist > outer || total <= 0f) {
                            onSelect(null)
                            return@detectTapGestures
                        }
                        // Angle measured clockwise from 12 o'clock, like the drawing below.
                        var angle = Math.toDegrees(atan2((pos.y - c.y).toDouble(), (pos.x - c.x).toDouble())).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        val hit = slices.firstOrNull { s ->
                            acc += s.value / total * 360f
                            angle <= acc
                        }
                        onSelect(hit?.key?.takeIf { it != selectedKey })
                    }
                },
        ) {
            val stroke = thickness.toPx()
            val boldStroke = stroke * 1.25f
            val diameter = min(size.width, size.height) - boldStroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            var start = -90f
            slices.forEach { s ->
                val angle = s.value / total * 360f * sweep.value
                val selected = selectedKey == s.key
                val color = if (selectedKey == null || selected) s.color else s.color.copy(alpha = 0.35f)
                drawArc(color, start, angle, false, topLeft, arcSize, style = Stroke(if (selected) boldStroke else stroke))
                // Thin separator so neighbouring slices of similar colour stay distinct.
                if (slices.size > 1 && angle > 1f) {
                    drawArc(gapColor, start - 0.4f, 0.8f, false, topLeft, arcSize, style = Stroke(boldStroke))
                }
                start += angle
            }
        }
        center()
    }
}

/** A bar made of stacked segments, drawn bottom-up in list order. */
data class ChartBar(val segments: List<ChartSlice>, val label: String?) {
    val total: Float get() = segments.sumOf { it.value.toDouble() }.toFloat()
}

/**
 * Stacked bar chart with dashed guide lines. [maxLabel] describes the top guide line (the largest
 * bar), x labels are drawn under bars whose label is not null. Tapping a bar selects it.
 */
@Composable
fun StackedBarChart(
    bars: List<ChartBar>,
    maxLabel: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
    description: String = "",
    height: Dp = 180.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val guide = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(500)) }
    val max = bars.maxOfOrNull { it.total } ?: 0f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(bars.size, selectedIndex) {
                detectTapGestures { pos ->
                    if (bars.isEmpty()) return@detectTapGestures
                    val slot = size.width / bars.size.toFloat()
                    val index = (pos.x / slot).toInt().coerceIn(0, bars.lastIndex)
                    // Empty buckets cannot be inspected; tapping one clears the selection.
                    onSelect(index.takeIf { bars[it].total > 0f && it != selectedIndex })
                }
            },
    ) {
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val topPad = labelHeight + 4.dp.toPx()
        val chartBottom = size.height - labelHeight - 6.dp.toPx()
        val chartHeight = chartBottom - topPad
        val slot = if (bars.isEmpty()) size.width else size.width / bars.size
        val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

        // Guides: top (max), middle and baseline.
        listOf(topPad, topPad + chartHeight / 2f).forEach { y ->
            drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(guide, Offset(0f, chartBottom), Offset(size.width, chartBottom), strokeWidth = 1.dp.toPx())
        if (max > 0f) {
            val text = measurer.measure(maxLabel, labelStyle)
            drawText(text, topLeft = Offset(size.width - text.size.width, topPad - text.size.height - 2.dp.toPx()))
        }

        bars.forEachIndexed { i, bar ->
            val x = slot * i + (slot - barWidth) / 2f
            if (selectedIndex == i) {
                drawRect(highlight, topLeft = Offset(slot * i, topPad), size = Size(slot, chartHeight))
            }
            if (max > 0f) {
                var y = chartBottom
                val faded = selectedIndex != null && selectedIndex != i
                bar.segments.forEachIndexed { s, seg ->
                    val h = seg.value / max * chartHeight * grow.value
                    if (h <= 0f) return@forEachIndexed
                    val color = if (faded) seg.color.copy(alpha = 0.45f) else seg.color
                    val isTop = s == bar.segments.indexOfLast { it.value > 0f }
                    val radius = if (isTop) CornerRadius(min(barWidth / 3f, 4.dp.toPx())) else CornerRadius.Zero
                    drawRoundRect(color, Offset(x, y - h), Size(barWidth, h), radius)
                    if (isTop && h > radius.x) {
                        // Square off the bottom of the rounded top segment.
                        drawRect(color, Offset(x, y - radius.x), Size(barWidth, radius.x))
                    }
                    y -= h
                }
            }
            bar.label?.let { label ->
                val text = measurer.measure(label, labelStyle)
                val lx = (slot * i + slot / 2f - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(lx, chartBottom + 4.dp.toPx()))
            }
        }
    }
}

/** A coloured stretch on a [DayTimeline], in minutes since the start of the day. */
data class TimelineSpan(val startMinute: Float, val endMinute: Float, val color: Color)

/** A 24-hour strip (local wall-clock minutes 0..1440) with coloured spans and hour ticks every 6 hours. */
@Composable
fun DayTimeline(
    spans: List<TimelineSpan>,
    modifier: Modifier = Modifier,
    nowMinute: Float? = null,
    description: String = "",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.outline
    val nowColor = MaterialTheme.colorScheme.error
    Canvas(modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = description }) {
        val barHeight = 16.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(track, Offset.Zero, Size(size.width, barHeight), radius)
        spans.forEach { span ->
            val x0 = span.startMinute.coerceIn(0f, 1440f) / 1440f * size.width
            val x1 = span.endMinute.coerceIn(0f, 1440f) / 1440f * size.width
            // At least one pixel wide so very short records stay visible.
            drawRect(span.color, Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(1.dp.toPx()), barHeight))
        }
        nowMinute?.let {
            val x = it.coerceIn(0f, 1440f) / 1440f * size.width
            drawLine(nowColor, Offset(x, -2.dp.toPx()), Offset(x, barHeight + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
        listOf(0, 6, 12, 18, 24).forEach { h ->
            val x = h * 60f / 1440f * size.width
            drawLine(tick, Offset(x, barHeight), Offset(x, barHeight + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(h.toString(), labelStyle)
            val lx = (x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(lx, barHeight + 4.dp.toPx()))
        }
    }
}
