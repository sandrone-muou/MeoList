package com.meolist.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meolist.data.db.entity.ActivityTypeEntity
import com.meolist.data.repo.TimeRecordRepositoryImpl
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimerStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerStageStorageTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), MeoDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationKeepsVersionTwoRecordsAndRunningTimers() {
        val name = "timer-stage-migration.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO activity_type VALUES (1, 'Study', 'timer', 1, 0, 0)")
            execSQL("INSERT INTO record VALUES (1, 1, NULL, 1000, 5000, 'history', 0)")
            execSQL("INSERT INTO running_timer VALUES (2, 1, NULL, 6000, 'running')")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT startAt, endAt, comment FROM record WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals(1000L, it.getLong(0)); assertEquals(5000L, it.getLong(1)); assertEquals("history", it.getString(2))
            }
            db.query("SELECT startedAt FROM running_timer WHERE id=2").use { assertTrue(it.moveToFirst()); assertEquals(6000L, it.getLong(0)) }
            db.query("SELECT count(*) FROM timer_stage").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun stagesRoundTripUpdateCascadeAndRestoreWithNewParent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MeoDatabase::class.java).build()
        try {
            db.activityTypeDao().insertType(ActivityTypeEntity(1, "Study", "timer", 1, 0, false))
            val repo = TimeRecordRepositoryImpl(db.timeRecordDao())
            val stages = listOf(TimerStage(1, 42, "Reading snapshot", 2, 1000, 2000), TimerStage(2, startAt = 2000))
            val runningId = repo.insertRunning(RunningTimer(typeId = 1, startedAt = 1000, stages = stages))
            assertEquals(stages, repo.getRunning(runningId)!!.stages)
            repo.updateRunning(repo.getRunning(runningId)!!.copy(stages = stages + TimerStage(3)))
            assertEquals(3, repo.getRunningByType(1)!!.stages.size)
            repo.deleteRunning(runningId)
            assertTrue(db.timeRecordDao().getStages().isEmpty())

            val recordId = repo.insertRecord(TimeRecord(typeId = 1, startAt = 1000, endAt = 2000, stages = stages.take(1)))
            val snapshot = repo.getRecord(recordId)!!
            repo.deleteRecord(recordId)
            assertTrue(db.timeRecordDao().getStages().isEmpty())
            val restoredId = repo.insertRecord(snapshot.copy(id = 0))
            assertNotEquals(recordId, restoredId)
            assertEquals(snapshot.stages, repo.getRecord(restoredId)!!.stages)
            db.activityTypeDao().deleteType(1)
            assertTrue(db.timeRecordDao().getStages().isEmpty())
        } finally { db.close() }
    }
}
