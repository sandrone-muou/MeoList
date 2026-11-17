package com.meolist.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {
    @Test
    fun dismissedFarmInfoPersistsAcrossPreferenceInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = DataStoreAppPreferences(context)
        preferences.dismissFarmInfo()
        preferences.updateSettings { it.copy(defaultReminderMinute = 10 * 60 + 15) }

        val reloaded = DataStoreAppPreferences(context)
        assertTrue(reloaded.farmInfoDismissed.first())
        assertEquals(10 * 60 + 15, reloaded.settings.first().defaultReminderMinute)
    }
    @Test
    fun navigationPersistsAndRepairsInvalidLists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = DataStoreAppPreferences(context)
        preferences.updateSettings { it.copy(bottomDestinations = listOf(
            com.meolist.domain.settings.BottomDestination.STATS,
            com.meolist.domain.settings.BottomDestination.STATS,
        )) }
        val reloaded = DataStoreAppPreferences(context).settings.first().bottomDestinations
        assertEquals(listOf(
            com.meolist.domain.settings.BottomDestination.TASKS,
            com.meolist.domain.settings.BottomDestination.TRACKER,
            com.meolist.domain.settings.BottomDestination.STATS,
            com.meolist.domain.settings.BottomDestination.MORE,
        ), reloaded)
    }
}
