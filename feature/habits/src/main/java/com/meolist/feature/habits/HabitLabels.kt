package com.meolist.feature.habits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.meolist.core.ui.currentLocale
import com.meolist.domain.habit.HabitSchedule
import java.time.format.TextStyle

@Composable
@ReadOnlyComposable
fun scheduleLabel(schedule: HabitSchedule): String = when (schedule) {
    HabitSchedule.Daily -> stringResource(R.string.habits_schedule_daily)
    is HabitSchedule.Weekdays -> {
        val locale = currentLocale()
        schedule.days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    is HabitSchedule.WeeklyTimes -> stringResource(R.string.habits_schedule_weekly, schedule.times)
}
