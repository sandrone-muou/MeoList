package com.meolist.domain.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class QuadrantRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 24)
    private val hour = 3_600_000L

    private fun task(priority: Priority = Priority.NONE, dueAt: Long? = null) =
        Task(id = 1, title = "T", priority = priority, dueAt = dueAt)

    private fun due(days: Long): Long =
        LocalDateTime.of(today.plusDays(days), java.time.LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun importanceFollowsMediumAndHighPriority() {
        assertFalse(QuadrantRules.isImportant(task(Priority.LOW)))
        assertFalse(QuadrantRules.isImportant(task(Priority.NONE)))
        assertTrue(QuadrantRules.isImportant(task(Priority.MEDIUM)))
        assertTrue(QuadrantRules.isImportant(task(Priority.HIGH)))
    }

    @Test
    fun urgencyIncludesOverdueTodayAndTheWindow() {
        assertTrue(QuadrantRules.isUrgent(task(dueAt = due(-3)), today, 1, zone))
        assertTrue(QuadrantRules.isUrgent(task(dueAt = due(0)), today, 1, zone))
        assertTrue(QuadrantRules.isUrgent(task(dueAt = due(1)), today, 1, zone))
        assertFalse(QuadrantRules.isUrgent(task(dueAt = due(2)), today, 1, zone))
        assertFalse(QuadrantRules.isUrgent(task(), today, 1, zone))
    }

    @Test
    fun classificationCoversAllFourQuadrants() {
        assertEquals(Quadrant.URGENT_IMPORTANT, QuadrantRules.of(task(Priority.HIGH, due(0)), today, 1, zone))
        assertEquals(Quadrant.NOT_URGENT_IMPORTANT, QuadrantRules.of(task(Priority.MEDIUM, due(10)), today, 1, zone))
        assertEquals(Quadrant.URGENT_NOT_IMPORTANT, QuadrantRules.of(task(Priority.NONE, due(1)), today, 1, zone))
        assertEquals(Quadrant.NOT_URGENT_NOT_IMPORTANT, QuadrantRules.of(task(), today, 1, zone))
        assertEquals(Quadrant.NOT_URGENT_IMPORTANT, QuadrantRules.of(task(Priority.HIGH), today, 5, zone))
    }

    @Test
    fun droppingMapsToPriorityWithoutChangingDates() {
        assertEquals(Priority.HIGH, QuadrantRules.priorityFor(Quadrant.URGENT_IMPORTANT))
        assertEquals(Priority.MEDIUM, QuadrantRules.priorityFor(Quadrant.NOT_URGENT_IMPORTANT))
        assertEquals(Priority.NONE, QuadrantRules.priorityFor(Quadrant.URGENT_NOT_IMPORTANT))
        assertEquals(Priority.NONE, QuadrantRules.priorityFor(Quadrant.NOT_URGENT_NOT_IMPORTANT))
        assertTrue(Quadrant.URGENT_IMPORTANT.isImportant)
        assertFalse(Quadrant.URGENT_NOT_IMPORTANT.isImportant)
    }
}
