package com.meolist.feature.farm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.meolist.core.theme.Meo
import com.meolist.core.ui.durationText
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.AchievementCatalog
import com.meolist.domain.farm.AchievementCategory
import com.meolist.domain.farm.AchievementMetric
import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.AchievementTier
import com.meolist.domain.farm.Crop
import com.meolist.domain.farm.CropUnlock

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

@Composable
@ReadOnlyComposable
fun tierColor(tier: AchievementTier): Color = lerp(
    when (tier) {
        AchievementTier.BRONZE -> Meo.colors.bronze
        AchievementTier.SILVER -> Meo.colors.silver
        AchievementTier.GOLD -> Meo.colors.gold
        AchievementTier.DIAMOND -> Meo.colors.diamond
        AchievementTier.LEGEND -> Meo.colors.legend
    },
    MaterialTheme.colorScheme.onSurfaceVariant,
    0.65f,
)

@Composable
fun tierLabel(tier: AchievementTier): String = stringResource(
    when (tier) {
        AchievementTier.BRONZE -> R.string.farm_tier_bronze
        AchievementTier.SILVER -> R.string.farm_tier_silver
        AchievementTier.GOLD -> R.string.farm_tier_gold
        AchievementTier.DIAMOND -> R.string.farm_tier_diamond
        AchievementTier.LEGEND -> R.string.farm_tier_legend
    },
)

@Composable
fun categoryLabel(category: AchievementCategory): String = stringResource(
    when (category) {
        AchievementCategory.HARVEST -> R.string.farm_category_harvest
        AchievementCategory.FOCUS -> R.string.farm_category_focus
        AchievementCategory.TASKS -> R.string.farm_category_tasks
        AchievementCategory.HABITS -> R.string.farm_category_habits
        AchievementCategory.GOALS -> R.string.farm_category_goals
    },
)

/** Human target of a badge, e.g. "Focus 10h", used on the series timeline. */
@Composable
fun achievementTargetLabel(achievement: Achievement): String = when (achievement.metric) {
    AchievementMetric.HARVESTS -> stringResource(R.string.farm_target_harvests, achievement.target)
    AchievementMetric.FOCUS_MINUTES ->
        stringResource(R.string.farm_target_focus, durationText(achievement.target * 60_000L))
    AchievementMetric.TASKS_DONE -> stringResource(R.string.farm_target_tasks, achievement.target)
    AchievementMetric.BEST_STREAK -> stringResource(R.string.farm_target_streak, achievement.target)
    AchievementMetric.GOALS_REACHED -> stringResource(R.string.farm_target_goals, achievement.target)
    AchievementMetric.CROPS_HARVESTED -> stringResource(R.string.farm_target_crops, achievement.target)
}

/** "3 times to go", "2h to go"; empty string once unlocked. */
@Composable
fun remainingLabel(state: AchievementState): String {
    if (state.unlocked) return ""
    val left = AchievementCatalog.remaining(state)
    val amount = when (state.achievement.metric) {
        AchievementMetric.FOCUS_MINUTES -> durationText(left * 60_000L)
        AchievementMetric.CROPS_HARVESTED -> stringResource(R.string.farm_remaining_crops, left)
        else -> stringResource(R.string.farm_remaining_count, left)
    }
    return stringResource(R.string.farm_achievement_remaining, amount)
}

@Composable
fun cropUnlockLabel(unlock: CropUnlock): String = when (unlock) {
    CropUnlock.Always -> ""
    is CropUnlock.Harvests -> stringResource(R.string.farm_crop_locked_harvests, unlock.count)
    is CropUnlock.FocusMinutes -> stringResource(R.string.farm_crop_locked_focus, unlock.minutes / 60)
    is CropUnlock.StreakDays -> stringResource(R.string.farm_crop_locked_streak, unlock.days)
    CropUnlock.AllOtherCrops -> stringResource(R.string.farm_crop_locked_all)
}
