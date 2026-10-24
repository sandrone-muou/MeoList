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
 * Guards the frozen schema: a database created from the checked-in v1 schema must open with the
 * current entities and expose every table, including the farm ones added during development.
 * The first real schema change adds `migrate1To2` next to this test.
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
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, MeoDatabase::class.java, databaseName).build()
        try {
            runBlocking {
                assertNotNull(db.taskDao().get(1))
                assertEquals(0, db.farmDao().getPlots().size)
                assertEquals(0, db.farmDao().getNutrients().size)
                assertEquals(0, db.farmDao().getHarvests().size)
                assertEquals(0, db.farmDao().getAchievements().size)
            }
        } finally {
            db.close()
        }
    }
}
