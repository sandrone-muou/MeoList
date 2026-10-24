package com.meolist.feature.farm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.FarmInteractor
import com.meolist.domain.farm.FarmSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    farm: FarmInteractor,
) : ViewModel() {
    val state: StateFlow<FarmSnapshot> = farm.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FarmSnapshot())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsRoute(
    onBack: () -> Unit,
    vm: AchievementsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val unlocked = state.achievements.filter { it.unlocked }
    val pending = state.achievements.filterNot { it.unlocked }
        .sortedByDescending { it.fraction }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.farm_achievements_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    R.string.farm_achievements_summary,
                                    unlocked.size,
                                    Achievement.entries.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.farm_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
            if (unlocked.isNotEmpty()) {
                item { AchievementHeader(stringResource(R.string.farm_achievements_unlocked)) }
                item { AchievementGroup(unlocked) }
            }
            if (pending.isNotEmpty()) {
                item { AchievementHeader(stringResource(R.string.farm_achievements_pending)) }
                item { AchievementGroup(pending) }
            }
        }
    }
}

@Composable
private fun AchievementHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun AchievementGroup(items: List<com.meolist.domain.farm.AchievementState>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { index, achievement ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                AchievementRow(achievement, Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}
