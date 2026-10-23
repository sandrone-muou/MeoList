package com.meolist.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.AppSettings
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
        val lastTaskFilter = stringPreferencesKey("last_task_filter")
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
        )
    }
}
