package com.meolist.feature.farm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.theme.Meo
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.shortDate
import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.IconBadge
import com.meolist.core.ui.components.MeoListDivider
import com.meolist.core.ui.components.MeoListGroup
import com.meolist.core.ui.components.MeoProgressBar
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.SectionHeader
import com.meolist.core.ui.components.StatChip
import com.meolist.core.ui.components.rememberConfirmHaptic
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.AchievementCatalog
import com.meolist.domain.farm.Crop
import com.meolist.domain.farm.FarmInteractor
import com.meolist.domain.farm.FarmPlot
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var plantingSlot by remember { mutableStateOf<Int?>(null) }
    var detailPlotId by remember { mutableStateOf<Long?>(null) }
    val haptic = rememberConfirmHaptic()
    val snapshot = state.snapshot
    val detailPlot = snapshot.activePlots.firstOrNull { it.id == detailPlotId }

    LaunchedEffect(state.event) {
        when (val event = state.event) {
            null -> Unit
            is FarmEvent.CompostApplied -> {
                haptic()
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

    LaunchedEffect(detailPlotId, snapshot.plots) {
        if (detailPlotId != null && detailPlot == null) detailPlotId = null
    }

    MeoScaffold(
        title = stringResource(R.string.farm_title),
        large = true,
        actions = {
            IconButton(onClick = onOpenAchievements) {
                Icon(Icons.Outlined.EmojiEvents, stringResource(R.string.farm_achievements_title))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) return@MeoScaffold

        LazyColumn(
            contentPadding = PaddingValues(
                start = Meo.spacing.screen,
                end = Meo.spacing.screen,
                top = Meo.spacing.xs,
                bottom = Meo.spacing.bottomContent,
            ),
            verticalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                FarmScene(
                    snapshot = snapshot,
                    onPlotClick = { detailPlotId = it.id },
                    onEmptySlot = { plantingSlot = it },
                    onLockedSlot = { slot ->
                        val needed = if (slot < FarmRules.BASE_PLOTS + 1) {
                            FarmRules.EXTRA_PLOT_AT
                        } else {
                            FarmRules.SECOND_EXTRA_PLOT_AT
                        }
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.farm_slot_locked_harvests, needed))
                        }
                    },
                )
            }
            item { CompostCard(snapshot) }
            item {
                SectionHeader(
                    title = stringResource(R.string.farm_achievements_title),
                    action = stringResource(R.string.farm_achievements_open),
                    onAction = onOpenAchievements,
                    modifier = Modifier.padding(top = Meo.spacing.sm),
                )
            }
            item {
                AchievementsPreview(
                    snapshot = snapshot,
                    onOpen = onOpenAchievements,
                )
            }
            item {
                SectionHeader(
                    title = stringResource(R.string.farm_harvests_title),
                    modifier = Modifier.padding(top = Meo.spacing.sm),
                )
            }
            item {
                if (snapshot.harvests.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Grass,
                        title = stringResource(R.string.farm_no_harvests),
                        body = stringResource(R.string.farm_tip),
                    )
                } else {
                    HarvestList(snapshot.harvests.take(5))
                }
            }
        }
    }

    plantingSlot?.let { slot ->
        PlantSheet(
            snapshot = snapshot,
            onPlant = { crop, useCompost ->
                vm.plant(slot, crop, useCompost)
                plantingSlot = null
            },
            onDismiss = { plantingSlot = null },
        )
    }

    detailPlot?.let { plot ->
        PlotSheet(
            plot = plot,
            growth = snapshot.growth(plot),
            stage = FarmRules.stage(plot, snapshot.entries),
            compost = snapshot.compost,
            onHarvest = {
                haptic()
                vm.harvest(plot.id)
            },
            onFertilize = {
                vm.applyCompost(plot.id)
                detailPlotId = null
            },
            onDismiss = { detailPlotId = null },
        )
    }

    state.harvest?.let { record ->
        HarvestDialog(record = record, onDismiss = vm::dismissHarvest)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmScene(
    snapshot: FarmSnapshot,
    onPlotClick: (FarmPlot) -> Unit,
    onEmptySlot: (Int) -> Unit,
    onLockedSlot: (Int) -> Unit,
) {
    val bySlot = snapshot.activePlots.associateBy { it.slot }
    val ready = snapshot.activePlots.count { snapshot.growth(it) >= it.crop.need }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Meo.colors.skyTop, Meo.colors.skyBottom),
                    ),
                ),
        ) {
            FarmBackdrop(Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(Meo.spacing.lg)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Meo.spacing.xs),
                ) {
                    StatChip(
                        icon = Icons.Outlined.Grass,
                        text = stringResource(R.string.farm_chip_harvests) + " " +
                            snapshot.metrics.harvested.toString(),
                        tint = MaterialTheme.colorScheme.onSurface,
                        container = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    )
                    StatChip(
                        icon = Icons.Outlined.Timer,
                        text = stringResource(R.string.farm_chip_focus) + " " +
                            durationText(snapshot.metrics.focusMinutes * 60_000L),
                        tint = MaterialTheme.colorScheme.onSurface,
                        container = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    )
                    StatChip(
                        icon = Icons.Outlined.LocalFireDepartment,
                        text = stringResource(R.string.farm_chip_streak) + " " +
                            stringResource(R.string.farm_days, snapshot.metrics.bestStreak),
                        tint = MaterialTheme.colorScheme.onSurface,
                        container = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    )
                }
                if (ready > 0) {
                    Spacer(Modifier.height(Meo.spacing.sm))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Meo.colors.successContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                stringResource(R.string.farm_scene_ready, ready),
                                style = MaterialTheme.typography.labelSmall,
                                color = Meo.colors.onSuccessContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Meo.spacing.md))
                (0 until FarmRules.MAX_PLOTS).chunked(3).forEachIndexed { index, slots ->
                    if (index > 0) Spacer(Modifier.height(Meo.spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                        slots.forEach { slot ->
                            val plot = bySlot[slot]
                            val locked = slot >= snapshot.plotLimit
                            val lockedNeeded = if (slot < FarmRules.BASE_PLOTS + 1) {
                                FarmRules.EXTRA_PLOT_AT
                            } else {
                                FarmRules.SECOND_EXTRA_PLOT_AT
                            }
                            PlotTile(
                                plot = plot,
                                locked = locked,
                                lockedNeeded = lockedNeeded,
                                growth = plot?.let { snapshot.growth(it) } ?: 0,
                                stage = plot?.let { FarmRules.stage(it, snapshot.entries) } ?: 0,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when {
                                        locked -> onLockedSlot(slot)
                                        plot != null -> onPlotClick(plot)
                                        else -> onEmptySlot(slot)
                                    }
                                },
                            )
                        }
                        repeat(3 - slots.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmBackdrop(modifier: Modifier = Modifier) {
    val leaf = Meo.colors.leaf
    val soil = Meo.colors.soil
    val sun = Meo.colors.gold
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sunCenter = Offset(w * 0.86f, h * 0.09f)
        drawCircle(sun.copy(alpha = 0.35f), radius = w * 0.075f, center = sunCenter)
        drawCircle(sun.copy(alpha = 0.14f), radius = w * 0.12f, center = sunCenter)
        drawOval(
            Color.White.copy(alpha = 0.42f),
            topLeft = Offset(w * 0.06f, h * 0.07f),
            size = androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.045f),
        )
        drawOval(
            Color.White.copy(alpha = 0.32f),
            topLeft = Offset(w * 0.16f, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.035f),
        )
        val hills = Path().apply {
            moveTo(0f, h * 0.82f)
            quadraticTo(w * 0.25f, h * 0.68f, w * 0.5f, h * 0.82f)
            quadraticTo(w * 0.75f, h * 0.96f, w, h * 0.78f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hills, leaf.copy(alpha = 0.20f))
        val field = Path().apply {
            moveTo(0f, h * 0.92f)
            quadraticTo(w * 0.5f, h * 0.86f, w, h * 0.93f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(field, soil.copy(alpha = 0.16f))
    }
}

@Composable
private fun PlotTile(
    plot: FarmPlot?,
    locked: Boolean,
    lockedNeeded: Int,
    growth: Int,
    stage: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val mature = plot != null && growth >= plot.crop.need
    val container = when {
        locked -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f)
        mature -> Meo.colors.successContainer.copy(alpha = 0.92f)
        plot != null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.48f)
    }
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(Meo.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            locked -> {
                Icon(
                    Icons.Outlined.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.farm_slot_locked_short, lockedNeeded),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            plot == null -> {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.farm_slot_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                CropArt(
                    crop = plot.crop,
                    stage = stage,
                    description = cropName(plot.crop),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    cropName(plot.crop),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(stageLabel(stage)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Meo.spacing.xs))
                CropGrowthBar(
                    progress = growth.toFloat() / plot.crop.need,
                    height = 6.dp,
                    color = if (mature) Meo.colors.success else MaterialTheme.colorScheme.primary,
                    showTip = false,
                    showTicks = false,
                )
                Text(
                    if (mature) {
                        stringResource(R.string.farm_slot_ready)
                    } else {
                        stringResource(R.string.farm_plot_growth, growth, plot.crop.need)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mature) Meo.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CompostCard(snapshot: FarmSnapshot) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Meo.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Outlined.Spa,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 40.dp,
            )
            Spacer(Modifier.width(Meo.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.farm_compost_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    if (snapshot.compost > 0) {
                        stringResource(R.string.farm_compost_amount, snapshot.compost)
                    } else {
                        stringResource(R.string.farm_compost_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            if (snapshot.compost > 0) {
                Text(
                    snapshot.compost.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AchievementsPreview(snapshot: FarmSnapshot, onOpen: () -> Unit) {
    val unlocked = snapshot.achievements.count { it.unlocked }
    val total = Achievement.entries.size
    val next = AchievementCatalog.nextUp(snapshot.achievements, limit = 2)
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Meo.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Meo.spacing.md),
        ) {
            AchievementOverview(unlocked, total)
            if (next.isNotEmpty()) {
                Text(
                    stringResource(R.string.farm_achievements_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            next.forEach { state ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = achievementIcon(state.achievement.metric),
                        color = tierColor(AchievementCatalog.tier(state.achievement)),
                        size = 32.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            achievementName(state.achievement),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            remainingLabel(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Meo.spacing.xs))
                        MeoProgressBar(progress = state.fraction, height = 4.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HarvestList(harvests: List<HarvestRecord>) {
    val today = LocalDate.now()
    val locale = currentLocale()
    MeoListGroup {
        harvests.forEachIndexed { index, harvest ->
            if (index > 0) MeoListDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Meo.spacing.lg, vertical = Meo.spacing.sm),
            ) {
                CropArt(
                    crop = harvest.crop,
                    stage = 5,
                    description = cropName(harvest.crop),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(Meo.spacing.md))
                Text(
                    cropName(harvest.crop),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.farm_harvest_nutrients, harvest.absorbedNutrients),
                    style = MaterialTheme.typography.labelLarge,
                    color = Meo.colors.success,
                )
                Spacer(Modifier.width(Meo.spacing.md))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlantSheet(
    snapshot: FarmSnapshot,
    onPlant: (Crop, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<Crop?>(null) }
    var useCompost by remember { mutableStateOf(snapshot.compost > 0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Meo.spacing.xl)
                .padding(bottom = Meo.spacing.xl),
        ) {
            Text(stringResource(R.string.farm_crops_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Meo.spacing.md))
            Crop.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                    pair.forEach { crop ->
                        val unlocked = FarmRules.isCropUnlocked(crop, snapshot.metrics)
                        CropOption(
                            crop = crop,
                            unlocked = unlocked,
                            selected = selected == crop,
                            unlockLabel = cropUnlockLabel(crop.unlock),
                            unlockProgress = FarmRules.cropUnlockProgress(crop, snapshot.metrics),
                            modifier = Modifier.weight(1f),
                            onSelect = { selected = crop },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(Meo.spacing.sm))
            }
            if (snapshot.compost > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useCompost, onCheckedChange = { useCompost = it })
                    Spacer(Modifier.width(Meo.spacing.sm))
                    Text(
                        stringResource(R.string.farm_plant_use_compost, snapshot.compost),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(Meo.spacing.md))
            Button(
                onClick = { selected?.let { onPlant(it, useCompost) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.farm_plant_action))
            }
        }
    }
}

@Composable
private fun CropOption(
    crop: Crop,
    unlocked: Boolean,
    selected: Boolean,
    unlockLabel: String,
    unlockProgress: Float?,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        enabled = unlocked,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(148.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(Meo.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CropArt(
                crop = crop,
                stage = 5,
                description = cropName(crop),
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { alpha = if (unlocked) 1f else 0.45f },
            )
            Spacer(Modifier.height(Meo.spacing.xs))
            Text(
                cropName(crop),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unlocked) {
                Text(
                    stringResource(R.string.farm_crop_level, crop.need),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(Meo.spacing.xs))
                if (unlockProgress != null) {
                    MeoProgressBar(
                        progress = unlockProgress,
                        height = 4.dp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    unlockLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlotSheet(
    plot: FarmPlot,
    growth: Int,
    stage: Int,
    compost: Int,
    onHarvest: () -> Unit,
    onFertilize: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mature = growth >= plot.crop.need
    val shownGrowth = growth.coerceAtMost(plot.crop.need)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Meo.spacing.xl)
                .padding(bottom = Meo.spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(Meo.spacing.xl))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Meo.colors.skyTop,
                                    Meo.colors.skyBottom,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CropArt(
                        crop = plot.crop,
                        stage = stage,
                        description = cropName(plot.crop),
                        modifier = Modifier.size(88.dp),
                    )
                }
                Spacer(Modifier.width(Meo.spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(cropName(plot.crop), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Meo.spacing.xs))
                    Text(
                        stringResource(stageLabel(stage)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (mature) Meo.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Meo.spacing.sm))
                    CropGrowthBar(
                        progress = shownGrowth.toFloat() / plot.crop.need,
                        height = 12.dp,
                        color = if (mature) Meo.colors.success else MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Meo.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.farm_plot_growth, shownGrowth, plot.crop.need),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (mature) {
                                stringResource(R.string.farm_slot_ready)
                            } else {
                                stringResource(
                                    R.string.farm_plot_remaining,
                                    (plot.crop.need - growth).coerceAtLeast(0),
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mature) Meo.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Meo.spacing.lg))
            if (mature) {
                Button(
                    onClick = onHarvest,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.farm_plot_harvest))
                }
            } else if (compost > 0) {
                FilledTonalButton(
                    onClick = onFertilize,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        stringResource(R.string.farm_plot_compost) + " · " +
                            stringResource(R.string.farm_compost_amount, compost),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.farm_plot_no_compost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HarvestDialog(record: HarvestRecord, onDismiss: () -> Unit) {
    val haptic = rememberConfirmHaptic()
    val scale = remember { Animatable(0.6f) }
    val spark = remember { Animatable(0f) }
    LaunchedEffect(record) {
        haptic()
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        spark.animateTo(1f, tween(900))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                HarvestSparks(
                    progress = spark.value,
                    color = Meo.colors.gold,
                    modifier = Modifier.matchParentSize(),
                )
                CropArt(
                    crop = record.crop,
                    stage = 5,
                    description = cropName(record.crop),
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                )
            }
        },
        title = { Text(stringResource(R.string.farm_harvest_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.farm_harvest_dialog_text,
                    cropName(record.crop),
                    record.absorbedNutrients,
                ),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.meolist.core.R.string.core_ok))
            }
        },
    )
}

@Composable
private fun HarvestSparks(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (progress <= 0f || progress >= 1f) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val alpha = (1f - progress).coerceIn(0f, 1f)
        repeat(12) { index ->
            val angle = index * (2 * PI / 12)
            val distance = size.minDimension * (0.18f + 0.32f * progress)
            val at = Offset(
                center.x + cos(angle).toFloat() * distance,
                center.y + sin(angle).toFloat() * distance,
            )
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = size.minDimension * 0.022f * (1f - progress * 0.4f),
                center = at,
            )
        }
    }
}
