package com.meolist.feature.tasks.manage

import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.MeoScaffold
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.ColorPicker
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoPalette
import com.meolist.domain.common.Clock
import com.meolist.domain.task.ListFolder
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import com.meolist.feature.tasks.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageState(
    val lists: List<TaskList> = emptyList(),
    val folders: List<ListFolder> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@HiltViewModel
class ListManageViewModel @Inject constructor(
    private val listRepo: TaskListRepository,
    private val tagRepo: TagRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<ManageState> = combine(
        listRepo.observeLists(),
        listRepo.observeFolders(),
        tagRepo.observeTags(),
    ) { lists, folders, tags -> ManageState(lists, folders, tags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageState())

    fun saveList(list: TaskList) = viewModelScope.launch { listRepo.saveList(list.copy(name = list.name.trim())) }
    fun deleteList(id: Long) = viewModelScope.launch { listRepo.deleteList(id, clock.now()) }
    fun saveFolder(folder: ListFolder) = viewModelScope.launch { listRepo.saveFolder(folder.copy(name = folder.name.trim())) }
    fun deleteFolder(id: Long) = viewModelScope.launch { listRepo.deleteFolder(id) }
    fun saveTag(tag: Tag) = viewModelScope.launch {
        // Tag names are unique; silently ignore a duplicate name.
        runCatching { tagRepo.saveTag(tag) }
    }
    fun deleteTag(id: Long) = viewModelScope.launch { tagRepo.deleteTag(id) }
}

private sealed interface Editing {
    data class ListEdit(val list: TaskList) : Editing
    data class FolderEdit(val folder: ListFolder) : Editing
    data class TagEdit(val tag: Tag) : Editing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListManageRoute(onBack: () -> Unit, vm: ListManageViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    MeoScaffold(
        title = stringResource(R.string.tasks_manage_lists),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.tasks_add)) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_new_list)) },
                        onClick = {
                            addMenu = false
                            editing = Editing.ListEdit(TaskList(name = "", color = MeoPalette.colors[state.lists.size % MeoPalette.colors.size]))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_new_folder)) },
                        onClick = { addMenu = false; editing = Editing.FolderEdit(ListFolder(name = "")) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_new_tag)) },
                        onClick = {
                            addMenu = false
                            editing = Editing.TagEdit(Tag(name = "", color = MeoPalette.colors[(state.tags.size + 3) % MeoPalette.colors.size]))
                        },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = com.meolist.core.theme.Meo.spacing.xs,
                vertical = com.meolist.core.theme.Meo.spacing.xs,
            ),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val allEmpty = state.lists.isEmpty() && state.folders.isEmpty() && state.tags.isEmpty()
            if (allEmpty) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.List,
                        title = stringResource(R.string.tasks_manage_empty_title),
                        body = stringResource(R.string.tasks_manage_empty_body),
                        primary = stringResource(R.string.tasks_new_list) to { addMenu = true },
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
                return@LazyColumn
            }
            item { Header(stringResource(R.string.tasks_drawer_lists)) }
            if (state.lists.isEmpty()) item { Hint(stringResource(R.string.tasks_no_lists)) }
            items(state.lists, key = { "l${it.id}" }) { list ->
                val folder = state.folders.firstOrNull { it.id == list.folderId }
                ListItem(
                    headlineContent = { Text(list.name) },
                    supportingContent = {
                        val parts = listOfNotNull(folder?.name, if (list.archived) stringResource(R.string.tasks_archived) else null)
                        if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                    },
                    leadingContent = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ColorDot(list.color, size = 14.dp) } },
                    modifier = Modifier.clickable { editing = Editing.ListEdit(list) },
                )
            }
            item { Header(stringResource(R.string.tasks_folders)) }
            if (state.folders.isEmpty()) item { Hint(stringResource(R.string.tasks_no_folders)) }
            items(state.folders, key = { "f${it.id}" }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    leadingContent = { Icon(Icons.Outlined.Folder, null) },
                    modifier = Modifier.clickable { editing = Editing.FolderEdit(folder) },
                )
            }
            item { Header(stringResource(R.string.tasks_drawer_tags)) }
            if (state.tags.isEmpty()) item { Hint(stringResource(R.string.tasks_no_tags)) }
            items(state.tags, key = { "t${it.id}" }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    leadingContent = { Icon(Icons.Outlined.Tag, null, tint = Color(tag.color)) },
                    modifier = Modifier.clickable { editing = Editing.TagEdit(tag) },
                )
            }
        }
    }

    when (val e = editing) {
        is Editing.ListEdit -> ListEditDialog(
            initial = e.list,
            folders = state.folders,
            onSave = vm::saveList,
            onDelete = { vm.deleteList(e.list.id) },
            onDismiss = { editing = null },
        )
        is Editing.FolderEdit -> NameColorDialog(
            title = stringResource(if (e.folder.id == 0L) R.string.tasks_new_folder else R.string.tasks_edit_folder),
            initialName = e.folder.name,
            initialColor = null,
            deleteMessage = stringResource(R.string.tasks_delete_folder_message),
            canDelete = e.folder.id != 0L,
            onSave = { name, _ -> vm.saveFolder(e.folder.copy(name = name)) },
            onDelete = { vm.deleteFolder(e.folder.id) },
            onDismiss = { editing = null },
        )
        is Editing.TagEdit -> NameColorDialog(
            title = stringResource(if (e.tag.id == 0L) R.string.tasks_new_tag else R.string.tasks_edit_tag),
            initialName = e.tag.name,
            initialColor = e.tag.color,
            deleteMessage = stringResource(R.string.tasks_delete_tag_message),
            canDelete = e.tag.id != 0L,
            onSave = { name, color -> vm.saveTag(e.tag.copy(name = name, color = color ?: e.tag.color)) },
            onDelete = { vm.deleteTag(e.tag.id) },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ListEditDialog(
    initial: TaskList,
    folders: List<ListFolder>,
    onSave: (TaskList) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var list by remember { mutableStateOf(initial) }
    var folderMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.tasks_new_list else R.string.tasks_edit_list)) },
        text = {
            Column {
                OutlinedTextField(
                    value = list.name,
                    onValueChange = { list = list.copy(name = it) },
                    label = { Text(stringResource(R.string.tasks_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPicker(list.color, { list = list.copy(color = it) }, Modifier.padding(vertical = 12.dp))
                Box {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.tasks_folder)) },
                        trailingContent = {
                            Text(folders.firstOrNull { it.id == list.folderId }?.name ?: stringResource(R.string.tasks_none))
                        },
                        modifier = Modifier.clickable { folderMenu = true },
                    )
                    DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_none)) },
                            onClick = { list = list.copy(folderId = null); folderMenu = false },
                        )
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name) },
                                onClick = { list = list.copy(folderId = folder.id); folderMenu = false },
                            )
                        }
                    }
                }
                if (initial.id != 0L) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tasks_archive_list), Modifier.weight(1f))
                        Switch(checked = list.archived, onCheckedChange = { list = list.copy(archived = it) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(list); onDismiss() }, enabled = list.name.isNotBlank()) {
                Text(stringResource(com.meolist.core.R.string.core_save))
            }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(com.meolist.core.R.string.core_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_delete_list),
            text = stringResource(R.string.tasks_delete_list_message),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun NameColorDialog(
    title: String,
    initialName: String,
    initialColor: Int?,
    deleteMessage: String,
    canDelete: Boolean,
    onSave: (String, Int?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tasks_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                color?.let { c -> ColorPicker(c, { color = it }, Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color); onDismiss() }, enabled = name.isNotBlank()) {
                Text(stringResource(com.meolist.core.R.string.core_save))
            }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(com.meolist.core.R.string.core_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = title,
            text = deleteMessage,
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}
