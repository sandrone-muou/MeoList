package com.meolist.feature.tasks.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.task.RepeatAdvance
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskItem
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import com.meolist.feature.tasks.R
import com.meolist.feature.tasks.RepeatAdvanceEffect
import com.meolist.feature.tasks.TaskRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repo: TaskRepository,
    listRepo: TaskListRepository,
    private val interactor: TaskInteractor,
    val clock: Clock,
) : ViewModel() {
    val query = MutableStateFlow("")

    val results: StateFlow<List<TaskItem>> = combine(
        query.debounce(200).flatMapLatest { repo.search(it) },
        listRepo.observeLists(),
    ) { tasks, lists ->
        val byId = lists.associateBy { it.id }
        tasks.map { TaskItem(it, it.listId?.let(byId::get), emptyList(), 0, 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastAdvance = MutableStateFlow<RepeatAdvance?>(null)
    val lastAdvance: StateFlow<RepeatAdvance?> = _lastAdvance

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch {
        interactor.setDone(id, done)?.let { _lastAdvance.value = it }
    }

    fun undoRepeat(advance: RepeatAdvance) = viewModelScope.launch { interactor.undoRepeat(advance) }

    fun consumeAdvance() {
        _lastAdvance.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(onBack: () -> Unit, onOpenTask: (Long) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val today = vm.clock.today()
    val zone = vm.clock.zone()
    val snackbar = remember { SnackbarHostState() }
    val advance by vm.lastAdvance.collectAsStateWithLifecycle()
    RepeatAdvanceEffect(advance, snackbar, today, zone, onUndo = { vm.undoRepeat(it) }, onConsumed = vm::consumeAdvance)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                stringResource(R.string.tasks_search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding())) {
            items(results, key = { it.task.id }) { item ->
                TaskRow(
                    item = item,
                    showList = true,
                    today = today,
                    zone = zone,
                    onToggle = { vm.setDone(item.task.id, it) },
                    onClick = { onOpenTask(item.task.id) },
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
