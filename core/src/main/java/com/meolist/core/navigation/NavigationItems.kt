package com.meolist.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meolist.core.R
import com.meolist.domain.settings.BottomDestination

@Composable
fun bottomDestinationLabel(destination: BottomDestination): String = stringResource(when (destination) {
    BottomDestination.TASKS -> R.string.core_nav_tasks
    BottomDestination.TRACKER -> R.string.core_nav_tracker
    BottomDestination.HABITS -> R.string.core_nav_habits
    BottomDestination.FARM -> R.string.core_nav_farm
    BottomDestination.CALENDAR -> R.string.core_nav_calendar
    BottomDestination.QUADRANT -> R.string.core_nav_quadrant
    BottomDestination.COUNTDOWNS -> R.string.core_nav_countdowns
    BottomDestination.STATS -> R.string.core_nav_stats
    BottomDestination.RECORDS -> R.string.core_nav_records
    BottomDestination.MORE -> R.string.core_nav_more
})

fun bottomDestinationIcon(destination: BottomDestination, selected: Boolean = false) = when (destination) {
    BottomDestination.TASKS -> if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
    BottomDestination.TRACKER -> if (selected) Icons.Filled.Timer else Icons.Outlined.Timer
    BottomDestination.HABITS -> if (selected) Icons.Filled.Repeat else Icons.Outlined.Repeat
    BottomDestination.FARM -> if (selected) Icons.Filled.Grass else Icons.Outlined.Grass
    BottomDestination.CALENDAR -> Icons.Outlined.CalendarMonth
    BottomDestination.QUADRANT -> Icons.Outlined.GridView
    BottomDestination.COUNTDOWNS -> Icons.Outlined.HourglassBottom
    BottomDestination.STATS -> Icons.Outlined.BarChart
    BottomDestination.RECORDS -> Icons.Outlined.ViewTimeline
    BottomDestination.MORE -> if (selected) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz
}
