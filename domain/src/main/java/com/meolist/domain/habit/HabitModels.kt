package com.meolist.domain.habit

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** When a habit is expected. [WeeklyTimes] can be done on any day as long as the week reaches the target. */
sealed interface HabitSchedule {
    data object Daily : HabitSchedule
    data class Weekdays(val days: Set<DayOfWeek>) : HabitSchedule
    data class WeeklyTimes(val times: Int) : HabitSchedule

    fun encode(): String = when (this) {
        Daily -> "daily"
        is Weekdays -> "days:" + days.sortedBy { it.value }.joinToString(",") { it.value.toString() }
        is WeeklyTimes -> "weekly:$times"
    }

    companion object {
        /** Tolerant parser: anything unknown becomes [Daily] so a habit stays usable. */
        fun decode(value: String): HabitSchedule {
            val parts = value.split(":", limit = 2)
            return when (parts[0]) {
                "days" -> Weekdays(
                    parts.getOrNull(1).orEmpty().split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.value == n } }
                        .toSet(),
                ).takeIf { it.days.isNotEmpty() } ?: Daily
                "weekly" -> WeeklyTimes((parts.getOrNull(1)?.toIntOrNull() ?: 3).coerceIn(1, 7))
                else -> Daily
            }
        }
    }
}

/**
 * A habit to check off. [dailyTarget] above 1 makes it countable ("8 glasses"); [unit] names the
 * unit. A check-in counts as done once its value reaches the target.
 */
data class Habit(
    val id: Long = 0,
    val name: String,
    val icon: String = "check",
    val color: Int = 0,
    val schedule: HabitSchedule = HabitSchedule.Daily,
    val dailyTarget: Int = 1,
    val unit: String = "",
    val startDay: LocalDate = LocalDate.now(),
    val archived: Boolean = false,
    val sortOrder: Long = 0,
) {
    val isCountable: Boolean get() = dailyTarget > 1
}

/** One day of a habit. A missing row means "not checked". */
data class HabitCheckIn(
    val habitId: Long,
    val day: LocalDate,
    val value: Int,
) {
    val isDone: Boolean get() = value > 0
}

interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    suspend fun getHabit(id: Long): Habit?
    suspend fun saveHabit(habit: Habit): Long
    suspend fun deleteHabit(id: Long)
    suspend fun nextSortOrder(): Long

    /** Every check-in of every habit, newest first. */
    fun observeCheckIns(): Flow<List<HabitCheckIn>>
    suspend fun getCheckIns(habitId: Long): List<HabitCheckIn>
    /** Stores [value] for the day; a value of 0 or less removes the row. */
    suspend fun setCheckIn(habitId: Long, day: LocalDate, value: Int)
}
