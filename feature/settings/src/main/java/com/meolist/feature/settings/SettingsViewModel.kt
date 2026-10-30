package com.meolist.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.domain.backup.BackupFormatException
import com.meolist.domain.backup.BackupService
import com.meolist.domain.backup.BackupSummary
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.AppSettings
import com.meolist.domain.settings.MonthChartStyle
import com.meolist.domain.settings.StatsDefaultView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsEvent {
    data object BackupSaved : SettingsEvent
    data object CsvSaved : SettingsEvent
    data class Restored(val summary: BackupSummary) : SettingsEvent
    data object InvalidBackup : SettingsEvent
    data object Failed : SettingsEvent
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val busy: Boolean = false,
    val event: SettingsEvent? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val backup: BackupService,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(prefs.settings, local) { settings, ui ->
        ui.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setMultipleTimers(value: Boolean) = update { it.copy(multipleTimers = value) }

    fun setIgnoreShortSeconds(seconds: Int) = update { it.copy(ignoreShortRecordSec = seconds.coerceIn(0, 600)) }

    fun setStopOnComplete(value: Boolean) = update { it.copy(stopTimerOnTaskComplete = value) }

    fun setUrgentDays(days: Int) = update { it.copy(urgentDays = days.coerceIn(1, 30)) }

    fun setFirstDayOfWeek(day: DayOfWeek) = update { it.copy(firstDayOfWeek = day) }

    fun setDefaultReminderMinute(minute: Int) =
        update { it.copy(defaultReminderMinute = minute.coerceIn(0, 23 * 60 + 59)) }

    fun setMonthChartStyle(style: MonthChartStyle) = update { it.copy(monthChartStyle = style) }

    fun setStatsDefaultView(view: StatsDefaultView) = update { it.copy(statsDefaultView = view) }

    fun consumeEvent() = local.update { it.copy(event = null) }

    fun writeBackup(uri: Uri) = runAction {
        val text = withContext(Dispatchers.IO) { backup.createBackup() }
        write(uri, text)
        SettingsEvent.BackupSaved
    }

    fun exportCsv(uri: Uri) = runAction {
        val text = withContext(Dispatchers.IO) { backup.recordsCsv() }
        write(uri, text)
        SettingsEvent.CsvSaved
    }

    fun restore(uri: Uri) = runAction {
        try {
            val text = read(uri)
            SettingsEvent.Restored(withContext(Dispatchers.IO) { backup.restoreBackup(text) })
        } catch (error: BackupFormatException) {
            SettingsEvent.InvalidBackup
        } catch (error: Exception) {
            Log.e("MeoList", "restore failed", error)
            throw error
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        prefs.updateSettings(transform)
    }

    private fun runAction(action: suspend () -> SettingsEvent) = viewModelScope.launch {
        if (local.value.busy) return@launch
        local.update { it.copy(busy = true, event = null) }
        val event = try {
            action()
        } catch (error: Exception) {
            Log.e("MeoList", "settings action failed", error)
            SettingsEvent.Failed
        }
        local.update { it.copy(busy = false, event = event) }
    }

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
