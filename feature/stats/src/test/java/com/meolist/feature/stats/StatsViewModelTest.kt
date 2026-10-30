package com.meolist.feature.stats

import androidx.lifecycle.viewModelScope
import com.meolist.domain.common.Clock
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.AppSettings
import com.meolist.domain.settings.StatsDefaultView
import com.meolist.domain.stats.StatsPeriod
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimeRecordRepository
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<StatsViewModel>()
    private val today = LocalDate.of(2026, 10, 27)
    private val clock = object : Clock {
        override fun now() = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        override fun zone() = ZoneOffset.UTC
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun waitsForPreferencesWithoutEmittingAnIncorrectWeek() = runTest {
        val prefs = Preferences()
        val vm = model(prefs)
        val seen = mutableListOf<StatsState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        runCurrent()
        assertTrue(vm.state.value.isLoading)
        assertTrue(seen.all { it.isLoading })

        prefs.publish(AppSettings(statsDefaultView = StatsDefaultView.YEAR))
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertEquals(StatsPeriod.YEAR, vm.state.value.period)
        assertTrue(seen.filterNot { it.isLoading }.all { it.period == StatsPeriod.YEAR })
    }

    @Test
    fun everyDefaultAndRememberedPeriodStartsAtToday() = runTest {
        val cases = listOf(
            StatsDefaultView.DAY to StatsPeriod.DAY,
            StatsDefaultView.WEEK to StatsPeriod.WEEK,
            StatsDefaultView.MONTH to StatsPeriod.MONTH,
            StatsDefaultView.YEAR to StatsPeriod.YEAR,
        ) + StatsPeriod.entries.map { StatsDefaultView.LAST_USED to it }
        cases.forEach { (default, expected) ->
            val last = if (default == StatsDefaultView.LAST_USED) expected
                else StatsPeriod.entries.first { it != expected }
            val prefs = Preferences(AppSettings(statsDefaultView = default, lastStatsPeriod = last))
            val vm = model(prefs)
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            runCurrent()
            assertEquals(expected, vm.state.value.period)
            assertTrue(today in vm.state.value.range)
            assertTrue(prefs.writes.isEmpty())
            job.cancel()
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun rapidChangesUpdateUiWhileWritesAreBlockedAndPersistInOrder() = runTest {
        val prefs = Preferences(AppSettings())
        val vm = model(prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        prefs.writeGate = gate
        val choices = listOf(StatsPeriod.DAY, StatsPeriod.MONTH, StatsPeriod.YEAR)
        choices.forEach(vm::setPeriod)
        runCurrent()
        assertEquals(StatsPeriod.YEAR, vm.state.value.period)
        assertEquals(listOf(StatsPeriod.DAY), prefs.started)
        assertTrue(prefs.writes.isEmpty())

        gate.complete(Unit)
        runCurrent()
        assertEquals(choices, prefs.writes)
        assertEquals(StatsPeriod.YEAR, prefs.current.lastStatsPeriod)
        assertEquals(StatsPeriod.YEAR, vm.state.value.period)
    }

    @Test
    fun periodSelectionDoesNotWaitForRecordQueries() = runTest {
        val prefs = Preferences(AppSettings(statsDefaultView = StatsDefaultView.MONTH))
        val vm = model(prefs, emptyFlow())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        assertEquals(StatsPeriod.MONTH, vm.state.value.period)
        vm.setPeriod(StatsPeriod.DAY)
        runCurrent()
        assertEquals(StatsPeriod.DAY, vm.state.value.period)
        assertEquals(today, vm.state.value.range.start)
        assertEquals(StatsPeriod.DAY, prefs.current.lastStatsPeriod)
    }

    @Test
    fun preferenceWriteBackDoesNotResetHistoricalRangeOrActivityFilter() = runTest {
        val prefs = Preferences(AppSettings())
        val vm = model(prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        vm.move(-3)
        runCurrent()
        vm.selectType(7L)
        runCurrent()
        val before = vm.state.value
        prefs.publish(prefs.current.copy(
            statsDefaultView = StatsDefaultView.YEAR,
            lastStatsPeriod = StatsPeriod.DAY,
            ignoreShortRecordSec = 30,
        ))
        runCurrent()
        assertEquals(before.range, vm.state.value.range)
        assertEquals(before.period, vm.state.value.period)
        assertEquals(7L, vm.state.value.selectedType)
    }

    @Test
    fun returningToLiveViewModelRetainsBrowsingAfterCollectionStops() = runTest {
        val prefs = Preferences(AppSettings())
        val vm = model(prefs)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        vm.move(-2)
        vm.selectType(7L)
        runCurrent()
        val before = vm.state.value
        job.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        prefs.publish(prefs.current.copy(statsDefaultView = StatsDefaultView.DAY))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        assertEquals(before.range, vm.state.value.range)
        assertEquals(7L, vm.state.value.selectedType)
    }

    @Test
    fun newViewModelRemembersPeriodButNotHistoricalAnchor() = runTest {
        val prefs = Preferences(AppSettings(statsDefaultView = StatsDefaultView.LAST_USED))
        val first = model(prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.state.collect {} }
        runCurrent()
        first.setPeriod(StatsPeriod.MONTH)
        first.move(-4)
        runCurrent()
        assertFalse(today in first.state.value.range)
        first.viewModelScope.cancel()

        val reopened = model(prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reopened.state.collect {} }
        runCurrent()
        assertEquals(StatsPeriod.MONTH, reopened.state.value.period)
        assertTrue(today in reopened.state.value.range)
    }

    private fun model(
        prefs: Preferences,
        recordFlow: Flow<List<TimeRecord>> = flowOf(emptyList()),
    ) = StatsViewModel(
        records = repository<TimeRecordRepository>(
            "observeRecords" to recordFlow,
            "observeRunning" to flowOf(emptyList<RunningTimer>()),
        ),
        types = repository<ActivityTypeRepository>(
            "observeTypes" to flowOf(listOf(ActivityType(7L, "Reading", "read", 0))),
        ),
        tasks = repository<TaskRepository>("observeAll" to flowOf(emptyList<Task>())),
        prefs = prefs,
        clock = clock,
    ).also(models::add)

    private class Preferences(initial: AppSettings? = null) : AppPreferences {
        private val values = MutableStateFlow(initial)
        override val settings = values.filterNotNull()
        val current get() = requireNotNull(values.value)
        val started = mutableListOf<StatsPeriod>()
        val writes = mutableListOf<StatsPeriod>()
        var writeGate: CompletableDeferred<Unit>? = null
        fun publish(value: AppSettings) { values.value = value }
        override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
            started += transform(current).lastStatsPeriod
            writeGate?.await()
            publish(transform(current))
            writes += current.lastStatsPeriod
        }
        override val lastTaskFilter = flowOf<String?>(null)
        override suspend fun setLastTaskFilter(key: String) = Unit
    }

    private inline fun <reified T> repository(vararg results: Pair<String, Any>): T {
        val methods = results.toMap()
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            methods[method.name] ?: error("Unexpected repository call: ${method.name}")
        } as T
    }
}
