package com.meolist.domain.settings

enum class BottomDestination(val key: String) {
    TASKS("tasks"), TRACKER("tracker"), HABITS("habits"), FARM("farm"),
    CALENDAR("calendar"), QUADRANT("tasks/quadrant"), COUNTDOWNS("countdowns"),
    STATS("stats"), RECORDS("tracker/records"), MORE("more");

    val required: Boolean get() = this == TASKS || this == TRACKER || this == MORE
}

object BottomNavigation {
    const val MAX_ITEMS = 5
    val defaults = listOf(BottomDestination.TASKS, BottomDestination.TRACKER, BottomDestination.HABITS, BottomDestination.FARM, BottomDestination.MORE)

    fun normalize(keys: List<String>): List<BottomDestination> {
        val known = keys.mapNotNull { key -> BottomDestination.entries.find { it.key == key } }.distinct()
        val items = known.filter { it != BottomDestination.MORE }.toMutableList()
        if (BottomDestination.TASKS !in items) items.add(0, BottomDestination.TASKS)
        if (BottomDestination.TRACKER !in items) items.add(1, BottomDestination.TRACKER)
        var optional = 0
        return items.filter { it.required || optional++ < 2 } + BottomDestination.MORE
    }
}
