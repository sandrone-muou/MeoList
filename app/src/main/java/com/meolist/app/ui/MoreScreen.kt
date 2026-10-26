package com.meolist.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meolist.app.R
import com.meolist.core.navigation.Routes
import com.meolist.core.theme.Meo
import com.meolist.core.ui.components.IconBadge
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = Meo.spacing.screen,
                end = Meo.spacing.screen,
                top = 0.dp,
                bottom = Meo.spacing.bottomContent,
            ),
            horizontalArrangement = Arrangement.spacedBy(Meo.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Meo.spacing.md),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.more_group_tools))
            }
            items(tools) { entry ->
                ToolCard(entry = entry, onClick = { onNavigate(entry.route) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    stringResource(R.string.more_group_app),
                    modifier = Modifier.padding(top = Meo.spacing.sm),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SettingsCard(onClick = { onNavigate(Routes.SETTINGS) })
            }
        }
    }
}

@Composable
private fun ToolCard(entry: MoreEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().height(152.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(Meo.spacing.lg)) {
            IconBadge(entry.icon, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(entry.title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(entry.desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Meo.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Outlined.Settings, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(Meo.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.more_settings), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.more_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
