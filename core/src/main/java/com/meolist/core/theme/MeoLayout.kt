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
    val toolCardHeight: Dp = 152.dp,
    val calendarCell: Dp = 44.dp,
    val trackerPad: Dp = 12.dp,
    val trackerGap: Dp = 8.dp,
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
            toolCardHeight = 128.dp,
            calendarCell = 40.dp,
            trackerPad = 10.dp,
            trackerGap = 6.dp,
        )

        fun forWidth(widthDp: Int): MeoLayout = if (widthDp < 600) Compact else MeoLayout()
    }
}

val LocalMeoLayout = staticCompositionLocalOf { MeoLayout() }
