package com.meolist.data.repo

import com.meolist.data.db.dao.CountdownDao
import com.meolist.data.db.dao.HabitDao
import com.meolist.data.db.entity.CountdownEntity
import com.meolist.data.db.entity.HabitCheckInEntity
import com.meolist.data.db.entity.HabitEntity
import com.meolist.domain.countdown.Countdown
import com.meolist.domain.countdown.CountdownMode
import com.meolist.domain.countdown.CountdownRepository
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.habit.HabitRepository
import com.meolist.domain.habit.HabitSchedule
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HabitRepositoryImpl @Inject constructor(private val dao: HabitDao) : HabitRepository {
    override fun observeHabits(): Flow<List<Habit>> = dao.observeHabits().map { list -> list.map { it.toModel() } }

    override suspend fun getHabits(): List<Habit> = dao.getHabits().map { it.toModel() }

    override suspend fun getHabit(id: Long): Habit? = dao.getHabit(id)?.toModel()

    override suspend fun saveHabit(habit: Habit): Long {
        val entity = habit.toEntity()
        return if (habit.id == 0L) {
            dao.insertHabit(entity.copy(sortOrder = dao.nextSortOrder()))
        } else {
            dao.updateHabit(entity)
            habit.id
        }
    }

    override suspend fun deleteHabit(id: Long) = dao.deleteHabit(id)

    override suspend fun nextSortOrder(): Long = dao.nextSortOrder()

    override fun observeCheckIns(): Flow<List<HabitCheckIn>> =
        dao.observeCheckIns().map { list -> list.map { it.toModel() } }

    override suspend fun getAllCheckIns(): List<HabitCheckIn> = dao.getAllCheckIns().map { it.toModel() }

    override suspend fun getCheckIns(habitId: Long): List<HabitCheckIn> = dao.getCheckIns(habitId).map { it.toModel() }

    override suspend fun setCheckIn(habitId: Long, day: LocalDate, value: Int) {
        if (value <= 0) {
            dao.deleteCheckIn(habitId, day.toEpochDay())
        } else {
            dao.upsertCheckIn(HabitCheckInEntity(habitId, day.toEpochDay(), value))
        }
    }

    private fun HabitEntity.toModel() = Habit(
        id = id,
        name = name,
        icon = icon,
        color = color,
        schedule = HabitSchedule.decode(scheduleRule),
        dailyTarget = dailyTarget,
        unit = unit,
        startDay = LocalDate.ofEpochDay(startDay),
        archived = archived,
        sortOrder = sortOrder,
    )

    private fun Habit.toEntity() = HabitEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        scheduleRule = schedule.encode(),
        dailyTarget = dailyTarget,
        unit = unit,
        startDay = startDay.toEpochDay(),
        archived = archived,
        sortOrder = sortOrder,
        createdAt = 0,
    )

    private fun HabitCheckInEntity.toModel() = HabitCheckIn(habitId, LocalDate.ofEpochDay(day), value)
}

class CountdownRepositoryImpl @Inject constructor(private val dao: CountdownDao) : CountdownRepository {
    override fun observeAll(): Flow<List<Countdown>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun get(id: Long): Countdown? = dao.get(id)?.toModel()

    override suspend fun save(countdown: Countdown): Long {
        val entity = countdown.toEntity()
        return if (countdown.id == 0L) {
            dao.insert(entity.copy(sortOrder = dao.nextSortOrder()))
        } else {
            dao.update(entity)
            countdown.id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun CountdownEntity.toModel() = Countdown(
        id = id,
        title = title,
        baseDay = LocalDate.ofEpochDay(baseDay),
        mode = CountdownMode.fromValue(mode),
        yearly = yearly,
        pinned = pinned,
        archived = archived,
        color = color,
        note = note,
        sortOrder = sortOrder,
    )

    private fun Countdown.toEntity() = CountdownEntity(
        id = id,
        title = title,
        baseDay = baseDay.toEpochDay(),
        mode = mode.value,
        yearly = yearly,
        pinned = pinned,
        archived = archived,
        color = color,
        note = note,
        sortOrder = sortOrder,
        createdAt = 0,
    )
}
