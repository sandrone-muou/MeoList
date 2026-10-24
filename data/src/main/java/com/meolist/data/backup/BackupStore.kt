package com.meolist.data.backup

import androidx.room.withTransaction
import com.meolist.data.db.MeoDatabase
import com.meolist.data.db.dao.ActivityTypeDao
import com.meolist.data.db.dao.CountdownDao
import com.meolist.data.db.dao.FarmDao
import com.meolist.data.db.dao.HabitDao
import com.meolist.data.db.dao.ReminderDao
import com.meolist.data.db.dao.TagDao
import com.meolist.data.db.dao.TaskDao
import com.meolist.data.db.dao.TaskListDao
import com.meolist.data.db.dao.TimeRecordDao
import com.meolist.data.db.entity.AchievementEntity
import com.meolist.data.db.entity.ActivityGoalEntity
import com.meolist.data.db.entity.ActivityTypeEntity
import com.meolist.data.db.entity.CountdownEntity
import com.meolist.data.db.entity.FarmPlotEntity
import com.meolist.data.db.entity.GoalReachedEntity
import com.meolist.data.db.entity.HabitCheckInEntity
import com.meolist.data.db.entity.HabitEntity
import com.meolist.data.db.entity.HarvestEntity
import com.meolist.data.db.entity.ListFolderEntity
import com.meolist.data.db.entity.NutrientEntity
import com.meolist.data.db.entity.RecordEntity
import com.meolist.data.db.entity.ReminderEntity
import com.meolist.data.db.entity.RunningTimerEntity
import com.meolist.data.db.entity.TagEntity
import com.meolist.data.db.entity.TaskEntity
import com.meolist.data.db.entity.TaskListEntity
import com.meolist.data.db.entity.TaskTagEntity
import com.meolist.domain.common.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads every table into a [BackupFile] and writes one back, preserving all ids and links. */
@Singleton
class BackupStore @Inject constructor(
    private val db: MeoDatabase,
    private val taskDao: TaskDao,
    private val listDao: TaskListDao,
    private val tagDao: TagDao,
    private val reminderDao: ReminderDao,
    private val typeDao: ActivityTypeDao,
    private val recordDao: TimeRecordDao,
    private val habitDao: HabitDao,
    private val countdownDao: CountdownDao,
    private val farmDao: FarmDao,
    private val clock: Clock,
) {
    suspend fun snapshot(): BackupFile = BackupFile(
        format = BackupFile.FORMAT,
        createdAt = clock.now(),
        folders = listDao.getFolders().map { FolderDto(it.id, it.name, it.sortOrder) },
        lists = listDao.getLists().map {
            ListDto(it.id, it.folderId, it.name, it.color, it.sortOrder, it.defaultActivityTypeId, it.archived)
        },
        tasks = taskDao.getAllIncludingDeleted().map {
            TaskDto(
                id = it.id,
                listId = it.listId,
                parentId = it.parentId,
                title = it.title,
                note = it.note,
                priority = it.priority,
                status = it.status,
                completedAt = it.completedAt,
                startAt = it.startAt,
                dueAt = it.dueAt,
                allDay = it.allDay,
                repeatRule = it.repeatRule,
                repeatFromCompletion = it.repeatFromCompletion,
                repeatOriginId = it.repeatOriginId,
                sortOrder = it.sortOrder,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                deletedAt = it.deletedAt,
            )
        },
        tags = tagDao.getTags().map { TagDto(it.id, it.name, it.color) },
        taskTags = taskDao.getAllTaskTags().map { TaskTagDto(it.taskId, it.tagId) },
        reminders = reminderDao.getAll().map { it.toDto() },
        activityTypes = typeDao.getTypes().map {
            ActivityTypeDto(it.id, it.name, it.icon, it.color, it.sortOrder, it.archived)
        },
        goals = typeDao.getGoals().map { GoalDto(it.typeId, it.period, it.targetSec) },
        goalReached = typeDao.getGoalsReached().map {
            GoalReachedDto(it.typeId, it.period, it.periodStartDay, it.reachedAt)
        },
        runningTimers = recordDao.getRunning().map {
            RunningTimerDto(it.id, it.typeId, it.taskId, it.startedAt, it.comment)
        },
        records = recordDao.getAllRecords().map {
            RecordDto(it.id, it.typeId, it.taskId, it.startAt, it.endAt, it.comment, it.isManual)
        },
        habits = habitDao.getHabits().map {
            HabitDto(
                it.id,
                it.name,
                it.icon,
                it.color,
                it.scheduleRule,
                it.dailyTarget,
                it.unit,
                it.startDay,
                it.archived,
                it.sortOrder,
                it.createdAt,
            )
        },
        checkIns = habitDao.getAllCheckIns().map { CheckInDto(it.habitId, it.day, it.value) },
        countdowns = countdownDao.getAllCountdowns().map {
            CountdownDto(
                it.id,
                it.title,
                it.baseDay,
                it.mode,
                it.yearly,
                it.pinned,
                it.archived,
                it.color,
                it.note,
                it.sortOrder,
                it.createdAt,
            )
        },
        farmPlots = farmDao.getPlots().map { FarmPlotDto(it.id, it.slot, it.crop, it.plantedAt, it.harvestedAt) },
        nutrients = farmDao.getNutrients().map { NutrientDto(it.sourceKey, it.plotId, it.amount, it.earnedAt) },
        harvests = farmDao.getHarvests().map {
            HarvestDto(it.id, it.crop, it.plotId, it.harvestedAt, it.nutrients)
        },
        achievements = farmDao.getAchievements().map { AchievementDto(it.key, it.unlockedAt) },
    )

    /**
     * Clears every table and inserts [file]. The file must have been validated already; ids are
     * inserted as-is so task/subtask links, tag links, record owners and farm assignments survive.
     */
    suspend fun restore(file: BackupFile) = withContext(Dispatchers.IO) {
        // clearAllTables is blocking and asserts that it is not on the main thread.
        db.clearAllTables()
        db.withTransaction {
            // Parents before children, otherwise foreign keys reject the inserts.
            file.folders.forEach {
                listDao.upsertFolder(ListFolderEntity(it.id, it.name, it.sortOrder))
            }
            file.lists.forEach {
                listDao.upsertList(
                    TaskListEntity(
                        id = it.id,
                        folderId = it.folderId,
                        name = it.name,
                        color = it.color,
                        sortOrder = it.sortOrder,
                        defaultActivityTypeId = it.defaultActivityTypeId,
                        archived = it.archived,
                    ),
                )
            }
            file.tags.forEach { tagDao.upsert(TagEntity(it.id, it.name, it.color)) }
            if (file.tasks.isNotEmpty()) {
                val ordered = file.tasks.sortedBy { it.parentId != null }
                taskDao.insertAll(
                    ordered.map {
                        TaskEntity(
                            id = it.id,
                            listId = it.listId,
                            parentId = it.parentId,
                            title = it.title,
                            note = it.note,
                            priority = it.priority,
                            status = it.status,
                            completedAt = it.completedAt,
                            startAt = it.startAt,
                            dueAt = it.dueAt,
                            allDay = it.allDay,
                            repeatRule = it.repeatRule,
                            repeatFromCompletion = it.repeatFromCompletion,
                            repeatOriginId = it.repeatOriginId,
                            sortOrder = it.sortOrder,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            deletedAt = it.deletedAt,
                        )
                    },
                )
            }
            if (file.taskTags.isNotEmpty()) {
                taskDao.insertTags(file.taskTags.map { TaskTagEntity(it.taskId, it.tagId) })
            }
            if (file.reminders.isNotEmpty()) {
                reminderDao.insertAll(file.reminders.map { it.toEntity() })
            }
            if (file.activityTypes.isNotEmpty()) {
                typeDao.insertTypes(
                    file.activityTypes.map {
                        ActivityTypeEntity(it.id, it.name, it.icon, it.color, it.sortOrder, it.archived)
                    },
                )
            }
            if (file.goals.isNotEmpty()) {
                typeDao.insertGoals(file.goals.map { ActivityGoalEntity(it.typeId, it.period, it.targetSec) })
            }
            file.goalReached.forEach {
                typeDao.insertGoalReached(GoalReachedEntity(it.typeId, it.period, it.periodStartDay, it.reachedAt))
            }
            if (file.runningTimers.isNotEmpty()) {
                recordDao.insertRunningTimers(
                    file.runningTimers.map {
                        RunningTimerEntity(it.id, it.typeId, it.taskId, it.startedAt, it.comment)
                    },
                )
            }
            if (file.records.isNotEmpty()) {
                recordDao.insertRecords(
                    file.records.map {
                        RecordEntity(it.id, it.typeId, it.taskId, it.startAt, it.endAt, it.comment, it.isManual)
                    },
                )
            }
            file.habits.forEach {
                habitDao.insertHabit(
                    HabitEntity(
                        id = it.id,
                        name = it.name,
                        icon = it.icon,
                        color = it.color,
                        scheduleRule = it.scheduleRule,
                        dailyTarget = it.dailyTarget,
                        unit = it.unit,
                        startDay = it.startDay,
                        archived = it.archived,
                        sortOrder = it.sortOrder,
                        createdAt = it.createdAt,
                    ),
                )
            }
            file.checkIns.forEach {
                habitDao.upsertCheckIn(HabitCheckInEntity(it.habitId, it.day, it.value))
            }
            file.countdowns.forEach {
                countdownDao.insert(
                    CountdownEntity(
                        id = it.id,
                        title = it.title,
                        baseDay = it.baseDay,
                        mode = it.mode,
                        yearly = it.yearly,
                        pinned = it.pinned,
                        archived = it.archived,
                        color = it.color,
                        note = it.note,
                        sortOrder = it.sortOrder,
                        createdAt = it.createdAt,
                    ),
                )
            }
            file.farmPlots.forEach {
                farmDao.insertPlot(FarmPlotEntity(it.id, it.slot, it.crop, it.plantedAt, it.harvestedAt))
            }
            if (file.nutrients.isNotEmpty()) {
                farmDao.upsertNutrients(
                    file.nutrients.map { NutrientEntity(it.sourceKey, it.plotId, it.amount, it.earnedAt) },
                )
            }
            file.harvests.forEach {
                farmDao.insertHarvest(HarvestEntity(it.id, it.crop, it.plotId, it.harvestedAt, it.nutrients))
            }
            file.achievements.forEach {
                farmDao.insertAchievement(AchievementEntity(it.key, it.unlockedAt))
            }
        }
    }

    /** Records in chronological order, with readable names, as a spreadsheet-friendly CSV. */
    suspend fun recordsCsv(): String {
        val types = typeDao.getTypes().associate { it.id to it.name }
        val tasks = taskDao.getAllIncludingDeleted().associate { it.id to it.title }
        return RecordCsv.render(recordDao.getAllRecords(), types, tasks, clock.zone())
    }

    private fun ReminderEntity.toDto() = ReminderDto(
        id = id,
        ownerType = ownerType,
        ownerId = ownerId,
        kind = kind,
        offsetMin = offsetMin,
        minuteOfDay = minuteOfDay,
        nextFireAt = nextFireAt,
        snoozedUntil = snoozedUntil,
        enabled = enabled,
    )

    private fun ReminderDto.toEntity() = ReminderEntity(
        id = id,
        ownerType = ownerType,
        ownerId = ownerId,
        kind = kind,
        offsetMin = offsetMin,
        minuteOfDay = minuteOfDay,
        nextFireAt = nextFireAt,
        snoozedUntil = snoozedUntil,
        enabled = enabled,
    )
}
