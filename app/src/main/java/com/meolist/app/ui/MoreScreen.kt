package com.meolist.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.meolist.app.R
import com.meolist.core.navigation.Routes
import com.meolist.core.theme.Meo
import com.meolist.core.ui.components.IconBadge
import com.meolist.core.ui.components.MeoListDivider
import com.meolist.core.ui.components.MeoListGroup
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.SectionHeader

private data class MoreEntry(
    val route: String,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val icon: ImageVector,
)

private val tools = listOf(
    MoreEntry(Routes.CALENDAR, R.string.more_calendar, R.string.more_calendar_desc, Icons.Outlined.CalendarMonth),
    MoreEntry(Routes.QUADRANT, R.string.more_quadrant, R.string.more_quadrant_desc, Icons.Outlined.GridView),
    MoreEntry(Routes.COUNTDOWNS, R.string.more_countdown, R.string.more_countdown_desc, Icons.Outlined.HourglassBottom),
    MoreEntry(Routes.STATS, R.string.more_stats, R.string.more_stats_desc, Icons.Outlined.BarChart),
    MoreEntry(Routes.RECORDS, R.string.more_records, R.string.more_records_desc, Icons.Outlined.ViewTimeline),
    MoreEntry(Routes.ACHIEVEMENTS, R.string.more_achievements, R.string.more_achievements_desc, Icons.Outlined.EmojiEvents),
)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    MeoScaffold(title = stringResource(R.string.more_title), large = true) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Meo.spacing.screen,
                end = Meo.spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = Meo.spacing.bottomContent,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionHeader(stringResource(R.string.more_group_tools)) }
            item {
                MeoListGroup {
                    tools.forEachIndexed { index, entry ->
                        if (index > 0) MeoListDivider()
                        MoreRow(entry) { onNavigate(entry.route) }
                    }
                }
            }
            item {
                SectionHeader(
                    stringResource(R.string.more_group_app),
                    modifier = Modifier.padding(top = Meo.spacing.sm),
                )
            }
            item {
                MeoListGroup {
                    MoreRow(MoreEntry(
                        Routes.SETTINGS, R.string.more_settings,
                        R.string.more_settings_desc, Icons.Outlined.Settings,
                    )) { onNavigate(Routes.SETTINGS) }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(entry: MoreEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Meo.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(entry.icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Meo.spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Meo.spacing.xs)) {
            Text(stringResource(entry.title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(entry.desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Meo.spacing.sm))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
