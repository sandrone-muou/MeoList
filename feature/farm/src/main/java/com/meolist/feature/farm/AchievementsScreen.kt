package com.meolist.feature.farm

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.meolist.core.ui.components.IconBadge
import com.meolist.core.ui.components.MeoProgressBar
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.ProgressRing
import com.meolist.core.ui.components.SectionHeader
import com.meolist.core.ui.components.rememberConfirmHaptic
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.AchievementCatalog
import com.meolist.domain.farm.AchievementCategory
import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.AchievementTier
import com.meolist.domain.farm.FarmInteractor
import com.meolist.domain.farm.FarmSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AchievementsRoute(
    onBack: () -> Unit,
    vm: AchievementsViewModel = hiltViewModel(),
) {
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val states = snapshot.achievements
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<AchievementState?>(null) }
    val selectedCategory = AchievementCategory.entries.firstOrNull { it.name == filter }
    val unlocked = states.count { it.unlocked }
    val next = AchievementCatalog.nextUp(states)
    val latest = AchievementCatalog.latestUnlocked(states)

    MeoScaffold(
        title = stringResource(R.string.farm_achievements_title),
        onBack = onBack,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = Meo.spacing.screen,
                end = Meo.spacing.screen,
                top = Meo.spacing.xs,
                bottom = Meo.spacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProgressHero(
                    unlocked = unlocked,
                    total = Achievement.entries.size,
                    snapshot = snapshot,
                    latest = latest,
                )
            }
            if (next.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        stringResource(R.string.farm_achievements_next),
                        modifier = Modifier.padding(top = Meo.spacing.sm),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NextUpRow(next) { detail = it }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryChips(selected = selectedCategory, onSelect = { filter = it?.name })
            }
            val categories = selectedCategory?.let { listOf(it) } ?: AchievementCategory.entries
            categories.forEach { category ->
                val categoryStates = AchievementCatalog.states(states, category)
                if (categoryStates.isEmpty()) return@forEach
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        categoryLabel(category),
                        trailing = "${categoryStates.count { it.unlocked }}/${categoryStates.size}",
                        modifier = Modifier.padding(top = Meo.spacing.sm),
                    )
                }
                items(categoryStates, key = { it.achievement.key }) { state ->
                    BadgeMedallion(state, onClick = { detail = state })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.farm_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Meo.spacing.lg, start = Meo.spacing.lg, end = Meo.spacing.lg),
                )
            }
        }
    }

    detail?.let { state ->
        AchievementDetailSheet(state = state, allStates = states, onDismiss = { detail = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressHero(
    unlocked: Int,
    total: Int,
    snapshot: FarmSnapshot,
    latest: AchievementState?,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Meo.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                progress = if (total == 0) 0f else unlocked.toFloat() / total,
                size = 104.dp,
                stroke = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$unlocked/$total",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.farm_achievements_unlocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.width(Meo.spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.farm_achievements_progress),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    latest?.let {
                        stringResource(R.string.farm_achievements_latest, achievementName(it.achievement))
                    } ?: stringResource(R.string.farm_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Meo.spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HeroChip(
                        icon = Icons.Outlined.Grass,
                        text = stringResource(R.string.farm_chip_harvests) + " " +
                            snapshot.metrics.harvested.toString(),
                    )
                    HeroChip(
                        icon = Icons.Outlined.Timer,
                        text = stringResource(R.string.farm_chip_focus) + " " +
                            durationText(snapshot.metrics.focusMinutes * 60_000L),
                    )
                    HeroChip(
                        icon = Icons.Outlined.LocalFireDepartment,
                        text = stringResource(R.string.farm_chip_streak) + " " +
                            stringResource(R.string.farm_days, snapshot.metrics.bestStreak),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun NextUpRow(next: List<AchievementState>, onClick: (AchievementState) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
        items(next, key = { it.achievement.key }) { state ->
            NextUpCard(state, onClick = { onClick(state) })
        }
    }
}

@Composable
private fun NextUpCard(state: AchievementState, onClick: () -> Unit) {
    val color = tierColor(AchievementCatalog.tier(state.achievement))
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(224.dp),
    ) {
        Column(Modifier.padding(Meo.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = achievementIcon(state.achievement.metric),
                    color = color,
                    size = 36.dp,
                )
                Spacer(Modifier.width(Meo.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        achievementName(state.achievement),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        remainingLabel(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(Meo.spacing.md))
            MeoProgressBar(
                progress = state.fraction,
                color = color,
                height = 6.dp,
            )
            Spacer(Modifier.height(Meo.spacing.xs))
            Text(
                "${state.progress} / ${state.achievement.target}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: AchievementCategory?, onSelect: (AchievementCategory?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Meo.spacing.xs),
        modifier = Modifier.fillMaxWidth().padding(vertical = Meo.spacing.xs),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.farm_achievements_all)) },
        )
        AchievementCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(categoryLabel(category)) },
            )
        }
    }
}

@Composable
private fun BadgeMedallion(state: AchievementState, onClick: () -> Unit) {
    val tier = AchievementCatalog.tier(state.achievement)
    val color = tierColor(tier)
    val haptic = rememberConfirmHaptic()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                haptic()
                onClick()
            }
            .padding(vertical = Meo.spacing.sm, horizontal = Meo.spacing.xs),
    ) {
        ProgressRing(
            progress = if (state.unlocked) 1f else state.fraction,
            size = 76.dp,
            stroke = 4.dp,
            color = if (state.unlocked) color else MaterialTheme.colorScheme.outline,
            trackColor = if (state.unlocked) {
                color.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.unlocked) {
                            Brush.linearGradient(
                                listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.6f)),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.unlocked) achievementIcon(state.achievement.metric) else Icons.Outlined.Lock,
                    contentDescription = achievementName(state.achievement),
                    tint = if (state.unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(Meo.spacing.sm))
        Text(
            achievementName(state.achievement),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (state.unlocked) {
                tierLabel(tier)
            } else {
                "${state.progress}/${state.achievement.target}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.unlocked) color else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementDetailSheet(
    state: AchievementState,
    allStates: List<AchievementState>,
    onDismiss: () -> Unit,
) {
    val tier = AchievementCatalog.tier(state.achievement)
    val color = tierColor(tier)
    val series = AchievementCatalog.series(state.achievement)
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
                ProgressRing(
                    progress = if (state.unlocked) 1f else state.fraction,
                    size = 96.dp,
                    stroke = 6.dp,
                    color = color,
                ) {
                    Box(
                        Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = if (state.unlocked) 1f else 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.unlocked) achievementIcon(state.achievement.metric) else Icons.Outlined.Lock,
                            null,
                            tint = if (state.unlocked) Color.White else color,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Meo.spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(achievementName(state.achievement), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Meo.spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TierPill(tier, color)
                        Spacer(Modifier.width(Meo.spacing.sm))
                        Text(
                            categoryLabel(AchievementCatalog.category(state.achievement)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Meo.spacing.sm))
                    Text(
                        if (state.unlocked) {
                            stringResource(
                                R.string.farm_achievement_unlocked_on,
                                shortDate(
                                    Instant.ofEpochMilli(state.unlockedAt ?: 0)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate(),
                                    LocalDate.now(),
                                    currentLocale(),
                                ),
                            )
                        } else {
                            remainingLabel(state)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.unlocked) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Meo.spacing.lg))
            Text(
                achievementDescription(state.achievement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Meo.spacing.lg))
            MeoProgressBar(progress = state.fraction, color = color)
            Spacer(Modifier.height(Meo.spacing.xs))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${state.progress} / ${state.achievement.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    tierLabel(tier),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }

            if (series.size > 1) {
                Spacer(Modifier.height(Meo.spacing.xl))
                Text(
                    stringResource(R.string.farm_achievement_series),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Meo.spacing.sm))
                series.forEachIndexed { index, achievement ->
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(vertical = Meo.spacing.sm),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    val unlocked = allStates.firstOrNull { it.achievement == achievement }?.unlocked == true
                    SeriesRow(
                        achievement = achievement,
                        current = achievement == state.achievement,
                        unlocked = unlocked,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesRow(
    achievement: Achievement,
    current: Boolean,
    unlocked: Boolean,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        unlocked -> color.copy(alpha = 0.2f)
                        current -> color.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (unlocked) {
                Icon(Icons.Outlined.Check, null, tint = color, modifier = Modifier.size(14.dp))
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (current) color else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.width(Meo.spacing.md))
        Text(
            achievementName(achievement),
            style = MaterialTheme.typography.bodyMedium,
            color = if (unlocked || current) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            achievementTargetLabel(achievement),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TierPill(tier: AchievementTier, color: Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(tierLabel(tier), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
