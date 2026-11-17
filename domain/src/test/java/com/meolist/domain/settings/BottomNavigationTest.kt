package com.meolist.domain.settings

import org.junit.Assert.*
import org.junit.Test

class BottomNavigationTest {
    @Test fun defaultsArePreserved() {
        assertEquals(BottomNavigation.defaults, BottomNavigation.normalize(BottomNavigation.defaults.map { it.key }))
    }

    @Test fun requiredEntriesAreRestoredAndMoreIsLast() {
        assertEquals(listOf(BottomDestination.TASKS, BottomDestination.TRACKER, BottomDestination.MORE), BottomNavigation.normalize(listOf("more", "unknown", "more")))
    }

    @Test fun optionalItemsAreCappedAndOrdered() {
        val result = BottomNavigation.normalize(listOf("stats", "tracker", "calendar", "farm", "tasks", "more", "stats"))
        assertEquals(listOf(BottomDestination.STATS, BottomDestination.TRACKER, BottomDestination.CALENDAR, BottomDestination.TASKS, BottomDestination.MORE), result)
        assertEquals(5, result.size)
    }

    @Test fun threeAndFourItemChoicesStaySmall() {
        assertEquals(3, BottomNavigation.normalize(listOf("tracker", "tasks", "more")).size)
        assertEquals(4, BottomNavigation.normalize(listOf("tracker", "tasks", "stats", "more")).size)
    }
}
