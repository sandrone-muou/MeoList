package com.meolist.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.meolist.core.R
import com.meolist.core.theme.Meo
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.domain.calendar.CalendarPeriod
import com.meolist.domain.calendar.CalendarRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarNavigator(
    anchor: LocalDate,
    period: CalendarPeriod,
    firstDayOfWeek: DayOfWeek,
    onPeriod: (CalendarPeriod) -> Unit,
    onMove: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val locale = currentLocale()
    val monthFormat = DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale,
    )
    val week = CalendarRules.weekDays(anchor, firstDayOfWeek)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (period == CalendarPeriod.MONTH) monthFormat.format(anchor)
                else "${longDate(week.first(), locale)} – ${longDate(week.last(), locale)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToday) { Text(stringResource(R.string.core_today)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.core_calendar_previous))
            }
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                CalendarPeriod.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = value == period,
                        onClick = { onPeriod(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarPeriod.entries.size),
                        icon = {},
                    ) {
                        Text(stringResource(if (value == CalendarPeriod.MONTH) R.string.core_calendar_month else R.string.core_calendar_week))
                    }
                }
            }
            IconButton(onClick = { onMove(1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.core_calendar_next))
            }
        }
    }
}

@Composable
fun MonthDateGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    marker: @Composable (LocalDate) -> Unit = {},
) {
    val dates = CalendarRules.monthGrid(month, firstDayOfWeek)
    Column(Modifier.fillMaxWidth()) {
        WeekdayLabels(dates.take(7))
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DateCell(date, today, selected, YearMonth.from(date) == month, onSelect, Modifier.weight(1f), marker)
                }
            }
        }
    }
}

@Composable
fun WeekDateStrip(
    anchor: LocalDate,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    marker: @Composable (LocalDate) -> Unit = {},
) {
    val dates = CalendarRules.weekDays(anchor, firstDayOfWeek)
    Column(modifier.fillMaxWidth()) {
        WeekdayLabels(dates)
        Row(Modifier.fillMaxWidth()) {
            dates.forEach { date -> DateCell(date, today, selected, true, onSelect, Modifier.weight(1f), marker) }
        }
    }
}

@Composable
private fun WeekdayLabels(dates: List<LocalDate>) {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth()) {
        dates.forEach { date ->
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = Meo.spacing.xs),
            )
        }
    }
}

@Composable
private fun DateCell(
    date: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    inMonth: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
    marker: @Composable (LocalDate) -> Unit,
) {
    val label = longDate(date, currentLocale())
    val active = date == selected
    Column(
        modifier.heightIn(min = Meo.layout.calendarDateMinHeight)
            .selectable(active, role = Role.Tab, onClick = { onSelect(date) })
            .semantics { contentDescription = label }
            .padding(vertical = Meo.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                .then(if (date == today && !active) Modifier.border(Meo.layout.calendarTodayStroke, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                .padding(horizontal = Meo.spacing.sm, vertical = Meo.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    active -> MaterialTheme.colorScheme.onPrimary
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    date == today -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        marker(date)
    }
}
