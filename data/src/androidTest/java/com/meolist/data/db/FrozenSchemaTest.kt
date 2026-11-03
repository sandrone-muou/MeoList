package com.meolist.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the v1 schema and verifies that the v1 -> v2 migration retains both task data and the old
 * farm rows for the first-use farm rebuild.
 */
@RunWith(AndroidJUnit4::class)
class FrozenSchemaTest {
    private val databaseName = "frozen-schema-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun schemaVersionOneOpensWithCurrentEntities() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO task (id, title, note, priority, status, allDay, repeatFromCompletion, sortOrder, createdAt, updatedAt) " +
                    "VALUES (1, 'from v1', '', 0, 0, 1, 0, 1, 1, 1)",
            )
            execSQL("INSERT INTO farm_plot (id, slot, crop, plantedAt, harvestedAt) VALUES (1, 0, 'carrot', 1, NULL)")
            execSQL("INSERT INTO farm_nutrient (sourceKey, plotId, amount, earnedAt) VALUES ('record:1', 1, 60, 1)")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, MeoDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            runBlocking {
                assertNotNull(db.taskDao().get(1))
                assertEquals(1, db.farmDao().getPlots().size)
                assertEquals(0L, db.farmDao().getNutrients().single().sequence)
                assertEquals(null, db.farmDao().getFarmState())
                assertEquals(0, db.farmDao().getFields().size)
                assertEquals(0, db.farmDao().getHarvests().size)
                assertEquals(0, db.farmDao().getAchievements().size)
            }
        } finally {
            db.close()
        }
    }
}
