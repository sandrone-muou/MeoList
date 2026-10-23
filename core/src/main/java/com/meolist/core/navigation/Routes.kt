package com.meolist.core.navigation

/**
 * String routes shared by every feature. Features never depend on each other;
 * they navigate to another feature's screen through these helpers only.
 */
object Routes {
    const val ARG_ID = "id"
    const val ARG_EXTRA = "extra"

    // Top-level destinations (bottom bar)
    const val TASKS = "tasks"
    const val TRACKER = "tracker"
    const val HABITS = "habits"
    const val FARM = "farm"
    const val MORE = "more"

    // Tasks
    const val TASK_DETAIL = "task/{$ARG_ID}"
    fun taskDetail(taskId: Long) = "task/$taskId"
    const val TASK_SEARCH = "tasks/search"
    const val QUADRANT = "tasks/quadrant"
    const val LIST_MANAGE = "tasks/lists"

    // Tracker
    const val ACTIVITY_TYPE_EDIT = "tracker/type/{$ARG_ID}"
    fun activityTypeEdit(typeId: Long = 0) = "tracker/type/$typeId"
    const val RECORDS = "tracker/records"
    const val RECORD_EDIT = "tracker/record/{$ARG_ID}?$ARG_EXTRA={$ARG_EXTRA}"
    /** [recordId] 0 creates a new record, optionally pre-selecting [typeId]. */
    fun recordEdit(recordId: Long = 0, typeId: Long = 0) = "tracker/record/$recordId?$ARG_EXTRA=$typeId"

    // Habits
    const val HABIT_DETAIL = "habit/{$ARG_ID}"
    fun habitDetail(habitId: Long) = "habit/$habitId"
    const val HABIT_EDIT = "habit/edit/{$ARG_ID}"
    fun habitEdit(habitId: Long = 0) = "habit/edit/$habitId"

    // Countdown
    const val COUNTDOWNS = "countdowns"
    const val COUNTDOWN_EDIT = "countdown/edit/{$ARG_ID}"
    fun countdownEdit(id: Long = 0) = "countdown/edit/$id"

    // Others
    const val STATS = "stats"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "farm/achievements"
}
