package com.meolist.data.backup

import com.meolist.domain.settings.AppSettings
import com.meolist.domain.settings.MonthChartStyle
import com.meolist.domain.settings.StatsDefaultView
import com.meolist.domain.stats.StatsPeriod
import com.meolist.domain.task.Quadrant
import java.time.DayOfWeek
import kotlinx.serialization.Serializable

/**
 * The on-disk backup format. Every field has a default so older or partial files stay readable;
 * [format] is bumped only for incompatible changes.
 */
@Serializable
data class BackupFile(
    /** Required, so an arbitrary JSON file (for example "{}") cannot wipe the database. */
    val format: Int,
    val createdAt: Long = 0,
    val folders: List<FolderDto> = emptyList(),
    val lists: List<ListDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val taskTags: List<TaskTagDto> = emptyList(),
    val reminders: List<ReminderDto> = emptyList(),
    val activityTypes: List<ActivityTypeDto> = emptyList(),
    val goals: List<GoalDto> = emptyList(),
    val goalReached: List<GoalReachedDto> = emptyList(),
    val runningTimers: List<RunningTimerDto> = emptyList(),
    val records: List<RecordDto> = emptyList(),
    val habits: List<HabitDto> = emptyList(),
    val checkIns: List<CheckInDto> = emptyList(),
    val countdowns: List<CountdownDto> = emptyList(),
    val farmPlots: List<FarmPlotDto> = emptyList(),
    val nutrients: List<NutrientDto> = emptyList(),
    val harvests: List<HarvestDto> = emptyList(),
    val achievements: List<AchievementDto> = emptyList(),
    val settings: SettingsDto? = null,
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class FolderDto(val id: Long, val name: String, val sortOrder: Long)

@Serializable
data class ListDto(
    val id: Long,
    val folderId: Long?,
    val name: String,
    val color: Int,
    val sortOrder: Long,
    val defaultActivityTypeId: Long?,
    val archived: Boolean,
)

@Serializable
data class TaskDto(
    val id: Long,
    val listId: Long?,
    val parentId: Long?,
    val title: String,
    val note: String,
    val priority: Int,
    val status: Int,
    val completedAt: Long?,
    val startAt: Long?,
    val dueAt: Long?,
    val allDay: Boolean,
    val repeatRule: String?,
    val repeatFromCompletion: Boolean,
    val repeatOriginId: Long?,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
data class TagDto(val id: Long, val name: String, val color: Int)

@Serializable
data class TaskTagDto(val taskId: Long, val tagId: Long)

@Serializable
data class ReminderDto(
    val id: Long,
    val ownerType: Int,
    val ownerId: Long,
    val kind: Int,
    val offsetMin: Int,
    val minuteOfDay: Int?,
    val nextFireAt: Long?,
    val snoozedUntil: Long?,
    val enabled: Boolean,
)

@Serializable
data class ActivityTypeDto(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Int,
    val sortOrder: Long,
    val archived: Boolean,
)

@Serializable
data class GoalDto(val typeId: Long, val period: Int, val targetSec: Long)

@Serializable
data class GoalReachedDto(val typeId: Long, val period: Int, val periodStartDay: Long, val reachedAt: Long)

@Serializable
data class RunningTimerDto(
    val id: Long,
    val typeId: Long,
    val taskId: Long?,
    val startedAt: Long,
    val comment: String,
)

@Serializable
data class RecordDto(
    val id: Long,
    val typeId: Long,
    val taskId: Long?,
    val startAt: Long,
    val endAt: Long,
    val comment: String,
    val isManual: Boolean,
)

@Serializable
data class HabitDto(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Int,
    val scheduleRule: String,
    val dailyTarget: Int,
    val unit: String,
    val startDay: Long,
    val archived: Boolean,
    val sortOrder: Long,
    val createdAt: Long,
)

@Serializable
data class CheckInDto(val habitId: Long, val day: Long, val value: Int)

@Serializable
data class CountdownDto(
    val id: Long,
    val title: String,
    val baseDay: Long,
    val mode: Int,
    val yearly: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val color: Int,
    val note: String,
    val sortOrder: Long,
    val createdAt: Long,
)

@Serializable
data class FarmPlotDto(
    val id: Long,
    val slot: Int,
    val crop: String,
    val plantedAt: Long,
    val harvestedAt: Long?,
)

@Serializable
data class NutrientDto(val sourceKey: String, val plotId: Long?, val amount: Int, val earnedAt: Long)

@Serializable
data class HarvestDto(
    val id: Long,
    val crop: String,
    val plotId: Long,
    val harvestedAt: Long,
    val nutrients: Int,
)

@Serializable
data class AchievementDto(val key: String, val unlockedAt: Long)

@Serializable
data class SettingsDto(
    val multipleTimers: Boolean,
    val ignoreShortRecordSec: Int,
    val firstDayOfWeek: Int,
    val urgentDays: Int,
    val stopTimerOnTaskComplete: Boolean,
    val defaultReminderMinute: Int,
    val monthChartStyle: String = MonthChartStyle.HEATMAP.name,
    val quadrantTags: Map<String, Long> = emptyMap(),
    val statsDefaultView: String = StatsDefaultView.WEEK.name,
    val lastStatsPeriod: String = StatsPeriod.WEEK.name,
) {
    fun toSettings(): AppSettings = AppSettings(
        multipleTimers = multipleTimers,
        ignoreShortRecordSec = ignoreShortRecordSec,
        firstDayOfWeek = runCatching { DayOfWeek.of(firstDayOfWeek) }.getOrDefault(DayOfWeek.MONDAY),
        urgentDays = urgentDays.coerceIn(1, 30),
        stopTimerOnTaskComplete = stopTimerOnTaskComplete,
        defaultReminderMinute = defaultReminderMinute.coerceIn(0, 1439),
        monthChartStyle = runCatching { MonthChartStyle.valueOf(monthChartStyle) }
            .getOrDefault(MonthChartStyle.HEATMAP),
        statsDefaultView = runCatching { StatsDefaultView.valueOf(statsDefaultView) }
            .getOrDefault(StatsDefaultView.WEEK),
        lastStatsPeriod = runCatching { StatsPeriod.valueOf(lastStatsPeriod) }
            .getOrDefault(StatsPeriod.WEEK),
        quadrantTags = quadrantTags.mapNotNull { (key, id) ->
            runCatching { Quadrant.valueOf(key) }.getOrNull()?.let { it to id }
        }.toMap(),
    )

    companion object {
        fun from(settings: AppSettings) = SettingsDto(
            multipleTimers = settings.multipleTimers,
            ignoreShortRecordSec = settings.ignoreShortRecordSec,
            firstDayOfWeek = settings.firstDayOfWeek.value,
            urgentDays = settings.urgentDays,
            stopTimerOnTaskComplete = settings.stopTimerOnTaskComplete,
            defaultReminderMinute = settings.defaultReminderMinute,
            monthChartStyle = settings.monthChartStyle.name,
            quadrantTags = settings.quadrantTags.mapKeys { it.key.name },
            statsDefaultView = settings.statsDefaultView.name,
            lastStatsPeriod = settings.lastStatsPeriod.name,
        )
    }
}
