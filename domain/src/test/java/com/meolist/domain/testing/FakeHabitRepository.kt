package com.meolist.domain.testing

import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.habit.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory habits and check-ins with the same semantics as the Room implementation. */
class FakeHabitRepository : HabitRepository {
    val habits = MutableStateFlow<Map<Long, Habit>>(emptyMap())
    val checkIns = MutableStateFlow<Set<HabitCheckIn>>(emptySet())
    private var nextId = 1L

    override fun observeHabits(): Flow<List<Habit>> = habits.map { m -> m.values.sortedBy { it.sortOrder } }
    override suspend fun getHabits(): List<Habit> = habits.value.values.sortedBy { it.sortOrder }
    override suspend fun getHabit(id: Long): Habit? = habits.value[id]

    override suspend fun saveHabit(habit: Habit): Long {
        val id = if (habit.id == 0L) nextId++ else habit.id
        habits.value = habits.value + (id to habit.copy(id = id))
        return id
    }

    override suspend fun deleteHabit(id: Long) {
        habits.value = habits.value - id
        checkIns.value = checkIns.value.filterNot { it.habitId == id }.toSet()
    }

    override suspend fun nextSortOrder(): Long = (habits.value.values.maxOfOrNull { it.sortOrder } ?: 0) + 1

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> = checkIns.map { it.sortedByDescending { c -> c.day } }

    override suspend fun getAllCheckIns(): List<HabitCheckIn> = checkIns.value.toList()

    override suspend fun getCheckIns(habitId: Long): List<HabitCheckIn> =
        checkIns.value.filter { it.habitId == habitId }

    override suspend fun setCheckIn(habitId: Long, day: LocalDate, value: Int) {
        val without = checkIns.value.filterNot { it.habitId == habitId && it.day == day }.toSet()
        checkIns.value = if (value <= 0) without else without + HabitCheckIn(habitId, day, value)
    }
}
