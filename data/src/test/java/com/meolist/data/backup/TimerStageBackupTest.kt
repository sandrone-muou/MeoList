package com.meolist.data.backup

import com.meolist.domain.backup.BackupFormatException
import com.meolist.domain.settings.BottomDestination
import org.junit.Assert.*
import org.junit.Test

class TimerStageBackupTest {
    private fun sample() = BackupFile(
        format = 1,
        activityTypes = listOf(ActivityTypeDto(1, "Study", "timer", 1, 1, false)),
        subprojects = listOf(SubprojectDto(2, 1, "Reading", 2, 1, true)),
        runningTimers = listOf(RunningTimerDto(3, 1, null, 1000, "")),
        records = listOf(RecordDto(4, 1, null, 1000, 3000, "", false)),
        timerStages = listOf(
            TimerStageDto(runningId = 3, position = 1, subprojectId = 2, name = "Original", startAt = 1000, endAt = 2000),
            TimerStageDto(runningId = 3, position = 2, startAt = 2000),
            TimerStageDto(runningId = 3, position = 3, subprojectId = 2, name = "Future"),
            TimerStageDto(recordId = 4, position = 1, subprojectId = 2, name = "Original", startAt = 1000, endAt = 3000),
        ),
        settings = SettingsDto(true, 60, 1, 1, true, 540, bottomDestinations = listOf("tracker", "tasks", "stats", "more")),
    )

    @Test fun keepsActivePlanHistorySnapshotsAndNavigation() {
        val file = sample()
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        assertEquals(file, decoded)
        assertEquals(listOf(BottomDestination.TRACKER, BottomDestination.TASKS, BottomDestination.STATS, BottomDestination.MORE), decoded.settings!!.toSettings().bottomDestinations)
    }

    @Test fun legacyWithoutNewFieldsHasNoInventedStages() {
        val file = BackupCodec.decode("""{"format":1}""")
        assertTrue(file.subprojects.isEmpty()); assertTrue(file.timerStages.isEmpty())
    }

    @Test fun rejectsMissingOwnersDuplicatePositionsGapsAndOverlappingStages() {
        val file = sample()
        val bad = listOf(
            file.copy(timerStages = listOf(file.timerStages.first().copy(runningId = 999))),
            file.copy(timerStages = file.timerStages + file.timerStages.first()),
            file.copy(timerStages = listOf(file.timerStages.last().copy(startAt = 1200))),
            file.copy(timerStages = listOf(file.timerStages.last().copy(endAt = 4000))),
            file.copy(timerStages = listOf(file.timerStages.first().copy(recordId = 4))),
        )
        bad.forEach { assertThrows(BackupFormatException::class.java) { BackupCodec.decode(BackupCodec.encode(it)) } }
    }
}
