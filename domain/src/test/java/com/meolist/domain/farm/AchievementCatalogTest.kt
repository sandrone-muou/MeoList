package com.meolist.domain.farm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {

    private fun state(
        achievement: Achievement,
        progress: Int,
        unlockedAt: Long? = null,
    ) = AchievementState(achievement, progress, unlockedAt)

    @Test
    fun everyBadgeBelongsToExactlyOneCategory() {
        assertEquals(Achievement.entries.size, AchievementCategory.entries.sumOf { category ->
            Achievement.entries.count { AchievementCatalog.category(it) == category }
        })
        assertEquals(AchievementCategory.HARVEST, AchievementCatalog.category(Achievement.ALL_CROPS))
        assertEquals(AchievementCategory.FOCUS, AchievementCatalog.category(Achievement.FOCUS_10H))
        assertEquals(AchievementCategory.TASKS, AchievementCatalog.category(Achievement.TASKS_50))
        assertEquals(AchievementCategory.HABITS, AchievementCatalog.category(Achievement.STREAK_7))
        assertEquals(AchievementCategory.GOALS, AchievementCatalog.category(Achievement.GOALS_5))
    }

    @Test
    fun seriesIsOrderedFromEasiestToHardest() {
        assertEquals(
            listOf(Achievement.FOCUS_1H, Achievement.FOCUS_10H, Achievement.FOCUS_50H, Achievement.FOCUS_200H),
            AchievementCatalog.series(AchievementMetric.FOCUS_MINUTES),
        )
        assertEquals(
            listOf(Achievement.FIRST_HARVEST, Achievement.HARVEST_5, Achievement.HARVEST_15),
            AchievementCatalog.series(Achievement.HARVEST_5),
        )
    }

    @Test
    fun tierRisesAlongTheSeriesAndAllCropsIsLegend() {
        assertEquals(AchievementTier.BRONZE, AchievementCatalog.tier(Achievement.FOCUS_1H))
        assertEquals(AchievementTier.SILVER, AchievementCatalog.tier(Achievement.FOCUS_10H))
        assertEquals(AchievementTier.GOLD, AchievementCatalog.tier(Achievement.FOCUS_50H))
        assertEquals(AchievementTier.DIAMOND, AchievementCatalog.tier(Achievement.FOCUS_200H))
        assertEquals(AchievementTier.BRONZE, AchievementCatalog.tier(Achievement.TASKS_10))
        assertEquals(AchievementTier.SILVER, AchievementCatalog.tier(Achievement.TASKS_50))
        assertEquals(AchievementTier.LEGEND, AchievementCatalog.tier(Achievement.ALL_CROPS))
    }

    @Test
    fun nextUpKeepsOneBadgePerSeriesAndSkipsUnlockedOnes() {
        val states = listOf(
            state(Achievement.FOCUS_1H, 60, unlockedAt = 1L),
            state(Achievement.FOCUS_10H, 300),
            state(Achievement.FOCUS_50H, 400),
            state(Achievement.HARVEST_5, 3),
            state(Achievement.TASKS_10, 2),
            state(Achievement.TASKS_50, 2),
            state(Achievement.ALL_CROPS, 2),
        )

        val next = AchievementCatalog.nextUp(states, limit = 4)

        assertTrue(next.none { it.unlocked })
        assertEquals(1, next.count { it.achievement.metric == AchievementMetric.FOCUS_MINUTES })
        assertEquals(1, next.count { it.achievement.metric == AchievementMetric.TASKS_DONE })
        // 3/5 harvest is the closest to completion (0.6), so it leads.
        assertEquals(Achievement.HARVEST_5, next.first().achievement)
        assertEquals(4, next.size)
        assertTrue(next.zipWithNext().all { (a, b) -> a.fraction >= b.fraction })
    }

    @Test
    fun nextUpRespectsTheLimit() {
        val states = Achievement.entries.map { state(it, 0) }
        assertEquals(2, AchievementCatalog.nextUp(states, limit = 2).size)
        assertTrue(AchievementCatalog.nextUp(emptyList()).isEmpty())
    }

    @Test
    fun latestUnlockedUsesTheNewestTimestamp() {
        val states = listOf(
            state(Achievement.FIRST_HARVEST, 1, unlockedAt = 100L),
            state(Achievement.FOCUS_1H, 60, unlockedAt = 900L),
            state(Achievement.HARVEST_5, 2),
        )
        assertEquals(Achievement.FOCUS_1H, AchievementCatalog.latestUnlocked(states)?.achievement)
        assertNull(AchievementCatalog.latestUnlocked(emptyList()))
    }

    @Test
    fun remainingNeverGoesNegative() {
        assertEquals(3, AchievementCatalog.remaining(state(Achievement.HARVEST_5, 2)))
        assertEquals(0, AchievementCatalog.remaining(state(Achievement.HARVEST_5, 5)))
    }

    @Test
    fun cropUnlockProgressTracksTheUnlockCondition() {
        val base = FarmMetrics()
        assertNull(FarmRules.cropUnlockProgress(Crop.CARROT, base))

        val tomato = FarmRules.cropUnlockProgress(Crop.TOMATO, FarmMetrics(harvested = 1))
        assertEquals(1f / 3f, tomato!!, 0.001f)

        val sunflower = FarmRules.cropUnlockProgress(Crop.SUNFLOWER, FarmMetrics(focusMinutes = 300))
        assertEquals(0.5f, sunflower!!, 0.001f)

        val pumpkin = FarmRules.cropUnlockProgress(Crop.PUMPKIN, FarmMetrics(bestStreak = 21))
        assertEquals(1f, pumpkin!!, 0.001f)

        val cherry = FarmRules.cropUnlockProgress(
            Crop.CHERRY,
            FarmMetrics(harvestsByCrop = mapOf(Crop.CARROT to 1, Crop.TOMATO to 1)),
        )
        assertEquals(2f / 5f, cherry!!, 0.001f)
    }
}
