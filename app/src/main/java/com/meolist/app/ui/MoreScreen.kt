package com.meolist.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.meolist.app.R
import com.meolist.core.navigation.Routes

private data class MoreEntry(
    val route: String,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val icon: ImageVector,
)

private val entries = listOf(
    MoreEntry(Routes.CALENDAR, R.string.more_calendar, R.string.more_calendar_desc, Icons.Outlined.CalendarMonth),
    MoreEntry(Routes.QUADRANT, R.string.more_quadrant, R.string.more_quadrant_desc, Icons.Outlined.GridView),
    MoreEntry(Routes.COUNTDOWNS, R.string.more_countdown, R.string.more_countdown_desc, Icons.Outlined.HourglassBottom),
    MoreEntry(Routes.STATS, R.string.more_stats, R.string.more_stats_desc, Icons.Outlined.BarChart),
    MoreEntry(Routes.RECORDS, R.string.more_records, R.string.more_records_desc, Icons.Outlined.ViewTimeline),
    MoreEntry(Routes.ACHIEVEMENTS, R.string.more_achievements, R.string.more_achievements_desc, Icons.Outlined.EmojiEvents),
    MoreEntry(Routes.SETTINGS, R.string.more_settings, R.string.more_settings_desc, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            items(entries) { entry ->
                ListItem(
                    modifier = Modifier.clickable { onNavigate(entry.route) },
                    leadingContent = {
                        Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = { Text(stringResource(entry.title)) },
                    supportingContent = { Text(stringResource(entry.desc)) },
                )
            }
        }
    }
}
