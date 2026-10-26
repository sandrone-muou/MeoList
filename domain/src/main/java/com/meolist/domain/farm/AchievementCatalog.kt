package com.meolist.domain.farm

/** How the achievement wall is grouped. */
enum class AchievementCategory { HARVEST, FOCUS, TASKS, HABITS, GOALS }

/** Medal rank inside a series; the whole set caps at diamond. */
enum class AchievementTier { BRONZE, SILVER, GOLD, DIAMOND, LEGEND }

/**
 * Read-only view over [Achievement]: which family a badge belongs to, how far up its ladder it
 * sits, and which badges the player is closest to earning. Pure functions, so the UI (and tests)
 * can rely on one definition.
 */
object AchievementCatalog {
    private val ladder = listOf(
        AchievementTier.BRONZE,
        AchievementTier.SILVER,
        AchievementTier.GOLD,
        AchievementTier.DIAMOND,
    )

    fun category(achievement: Achievement): AchievementCategory = when (achievement.metric) {
        AchievementMetric.HARVESTS, AchievementMetric.CROPS_HARVESTED -> AchievementCategory.HARVEST
        AchievementMetric.FOCUS_MINUTES -> AchievementCategory.FOCUS
        AchievementMetric.TASKS_DONE -> AchievementCategory.TASKS
        AchievementMetric.BEST_STREAK -> AchievementCategory.HABITS
        AchievementMetric.GOALS_REACHED -> AchievementCategory.GOALS
    }

    /** Badges sharing a metric, easiest first: 1h → 10h → 50h → 200h. */
    fun series(metric: AchievementMetric): List<Achievement> =
        Achievement.entries.filter { it.metric == metric }.sortedBy { it.target }

    fun series(achievement: Achievement): List<Achievement> = series(achievement.metric)

    fun tier(achievement: Achievement): AchievementTier =
        if (achievement == Achievement.ALL_CROPS) {
            AchievementTier.LEGEND
        } else {
            val index = series(achievement).indexOf(achievement).coerceAtLeast(0)
            ladder[index.coerceAtMost(ladder.lastIndex)]
        }

    /** States of one category in ladder order, for the badge wall. */
    fun states(states: List<AchievementState>, category: AchievementCategory): List<AchievementState> =
        states.filter { category(it.achievement) == category }
            .sortedWith(compareBy({ it.achievement.metric.ordinal }, { it.achievement.target }))

    /**
     * The nearest unfinished badge of every series, closest to completion first. One badge per
     * series keeps the "what to do next" strip from showing four focus tiers at once.
     */
    fun nextUp(states: List<AchievementState>, limit: Int = 3): List<AchievementState> =
        states.filterNot { it.unlocked }
            .groupBy { it.achievement.metric }
            .mapNotNull { (_, group) -> group.minByOrNull { it.achievement.target } }
            .sortedByDescending { it.fraction }
            .take(limit)

    /** Most recently unlocked badge, if any. */
    fun latestUnlocked(states: List<AchievementState>): AchievementState? =
        states.filter { it.unlocked }.maxByOrNull { it.unlockedAt ?: 0L }

    fun remaining(state: AchievementState): Int =
        (state.achievement.target - state.progress).coerceAtLeast(0)
}
