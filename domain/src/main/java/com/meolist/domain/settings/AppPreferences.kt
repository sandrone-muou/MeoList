package com.meolist.domain.settings

import com.meolist.domain.stats.StatsPeriod
import com.meolist.domain.task.Quadrant
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/** How the monthly statistics view is drawn. */
enum class MonthChartStyle { HEATMAP, BARS }

enum class StatsDefaultView { DAY, WEEK, MONTH, YEAR, LAST_USED }

data class AppSettings(
    /** Allow several activity timers to run at the same time. */
    val multipleTimers: Boolean = true,
    /** Records shorter than this many seconds are discarded when a timer stops. */
    val ignoreShortRecordSec: Int = 60,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Tasks due within this many days count as urgent in the Eisenhower matrix. */
    val urgentDays: Int = 1,
    /** Completing a task stops timers linked to it. */
    val stopTimerOnTaskComplete: Boolean = true,
    /** Default time of day (minutes) for reminders on all-day items. */
    val defaultReminderMinute: Int = 9 * 60,
    /** Chart used for the monthly statistics view. */
    val monthChartStyle: MonthChartStyle = MonthChartStyle.HEATMAP,
    /** Optional tag per quadrant; tagged tasks take precedence over the date/priority fallback. */
    val quadrantTags: Map<Quadrant, Long> = emptyMap(),
    val statsDefaultView: StatsDefaultView = StatsDefaultView.WEEK,
    val lastStatsPeriod: StatsPeriod = StatsPeriod.WEEK,
)

interface AppPreferences {
    val settings: Flow<AppSettings>
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)

    val lastTaskFilter: Flow<String?>
    suspend fun setLastTaskFilter(key: String)
}
