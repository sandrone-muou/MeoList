package com.meolist.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.AppSettings
import com.meolist.domain.settings.MonthChartStyle
import com.meolist.domain.settings.StatsDefaultView
import com.meolist.domain.stats.StatsPeriod
import com.meolist.domain.task.Quadrant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meolist_prefs")

@Singleton
class DataStoreAppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : AppPreferences {

    private val store = context.dataStore

    private object Keys {
        val multipleTimers = booleanPreferencesKey("multiple_timers")
        val ignoreShortRecordSec = intPreferencesKey("ignore_short_record_sec")
        val firstDayOfWeek = intPreferencesKey("first_day_of_week")
        val urgentDays = intPreferencesKey("urgent_days")
        val stopTimerOnTaskComplete = booleanPreferencesKey("stop_timer_on_task_complete")
        val defaultReminderMinute = intPreferencesKey("default_reminder_minute")
        val monthChartStyle = stringPreferencesKey("month_chart_style")
        val statsDefaultView = stringPreferencesKey("stats_default_view")
        val lastStatsPeriod = stringPreferencesKey("last_stats_period")
        val lastTaskFilter = stringPreferencesKey("last_task_filter")
        val quadrantTags = mapOf(
            Quadrant.URGENT_IMPORTANT to longPreferencesKey("quadrant_tag_urgent_important"),
            Quadrant.NOT_URGENT_IMPORTANT to longPreferencesKey("quadrant_tag_not_urgent_important"),
            Quadrant.URGENT_NOT_IMPORTANT to longPreferencesKey("quadrant_tag_urgent_not_important"),
            Quadrant.NOT_URGENT_NOT_IMPORTANT to longPreferencesKey("quadrant_tag_not_urgent_not_important"),
        )
    }

    override val settings: Flow<AppSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.multipleTimers] = next.multipleTimers
            prefs[Keys.ignoreShortRecordSec] = next.ignoreShortRecordSec
            prefs[Keys.firstDayOfWeek] = next.firstDayOfWeek.value
            prefs[Keys.urgentDays] = next.urgentDays
            prefs[Keys.stopTimerOnTaskComplete] = next.stopTimerOnTaskComplete
            prefs[Keys.defaultReminderMinute] = next.defaultReminderMinute
            prefs[Keys.monthChartStyle] = next.monthChartStyle.name
            prefs[Keys.statsDefaultView] = next.statsDefaultView.name
            prefs[Keys.lastStatsPeriod] = next.lastStatsPeriod.name
            Keys.quadrantTags.forEach { (quadrant, key) ->
                val tagId = next.quadrantTags[quadrant]
                if (tagId != null) prefs[key] = tagId else prefs.remove(key)
            }
        }
    }

    override val lastTaskFilter: Flow<String?> = store.data.map { it[Keys.lastTaskFilter] }.distinctUntilChanged()

    override suspend fun setLastTaskFilter(key: String) {
        store.edit { it[Keys.lastTaskFilter] = key }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            multipleTimers = this[Keys.multipleTimers] ?: defaults.multipleTimers,
            ignoreShortRecordSec = this[Keys.ignoreShortRecordSec] ?: defaults.ignoreShortRecordSec,
            firstDayOfWeek = this[Keys.firstDayOfWeek]?.let { runCatching { DayOfWeek.of(it) }.getOrNull() }
                ?: defaults.firstDayOfWeek,
            urgentDays = this[Keys.urgentDays] ?: defaults.urgentDays,
            stopTimerOnTaskComplete = this[Keys.stopTimerOnTaskComplete] ?: defaults.stopTimerOnTaskComplete,
            defaultReminderMinute = this[Keys.defaultReminderMinute] ?: defaults.defaultReminderMinute,
            monthChartStyle = this[Keys.monthChartStyle]?.let { runCatching { MonthChartStyle.valueOf(it) }.getOrNull() }
                ?: defaults.monthChartStyle,
            statsDefaultView = this[Keys.statsDefaultView]?.let { runCatching { StatsDefaultView.valueOf(it) }.getOrNull() }
                ?: defaults.statsDefaultView,
            lastStatsPeriod = this[Keys.lastStatsPeriod]?.let { runCatching { StatsPeriod.valueOf(it) }.getOrNull() }
                ?: defaults.lastStatsPeriod,
            quadrantTags = Keys.quadrantTags.mapNotNull { (quadrant, key) ->
                this[key]?.let { quadrant to it }
            }.toMap(),
        )
    }
}
