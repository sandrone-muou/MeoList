package com.meolist.data.repo

import com.meolist.data.db.dao.ReminderDao
import com.meolist.data.db.entity.ReminderEntity
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderKind
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.reminder.ReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun ReminderEntity.toDomain() = Reminder(
    id = id,
    ownerType = ReminderOwner.fromValue(ownerType),
    ownerId = ownerId,
    kind = ReminderKind.fromValue(kind),
    offsetMin = offsetMin,
    minuteOfDay = minuteOfDay,
    nextFireAt = nextFireAt,
    snoozedUntil = snoozedUntil,
    enabled = enabled,
)

internal fun Reminder.toEntity() = ReminderEntity(
    id = id,
    ownerType = ownerType.value,
    ownerId = ownerId,
    kind = kind.value,
    offsetMin = offsetMin,
    minuteOfDay = minuteOfDay,
    nextFireAt = nextFireAt,
    snoozedUntil = snoozedUntil,
    enabled = enabled,
)

class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
) : ReminderRepository {
    override fun observeForOwner(ownerType: ReminderOwner, ownerId: Long): Flow<List<Reminder>> =
        dao.observeForOwner(ownerType.value, ownerId).map { it.map(ReminderEntity::toDomain) }

    override fun observeOwnerIds(ownerType: ReminderOwner): Flow<Set<Long>> =
        dao.observeOwnerIds(ownerType.value).map { it.toSet() }

    override suspend fun getForOwner(ownerType: ReminderOwner, ownerId: Long): List<Reminder> =
        dao.getForOwner(ownerType.value, ownerId).map { it.toDomain() }

    override suspend fun get(id: Long): Reminder? = dao.get(id)?.toDomain()

    override suspend fun replaceForOwner(ownerType: ReminderOwner, ownerId: Long, reminders: List<Reminder>) =
        dao.replaceForOwner(ownerType.value, ownerId, reminders.map { it.copy(id = 0, ownerType = ownerType, ownerId = ownerId).toEntity() })

    override suspend fun update(reminder: Reminder) = dao.update(reminder.toEntity())
    override suspend fun updateAll(reminders: List<Reminder>) = dao.updateAll(reminders.map { it.toEntity() })
    override suspend fun getAll(): List<Reminder> = dao.getAll().map { it.toDomain() }
    override suspend fun getDue(at: Long): List<Reminder> = dao.getDue(at).map { it.toDomain() }
    override suspend fun nextFireTime(): Long? = dao.nextFireTime()
    override suspend fun deleteOrphans() = dao.deleteTaskOrphans()
}
