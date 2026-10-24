package com.meolist.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meolist.data.db.MeoDatabase
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
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Writes a little of everything, snapshots it, wipes it and restores it. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private lateinit var db: MeoDatabase
    private lateinit var store: BackupStore

    private val clock = object : Clock {
        override fun now(): Long = 1_700_000_000_000
        override fun zone(): ZoneId = ZoneId.of("Asia/Shanghai")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeoDatabase::class.java).build()
        store = BackupStore(
            db = db,
            taskDao = db.taskDao(),
            listDao = db.taskListDao(),
            tagDao = db.tagDao(),
            reminderDao = db.reminderDao(),
            typeDao = db.activityTypeDao(),
            recordDao = db.timeRecordDao(),
            habitDao = db.habitDao(),
            countdownDao = db.countdownDao(),
            farmDao = db.farmDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        val recordStart = java.time.LocalDateTime.of(2026, 10, 24, 8, 0)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        db.taskListDao().upsertFolder(ListFolderEntity(1, "Work", 1))
        db.taskListDao().upsertList(TaskListEntity(2, 1, "Project", 0x3B6939, 1, 1, false))
        db.tagDao().upsert(TagEntity(3, "home", 0xE53935))
        db.taskDao().insertAll(
            listOf(
                TaskEntity(4, 2, null, "Plan", "note", 2, 0, null, null, null, true, null, false, null, 1, 10, 10, null),
                TaskEntity(5, 2, 4, "Draft", "", 0, 0, null, null, null, true, null, false, null, 2, 10, 10, null),
            ),
        )
        db.taskDao().insertTags(listOf(TaskTagEntity(4, 3)))
        db.reminderDao().insertAll(listOf(ReminderEntity(6, 0, 4, 1, 10, null, 2_000_000, null, true)))
        db.activityTypeDao().insertTypes(listOf(ActivityTypeEntity(1, "Work", "timer", 0, 1, false)))
        db.activityTypeDao().insertGoals(listOf(ActivityGoalEntity(1, 0, 3600)))
        db.activityTypeDao().insertGoalReached(GoalReachedEntity(1, 0, 20_100, 20_200))
        db.timeRecordDao().insertRunning(RunningTimerEntity(7, 1, 4, 30, "running"))
        db.timeRecordDao().insertRecord(RecordEntity(8, 1, 4, recordStart, recordStart + 30 * 60_000, "deep work", false))
        db.habitDao().insertHabit(HabitEntity(9, "Read", "read", 0, "daily", 1, "", 20_000, false, 1, 0))
        db.habitDao().upsertCheckIn(HabitCheckInEntity(9, 20_000, 1))
        db.countdownDao().insert(CountdownEntity(10, "Trip", 20_500, 0, false, false, false, 0, "", 1, 0))
        db.farmDao().insertPlot(FarmPlotEntity(11, 0, "carrot", 60, null))
        db.farmDao().upsertNutrients(listOf(NutrientEntity("record:8", 11, 30, recordStart)))
        db.farmDao().insertHarvest(HarvestEntity(12, "carrot", 11, 300, 180))
        db.farmDao().insertAchievement(AchievementEntity("focus_1h", 400))
    }

    @Test
    fun snapshotRestoreKeepsEveryLink() = runBlocking {
        seed()

        val json = BackupCodec.encode(store.snapshot().copy(settings = SettingsDto(true, 60, 1, 1, true, 540)))
        // Change things after the backup was taken.
        db.taskDao().insert(
            TaskEntity(99, null, null, "After backup", "", 0, 0, null, null, null, true, null, false, null, 1, 0, 0, null),
        )
        db.timeRecordDao().deleteRecord(8)

        val restored = BackupCodec.decode(json)
        store.restore(restored)

        assertNull(db.taskDao().get(99))
        assertEquals(listOf(4L, 5L), db.taskDao().getAllIncludingDeleted().map { it.id })
        assertEquals(4L, db.taskDao().get(5)?.parentId)
        assertEquals(setOf(3L), db.taskDao().getTagIds(4).toSet())
        assertEquals(1, db.reminderDao().getAll().size)
        assertEquals(1, db.activityTypeDao().getTypes().size)
        assertEquals(1, db.timeRecordDao().getAllRecords().size)
        assertEquals(1, db.timeRecordDao().getRunning().size)
        assertEquals(1, db.habitDao().getAllCheckIns().size)
        assertEquals("carrot", db.farmDao().getPlots().single().crop)
        assertEquals(11L, db.farmDao().getNutrients().single().plotId)
        assertEquals(1, db.farmDao().getHarvests().size)
        assertEquals(400L, db.farmDao().getAchievements().single().unlockedAt)

        val csv = store.recordsCsv()
        assertTrue(csv.contains("2026-10-24"))
        assertTrue(csv.contains(",30,Work,Plan,"))
        assertTrue(csv.contains("deep work"))
    }
}
