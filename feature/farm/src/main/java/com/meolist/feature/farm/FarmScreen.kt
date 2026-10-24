package com.meolist.feature.farm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.shortDate
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.AchievementMetric
import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.Crop
import com.meolist.domain.farm.CropUnlock
import com.meolist.domain.farm.FarmInteractor
import com.meolist.domain.farm.FarmRules
import com.meolist.domain.farm.FarmSnapshot
import com.meolist.domain.farm.HarvestRecord
import com.meolist.domain.farm.HarvestResult
import com.meolist.domain.farm.PlantResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FarmEvent {
    data class CompostApplied(val amount: Int) : FarmEvent
    data object SlotTaken : FarmEvent
    data object CropLocked : FarmEvent
    data object NotReady : FarmEvent
}

data class FarmUiState(
    val snapshot: FarmSnapshot = FarmSnapshot(),
    val loaded: Boolean = false,
    val event: FarmEvent? = null,
    val harvest: HarvestRecord? = null,
)

@HiltViewModel
class FarmViewModel @Inject constructor(
    private val farm: FarmInteractor,
) : ViewModel() {
    private val local = MutableStateFlow(FarmUiState())

    val state: StateFlow<FarmUiState> = combine(farm.observe(), local) { snapshot, ui ->
        ui.copy(snapshot = snapshot, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FarmUiState())

    init {
        viewModelScope.launch { farm.sync() }
    }

    fun plant(slot: Int, crop: Crop, useCompost: Boolean) = viewModelScope.launch {
        when (val result = farm.plant(slot, crop, useCompost)) {
            is PlantResult.Planted -> if (result.compostUsed > 0) {
                local.update { it.copy(event = FarmEvent.CompostApplied(result.compostUsed)) }
            }
            PlantResult.SlotTaken -> local.update { it.copy(event = FarmEvent.SlotTaken) }
            PlantResult.SlotLocked, PlantResult.CropLocked ->
                local.update { it.copy(event = FarmEvent.CropLocked) }
        }
    }

    fun harvest(plotId: Long) = viewModelScope.launch {
        when (val result = farm.harvest(plotId)) {
            is HarvestResult.Harvested -> local.update { it.copy(harvest = result.record) }
            HarvestResult.NotReady -> local.update { it.copy(event = FarmEvent.NotReady) }
            HarvestResult.Empty -> Unit
        }
    }

    fun applyCompost(plotId: Long) = viewModelScope.launch {
        val moved = farm.applyCompost(plotId)
        if (moved > 0) local.update { it.copy(event = FarmEvent.CompostApplied(moved)) }
    }

    fun dismissHarvest() = local.update { it.copy(harvest = null) }

    fun consumeEvent() = local.update { it.copy(event = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmRoute(
    onOpenAchievements: () -> Unit,
    vm: FarmViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var plantingSlot by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.event) {
        when (val event = state.event) {
            null -> Unit
            is FarmEvent.CompostApplied -> {
                snackbar.showSnackbar(context.getString(R.string.farm_compost_applied, event.amount))
                vm.consumeEvent()
            }
            FarmEvent.SlotTaken -> {
                snackbar.showSnackbar(context.getString(R.string.farm_slot_taken))
                vm.consumeEvent()
            }
            FarmEvent.CropLocked -> {
                snackbar.showSnackbar(context.getString(R.string.farm_crop_locked_msg))
                vm.consumeEvent()
            }
            FarmEvent.NotReady -> {
                snackbar.showSnackbar(context.getString(R.string.farm_not_ready))
                vm.consumeEvent()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.farm_title)) },
                actions = {
                    IconButton(onClick = onOpenAchievements) {
                        Icon(Icons.Outlined.EmojiEvents, stringResource(R.string.farm_achievements_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val snapshot = state.snapshot
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item { FarmSummaryCard(snapshot) }
            item {
                SectionHeader(
                    title = stringResource(R.string.farm_section_plots),
                    trailing = stringResource(
                        R.string.farm_plots_count,
                        snapshot.activePlots.size,
                        snapshot.plotLimit,
                    ),
                )
            }
            item {
                Box(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val byslot = snapshot.activePlots.associateBy { it.slot }
                        (0 until FarmRules.MAX_PLOTS).chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { slot ->
                                    Box(Modifier.weight(1f)) {
                                        if (slot >= snapshot.plotLimit) {
                                            LockedSlotCard(slot)
                                        } else {
                                            val plot = byslot[slot]
                                            if (plot == null) {
                                                EmptySlotCard(onClick = { plantingSlot = slot })
                                            } else {
                                                PlotCard(
                                                    plot = plot,
                                                    growth = snapshot.growth(plot),
                                                    stage = FarmRules.stage(plot, snapshot.entries),
                                                    compost = snapshot.compost,
                                                    onHarvest = { vm.harvest(plot.id) },
                                                    onFertilize = { vm.applyCompost(plot.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item { CompostCard(snapshot) }
            item {
                SectionHeader(
                    title = stringResource(R.string.farm_achievements_title),
                    trailing = stringResource(
                        R.string.farm_achievements_summary,
                        snapshot.unlockedCount(),
                        Achievement.entries.size,
                    ),
                    onClick = onOpenAchievements,
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        val preview = snapshot.achievements
                            .sortedWith(compareByDescending<AchievementState> { it.unlocked }.thenByDescending { it.progress.toFloat() / it.achievement.target })
                            .take(3)
                        preview.forEachIndexed { index, achievement ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            AchievementRow(achievement, Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
            item {
                SectionHeader(title = stringResource(R.string.farm_harvests_title))
            }
            item {
                if (snapshot.harvests.isEmpty()) {
                    Text(
                        stringResource(R.string.farm_no_harvests),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                } else {
                    HarvestList(snapshot.harvests.take(5))
                }
            }
        }
    }

    plantingSlot?.let { slot ->
        PlantDialog(
            snapshot = state.snapshot,
            onPlant = { crop, useCompost ->
                vm.plant(slot, crop, useCompost)
                plantingSlot = null
            },
            onDismiss = { plantingSlot = null },
        )
    }

    state.harvest?.let { record ->
        HarvestDialog(record = record, onDismiss = vm::dismissHarvest)
    }
}

@Composable
private fun FarmSummaryCard(snapshot: FarmSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStat("${snapshot.metrics.harvested}", stringResource(R.string.farm_stat_harvests))
                SummaryStat(
                    durationText(snapshot.metrics.focusMinutes * 60_000L),
                    stringResource(R.string.farm_stat_focus),
                )
                SummaryStat(
                    stringResource(R.string.farm_days, snapshot.metrics.bestStreak),
                    stringResource(R.string.farm_stat_streak),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.farm_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptySlotCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(192.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.farm_slot_empty),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LockedSlotCard(slot: Int) {
    val needed = if (slot < FarmRules.BASE_PLOTS + 1) FarmRules.EXTRA_PLOT_AT else FarmRules.SECOND_EXTRA_PLOT_AT
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().height(192.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.farm_slot_locked_harvests, needed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlotCard(
    plot: com.meolist.domain.farm.FarmPlot,
    growth: Int,
    stage: Int,
    compost: Int,
    onHarvest: () -> Unit,
    onFertilize: () -> Unit,
) {
    val mature = growth >= plot.crop.need
    val fraction = (growth.toFloat() / plot.crop.need).coerceIn(0f, 1f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (mature) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().height(192.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CropArt(
                crop = plot.crop,
                stage = stage,
                description = cropName(plot.crop),
                modifier = Modifier.size(64.dp),
            )
            Text(
                cropName(plot.crop),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(stageLabel(stage)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                drawStopIndicator = {},
                color = if (mature) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.farm_plot_growth, growth, plot.crop.need),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            when {
                mature -> Button(onClick = onHarvest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.farm_plot_harvest))
                }
                compost > 0 -> TextButton(onClick = onFertilize, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.farm_plot_compost))
                }
            }
        }
    }
}

@Composable
private fun CompostCard(snapshot: FarmSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.farm_compost_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (snapshot.compost > 0) stringResource(R.string.farm_compost_amount, snapshot.compost)
                    else stringResource(R.string.farm_compost_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HarvestList(harvests: List<HarvestRecord>) {
    val today = LocalDate.now()
    val locale = currentLocale()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            harvests.forEachIndexed { index, harvest ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    CropArt(
                        crop = harvest.crop,
                        stage = 5,
                        description = cropName(harvest.crop),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(cropName(harvest.crop), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "+${harvest.nutrients}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        shortDate(
                            Instant.ofEpochMilli(harvest.harvestedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                            today,
                            locale,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlantDialog(
    snapshot: FarmSnapshot,
    onPlant: (Crop, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<Crop?>(null) }
    var useCompost by remember { mutableStateOf(snapshot.compost > 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.farm_plant_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Crop.entries.forEach { crop ->
                    val unlocked = FarmRules.isCropUnlocked(crop, snapshot.metrics)
                    val isSelected = selected == crop
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = unlocked) { selected = crop }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        CropArt(
                            crop = crop,
                            stage = 5,
                            description = cropName(crop),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cropName(crop), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (unlocked) stringResource(R.string.farm_plant_need, crop.need)
                                else cropUnlockLabel(crop.unlock),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (unlocked) {
                            RadioButton(selected = isSelected, onClick = { selected = crop })
                        } else {
                            Icon(
                                Icons.Outlined.Lock,
                                stringResource(R.string.farm_crop_locked),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (snapshot.compost > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = useCompost, onCheckedChange = { useCompost = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.farm_plant_use_compost, snapshot.compost),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onPlant(it, useCompost) } },
            ) {
                Text(stringResource(R.string.farm_plant_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
}

@Composable
private fun HarvestDialog(record: HarvestRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            CropArt(
                crop = record.crop,
                stage = 5,
                description = cropName(record.crop),
                modifier = Modifier.size(88.dp),
            )
        },
        title = { Text(stringResource(R.string.farm_harvest_dialog_title)) },
        text = {
            Text(stringResource(R.string.farm_harvest_dialog_text, cropName(record.crop), record.nutrients))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_ok)) }
        },
    )
}

@Composable
fun AchievementRow(state: AchievementState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (state.unlocked) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                achievementIcon(state.achievement.metric),
                null,
                tint = if (state.unlocked) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    achievementName(state.achievement),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (state.unlocked) {
                        stringResource(
                            R.string.farm_achievement_unlocked_on,
                            shortDate(
                                Instant.ofEpochMilli(state.unlockedAt ?: 0).atZone(ZoneId.systemDefault()).toLocalDate(),
                                LocalDate.now(),
                                currentLocale(),
                            ),
                        )
                    } else {
                        stringResource(
                            R.string.farm_achievement_progress,
                            state.progress,
                            state.achievement.target,
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                achievementDescription(state.achievement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!state.unlocked) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.fraction },
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
        }
    }
}

@Composable
private fun cropUnlockLabel(unlock: CropUnlock): String = when (unlock) {
    CropUnlock.Always -> ""
    is CropUnlock.Harvests -> stringResource(R.string.farm_crop_locked_harvests, unlock.count)
    is CropUnlock.FocusMinutes -> stringResource(R.string.farm_crop_locked_focus, unlock.minutes / 60)
    is CropUnlock.StreakDays -> stringResource(R.string.farm_crop_locked_streak, unlock.days)
    CropUnlock.AllOtherCrops -> stringResource(R.string.farm_crop_locked_all)
}

@Composable
fun cropName(crop: Crop): String = stringResource(
    when (crop) {
        Crop.CARROT -> R.string.crop_carrot
        Crop.TOMATO -> R.string.crop_tomato
        Crop.SUNFLOWER -> R.string.crop_sunflower
        Crop.PUMPKIN -> R.string.crop_pumpkin
        Crop.GRAPE -> R.string.crop_grape
        Crop.CHERRY -> R.string.crop_cherry
    },
)

@Composable
fun achievementName(achievement: Achievement): String = stringResource(
    when (achievement) {
        Achievement.FIRST_HARVEST -> R.string.achievement_first_harvest
        Achievement.HARVEST_5 -> R.string.achievement_harvest_5
        Achievement.HARVEST_15 -> R.string.achievement_harvest_15
        Achievement.FOCUS_1H -> R.string.achievement_focus_1h
        Achievement.FOCUS_10H -> R.string.achievement_focus_10h
        Achievement.FOCUS_50H -> R.string.achievement_focus_50h
        Achievement.FOCUS_200H -> R.string.achievement_focus_200h
        Achievement.TASKS_10 -> R.string.achievement_tasks_10
        Achievement.TASKS_50 -> R.string.achievement_tasks_50
        Achievement.STREAK_7 -> R.string.achievement_streak_7
        Achievement.STREAK_21 -> R.string.achievement_streak_21
        Achievement.GOALS_5 -> R.string.achievement_goals_5
        Achievement.ALL_CROPS -> R.string.achievement_all_crops
    },
)

@Composable
fun achievementDescription(achievement: Achievement): String = stringResource(
    when (achievement) {
        Achievement.FIRST_HARVEST -> R.string.achievement_first_harvest_desc
        Achievement.HARVEST_5 -> R.string.achievement_harvest_5_desc
        Achievement.HARVEST_15 -> R.string.achievement_harvest_15_desc
        Achievement.FOCUS_1H -> R.string.achievement_focus_1h_desc
        Achievement.FOCUS_10H -> R.string.achievement_focus_10h_desc
        Achievement.FOCUS_50H -> R.string.achievement_focus_50h_desc
        Achievement.FOCUS_200H -> R.string.achievement_focus_200h_desc
        Achievement.TASKS_10 -> R.string.achievement_tasks_10_desc
        Achievement.TASKS_50 -> R.string.achievement_tasks_50_desc
        Achievement.STREAK_7 -> R.string.achievement_streak_7_desc
        Achievement.STREAK_21 -> R.string.achievement_streak_21_desc
        Achievement.GOALS_5 -> R.string.achievement_goals_5_desc
        Achievement.ALL_CROPS -> R.string.achievement_all_crops_desc
    },
)

fun achievementIcon(metric: AchievementMetric): ImageVector = when (metric) {
    AchievementMetric.HARVESTS -> Icons.Outlined.Grass
    AchievementMetric.FOCUS_MINUTES -> Icons.Outlined.Timer
    AchievementMetric.TASKS_DONE -> Icons.Outlined.TaskAlt
    AchievementMetric.BEST_STREAK -> Icons.Outlined.LocalFireDepartment
    AchievementMetric.GOALS_REACHED -> Icons.Outlined.Flag
    AchievementMetric.CROPS_HARVESTED -> Icons.Outlined.EmojiEvents
}

fun stageLabel(stage: Int): Int = when (stage) {
    0 -> R.string.farm_stage_0
    1 -> R.string.farm_stage_1
    2 -> R.string.farm_stage_2
    3 -> R.string.farm_stage_3
    4 -> R.string.farm_stage_4
    else -> R.string.farm_stage_5
}
