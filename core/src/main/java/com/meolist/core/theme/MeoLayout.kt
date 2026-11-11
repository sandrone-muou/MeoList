package com.meolist.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Card and chart metrics that need a phone-sized variant; spacing rhythm stays in [MeoSpacing]. */
@Immutable
data class MeoLayout(
    val compact: Boolean = false,
    val cardPadH: Dp = 16.dp,
    val cardPadV: Dp = 14.dp,
    val cardGap: Dp = 12.dp,
    val listGap: Dp = 8.dp,
    val sectionTop: Dp = 20.dp,
    val sectionBottom: Dp = 6.dp,
    val listBottom: Dp = 96.dp,
    val barChartHeight: Dp = 180.dp,
    val weekChartHeight: Dp = 236.dp,
    val heatmapCell: Dp = 36.dp,
    val heatmapGap: Dp = 4.dp,
    val donutMin: Dp = 144.dp,
    val plotTileMin: Dp = 112.dp,
    val plotTileMinWidth: Dp = 136.dp,
    val plotTileWideMinHeight: Dp = 88.dp,
    val calendarCell: Dp = 44.dp,
    val calendarDateMinHeight: Dp = 56.dp,
    val calendarTodayStroke: Dp = 1.dp,
    val taskFilterRowMinHeight: Dp = 64.dp,
    val listLeadingIconSize: Dp = 24.dp,
    val trackerPad: Dp = 12.dp,
    val trackerGap: Dp = 8.dp,
    val trackerCardMinWidth: Dp = 168.dp,
    val trackerIconSize: Dp = 40.dp,
    val trackerControlMinSize: Dp = 48.dp,
    val timeDialogMaxWidth: Dp = 440.dp,
    val timeWheelRowHeight: Dp = 48.dp,
    val timeWheelHeight: Dp = 144.dp,
) {
    companion object {
        val Compact = MeoLayout(
            compact = true,
            cardPadH = 12.dp,
            cardPadV = 10.dp,
            cardGap = 8.dp,
            listGap = 6.dp,
            sectionTop = 12.dp,
            sectionBottom = 4.dp,
            listBottom = 80.dp,
            barChartHeight = 144.dp,
            weekChartHeight = 196.dp,
            heatmapCell = 30.dp,
            heatmapGap = 3.dp,
            donutMin = 128.dp,
            plotTileMin = 96.dp,
            plotTileMinWidth = 104.dp,
            plotTileWideMinHeight = 72.dp,
            calendarCell = 40.dp,
            calendarDateMinHeight = 48.dp,
            calendarTodayStroke = 1.dp,
            taskFilterRowMinHeight = 56.dp,
            listLeadingIconSize = 24.dp,
            trackerPad = 8.dp,
            trackerGap = 6.dp,
            trackerCardMinWidth = 148.dp,
            trackerIconSize = 32.dp,
            trackerControlMinSize = 48.dp,
            timeDialogMaxWidth = 360.dp,
            timeWheelRowHeight = 44.dp,
            timeWheelHeight = 132.dp,
        )

        fun forWidth(widthDp: Int): MeoLayout = if (widthDp < 600) Compact else MeoLayout()
    }

    fun trackerColumnCount(contentWidth: Dp): Int =
        columnCount(contentWidth, trackerCardMinWidth, trackerGap).coerceAtMost(4)

    fun farmPlotColumnCount(contentWidth: Dp): Int =
        columnCount(contentWidth, plotTileMinWidth, MeoSpacing().sm).coerceAtMost(5)
}

val LocalMeoLayout = staticCompositionLocalOf { MeoLayout() }

private fun columnCount(contentWidth: Dp, minCardWidth: Dp, gap: Dp): Int {
    if (contentWidth <= 0.dp) return 1
    return ((contentWidth.value + gap.value) / (minCardWidth.value + gap.value))
        .toInt()
        .coerceAtLeast(1)
}
