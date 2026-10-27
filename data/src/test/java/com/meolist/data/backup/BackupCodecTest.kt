package com.meolist.data.backup

import com.meolist.domain.backup.BackupFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private fun sample() = BackupFile(
        format = BackupFile.FORMAT,
        createdAt = 1_700_000_000_000,
        folders = listOf(FolderDto(1, "Work", 1)),
        lists = listOf(ListDto(2, 1, "Project", 0x3B6939, 1, 3, false)),
        tasks = listOf(
            TaskDto(
                id = 4,
                listId = 2,
                parentId = null,
                title = "Plan",
                note = "with, comma",
                priority = 2,
                status = 1,
                completedAt = 10,
                startAt = null,
                dueAt = 20,
                allDay = true,
                repeatRule = "FREQ=WEEKLY;INTERVAL=1",
                repeatFromCompletion = false,
                repeatOriginId = null,
                sortOrder = 1,
                createdAt = 5,
                updatedAt = 10,
                deletedAt = null,
            ),
        ),
        tags = listOf(TagDto(5, "home", 0xFF0000)),
        taskTags = listOf(TaskTagDto(4, 5)),
        reminders = listOf(ReminderDto(6, 0, 4, 1, 10, null, 30, null, true)),
        activityTypes = listOf(ActivityTypeDto(3, "Work", "timer", 1, 1, false)),
        goals = listOf(GoalDto(3, 0, 3600)),
        goalReached = listOf(GoalReachedDto(3, 0, 20_000, 10)),
        runningTimers = listOf(RunningTimerDto(7, 3, 1, 50, "deep work")),
        records = listOf(RecordDto(8, 3, 1, 50, 60, "note", false)),
        habits = listOf(HabitDto(9, "Read", "read", 2, "daily", 1, "", 20_000, false, 1, 0)),
        checkIns = listOf(CheckInDto(9, 20_000, 1)),
        countdowns = listOf(CountdownDto(10, "Trip", 20_100, 0, false, false, false, 3, "", 1, 0)),
        farmPlots = listOf(FarmPlotDto(11, 0, "carrot", 60, null)),
        nutrients = listOf(NutrientDto("record:8", 11, 10, 60)),
        harvests = listOf(HarvestDto(12, "carrot", 11, 70, 180)),
        achievements = listOf(AchievementDto("first_harvest", 70)),
        settings = SettingsDto(
            multipleTimers = true,
            ignoreShortRecordSec = 60,
            firstDayOfWeek = 1,
            urgentDays = 1,
            stopTimerOnTaskComplete = true,
            defaultReminderMinute = 540,
            quadrantTags = mapOf("URGENT_IMPORTANT" to 5L),
        ),
    )

    @Test
    fun roundTripKeepsEveryField() {
        val file = sample()
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun emptyObjectIsRejected() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{}") }
    }

    @Test
    fun otherFormatsAreRejected() {
        val json = BackupCodec.encode(sample().copy(format = 99))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json) }
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = BackupCodec.encode(sample()).replaceFirst("\"format\":1", "\"format\":1,\"future\":true")
        assertEquals(sample(), BackupCodec.decode(json))
    }

    @Test
    fun oldBackupWithoutQuadrantTagsFallsBackToAutomatic() {
        val json = BackupCodec.encode(sample()).replace(Regex(",\"quadrantTags\":\\{[^}]*\\}"), "")
        val settings = BackupCodec.decode(json).settings
        assertEquals(emptyMap<String, Long>(), settings?.quadrantTags)
    }

    @Test
    fun plainTextIsRejected() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
    }
}
