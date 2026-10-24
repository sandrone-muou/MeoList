package com.meolist.data.backup

import com.meolist.domain.backup.BackupService
import com.meolist.domain.backup.BackupSummary
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.tracker.TimerSync
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class BackupServiceImpl @Inject constructor(
    private val store: BackupStore,
    private val prefs: AppPreferences,
    private val reminders: ReminderScheduler,
    private val timerSync: TimerSync,
) : BackupService {
    override suspend fun createBackup(): String {
        val file = store.snapshot().copy(settings = SettingsDto.from(prefs.settings.first()))
        return BackupCodec.encode(file)
    }

    override suspend fun restoreBackup(json: String): BackupSummary {
        val file = BackupCodec.decode(json)
        store.restore(file)
        file.settings?.let { settings -> prefs.updateSettings { settings.toSettings() } }
        // Reminder fire times are derived from tasks, and all ids/links may have changed.
        reminders.rescheduleAll(recompute = true)
        timerSync.sync()
        return BackupSummary(
            createdAt = file.createdAt,
            lists = file.lists.size,
            tasks = file.tasks.size,
            records = file.records.size,
            habits = file.habits.size,
            countdowns = file.countdowns.size,
            harvests = file.harvests.size,
            settingsRestored = file.settings != null,
        )
    }

    override suspend fun recordsCsv(): String = store.recordsCsv()
}
