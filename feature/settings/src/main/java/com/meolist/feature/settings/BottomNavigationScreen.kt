package com.meolist.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.navigation.bottomDestinationIcon
import com.meolist.core.navigation.bottomDestinationLabel
import com.meolist.core.theme.Meo
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.domain.settings.BottomDestination
import com.meolist.domain.settings.BottomNavigation

@Composable
fun BottomNavigationRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected = state.settings.bottomDestinations
    fun move(item: BottomDestination, delta: Int) {
        val list = selected.toMutableList()
        val from = list.indexOf(item)
        val to = from + delta
        if (from < 0 || item == BottomDestination.MORE || to !in 0 until list.lastIndex) return
        list[from] = list[to]
        list[to] = item
        vm.setBottomDestinations(list)
    }
    MeoScaffold(title = stringResource(R.string.settings_bottom_navigation), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("bottom-settings-list"),
            contentPadding = PaddingValues(Meo.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
        ) {
            item {
                Text(stringResource(R.string.settings_bottom_navigation_hint, selected.size), style = MaterialTheme.typography.bodyMedium)
            }
            items(selected, key = { it.key }) { item ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(Meo.spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(bottomDestinationIcon(item), null)
                            Spacer(Modifier.width(Meo.spacing.sm))
                            Text(bottomDestinationLabel(item), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            if (item.required) Icon(Icons.Outlined.Lock, stringResource(R.string.settings_bottom_required))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { move(item, -1) }, enabled = item != BottomDestination.MORE && selected.indexOf(item) > 0) {
                                Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.settings_bottom_move_up))
                            }
                            IconButton(onClick = { move(item, 1) }, enabled = item != BottomDestination.MORE && selected.indexOf(item) < selected.lastIndex - 1) {
                                Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.settings_bottom_move_down))
                            }
                            if (!item.required) TextButton(onClick = { vm.removeBottomDestination(item) }) {
                                Text(stringResource(R.string.settings_bottom_remove))
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.settings_bottom_available), style = MaterialTheme.typography.titleMedium) }
            items(BottomDestination.entries.filter { !it.required && it !in selected }, key = { it.key }) { item ->
                OutlinedButton(
                    onClick = { vm.addBottomDestination(item) },
                    enabled = selected.size < BottomNavigation.MAX_ITEMS,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(bottomDestinationIcon(item), null)
                    Spacer(Modifier.width(Meo.spacing.sm))
                    Text(bottomDestinationLabel(item))
                }
            }
            item { TextButton(onClick = { vm.setBottomDestinations(BottomNavigation.defaults) }) {
                Text(stringResource(R.string.settings_bottom_reset))
            } }
        }
    }
}
