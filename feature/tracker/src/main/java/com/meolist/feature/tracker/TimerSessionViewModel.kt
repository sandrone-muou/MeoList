package com.meolist.feature.tracker

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.tracker.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TimerSessionState(
    val loaded: Boolean = false,
    val type: ActivityType? = null,
    val projects: List<ActivitySubproject> = emptyList(),
    val timer: RunningTimer? = null,
    val record: TimeRecord? = null,
    val draft: List<Long?> = emptyList(),
    val busy: Boolean = false,
    val message: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimerSessionViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val types: ActivityTypeRepository,
    private val records: TimeRecordRepository,
    private val timers: TimerInteractor,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val id: Long = requireNotNull(handle[Routes.ARG_ID])
    val finished: Boolean = handle[Routes.ARG_FINISHED] ?: false
    private val local = MutableStateFlow(TimerSessionState(
        draft = (handle.get<LongArray>("plan") ?: longArrayOf()).map { it.takeIf { value -> value > 0 } },
    ))
    private val record = if (finished) records.observeRecord(id) else flowOf<TimeRecord?>(null)
    private val typeId = if (finished) record.map { it?.typeId }.distinctUntilChanged()
        else flowOf(id)

    val state: StateFlow<TimerSessionState> = typeId.flatMapLatest { typeId ->
        combine(types.observeTypes(), types.observeSubprojects(typeId ?: 0), records.observeRunning(), record, local) {
            allTypes, projects, running, saved, ui ->
            ui.copy(loaded = true, type = allTypes.find { it.id == typeId }, projects = projects,
                timer = if (finished) null else running.find { it.typeId == typeId },
                record = saved)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun now(): Long = clock.now()
    fun consumeMessage() { local.update { it.copy(message = null) } }

    private fun action(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        appScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("MeoList", "timer session change failed", error)
                local.update { it.copy(message = R.string.tracker_session_failed) }
            }
            finally { local.update { it.copy(busy = false) } }
        }
    }

    fun start() = action {
        val type = state.value.type ?: return@action
        if (!type.archived) {
            timers.start(type.id, stagePlan = local.value.draft)
            withContext(Dispatchers.Main.immediate) { handle["plan"] = longArrayOf() }
            local.update { it.copy(draft = emptyList()) }
        }
    }

    fun mark() = action {
        state.value.timer?.let {
            if (!timers.mark(it.id)) local.update { ui -> ui.copy(message = R.string.tracker_mark_too_soon) }
        }
    }

    fun stop(onStopped: (Long?) -> Unit) = action {
        val timer = state.value.timer ?: return@action
        val recordId = timers.stop(timer.id)
        withContext(Dispatchers.Main.immediate) { onStopped(recordId) }
    }

    fun assign(position: Int, projectId: Long?) {
        val ui = state.value
        when {
            ui.record != null -> action { timers.assignRecordStage(ui.record.id, position, projectId) }
            ui.timer != null -> action { timers.assignRunningStage(ui.timer.id, position, projectId) }
            !finished -> {
                val plan = local.value.draft.toMutableList()
                while (plan.size < position) plan += null
                plan[position - 1] = projectId
                handle["plan"] = plan.map { it ?: -1L }.toLongArray()
                local.update { it.copy(draft = plan) }
            }
        }
    }

    fun addStage() {
        val ui = state.value
        assign((ui.timer?.stages?.maxOfOrNull { it.position } ?: ui.draft.size) + 1, null)
    }

    fun removeStage(position: Int) {
        val timer = state.value.timer
        if (timer != null) action { timers.removePlannedStage(timer.id, position) }
        else if (!finished) {
            val plan = local.value.draft.filterIndexed { index, _ -> index != position - 1 }
            handle["plan"] = plan.map { it ?: -1L }.toLongArray()
            local.update { it.copy(draft = plan) }
        }
    }

    fun saveProject(project: ActivitySubproject) = action { timers.saveSubproject(project) }

    fun moveProject(project: ActivitySubproject, delta: Int) = action {
        timers.moveSubproject(project.id, delta)
    }
}
