package com.meolist.feature.tasks

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.TaskCreator
import com.meolist.domain.task.TaskDraft
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TaskCreateState(
    val lists: List<TaskList> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class TaskCreateViewModel @Inject constructor(
    lists: TaskListRepository,
    tags: TagRepository,
    private val creator: TaskCreator,
    clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    val today = clock.today()
    val date = savedState.get<Long>(Routes.ARG_DAY)?.takeIf { it != Routes.NO_DAY }
        ?.let(LocalDate::ofEpochDay) ?: today
    val state = combine(lists.observeLists(), tags.observeTags()) { allLists, allTags ->
        TaskCreateState(allLists.filterNot { it.archived }, allTags, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskCreateState())

    suspend fun add(draft: TaskDraft) { creator.create(draft) }
}

@Composable
fun TaskCreateRoute(onBack: () -> Unit, vm: TaskCreateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = currentLocale()
    BackHandler(busy) { }
    MeoScaffold(title = stringResource(R.string.tasks_new), onBack = { if (!busy) onBack() }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loaded) {
                TaskEntryForm(
                    defaults = QuickAddDefaults(vm.date, null, emptySet()),
                    lists = state.lists,
                    tags = state.tags,
                    today = vm.today,
                    onAdd = vm::add,
                    showHeading = false,
                    onBusy = { busy = it },
                    onSaved = { date ->
                        val message = if (date == null) context.getString(R.string.tasks_created_no_date)
                            else context.getString(R.string.tasks_created_for_date, longDate(date, locale))
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
