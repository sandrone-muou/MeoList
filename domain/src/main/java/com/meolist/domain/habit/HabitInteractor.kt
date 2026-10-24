package com.meolist.domain.habit

import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.farm.FarmHook
import javax.inject.Inject
import javax.inject.Singleton

/** Write operations for habits with the rules the UI should not repeat. */
@Singleton
class HabitInteractor @Inject constructor(
    private val habits: HabitRepository,
    private val clock: Clock,
    private val farm: FarmHook = FarmHook.None,
) {
    suspend fun save(habit: Habit): Long {
        val clean = habit.copy(
            name = habit.name.trim(),
            unit = habit.unit.trim(),
            dailyTarget = habit.dailyTarget.coerceIn(1, MAX_TARGET),
        )
        require(clean.name.isNotEmpty()) { "name must not be blank" }
        return habits.saveHabit(clean)
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val habit = habits.getHabit(id) ?: return
        habits.saveHabit(habit.copy(archived = archived))
    }

    suspend fun delete(id: Long) {
        habits.deleteHabit(id)
        farm.onNutrientsMayHaveChanged()
    }

    /**
     * Sets the day's value, clamped to the daily target. Future days are ignored; past days can be
     * filled in later. A value of 0 removes the check-in.
     */
    suspend fun setCheckIn(habitId: Long, day: java.time.LocalDate, value: Int) {
        val habit = habits.getHabit(habitId) ?: return
        if (day > clock.today()) return
        val clamped = value.coerceIn(0, HabitRules.tapValue(habit))
        habits.setCheckIn(habitId, day, clamped)
        farm.onNutrientsMayHaveChanged()
    }

    /** Checks the day off completely, or clears it when it is already done. */
    suspend fun toggle(habitId: Long, day: java.time.LocalDate) {
        val habit = habits.getHabit(habitId) ?: return
        val existing = habits.getCheckIns(habitId).firstOrNull { it.day == day }
        val done = existing != null && HabitRules.isDone(habit, existing.value)
        setCheckIn(habitId, day, if (done) 0 else HabitRules.tapValue(habit))
    }

    private companion object {
        const val MAX_TARGET = 999
    }
}
