package com.phoenix.remindme.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
) {
    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<ReminderEntity?> = dao.observe(id)
    fun observeHistory(id: Long): Flow<List<ReminderHistoryEntity>> = dao.observeHistory(id)
    suspend fun get(id: Long): ReminderEntity? = dao.get(id)
    suspend fun getSchedulable(): List<ReminderEntity> = dao.getSchedulable()
    suspend fun getLocationSchedulable(): List<ReminderEntity> = dao.getLocationSchedulable()
    suspend fun countActiveForPlace(placeId: Long): Int = dao.countActiveForPlace(placeId)

    suspend fun markTriggered(id: Long): ReminderEntity? {
        return if (dao.markTriggered(id) > 0) dao.get(id) else null
    }

    suspend fun save(reminder: ReminderEntity): Long =
        if (reminder.id == 0L) dao.insert(reminder) else {
            dao.update(reminder.copy(updatedAt = System.currentTimeMillis()))
            reminder.id
        }

    suspend fun complete(id: Long): ReminderEntity? = advanceOrFinish(id, HistoryAction.COMPLETED)

    suspend fun snooze(id: Long, durationMinutes: Int): ReminderEntity? {
        require(durationMinutes > 0) { "Snooze duration must be positive" }
        dao.snoozeWithHistory(id, durationMinutes)
        return dao.get(id)
    }

    suspend fun dismiss(id: Long): ReminderEntity? = advanceOrFinish(id, HistoryAction.DISMISSED)
    suspend fun delete(reminder: ReminderEntity) {
        dao.deleteHistory(reminder.id)
        dao.delete(reminder)
    }

    private suspend fun advanceOrFinish(id: Long, action: HistoryAction): ReminderEntity? {
        val reminder = dao.get(id) ?: return null
        val now = System.currentTimeMillis()
        dao.insertHistory(ReminderHistoryEntity(reminderId = id, firedAt = now, action = action, actionAt = now))
        val next = if (reminder.baseType == BaseType.TIME && reminder.isRecurring) {
            Recurrence.next(reminder.recurrenceRule, reminder.triggerTime ?: now)
        } else {
            null
        }
        val updated = if (reminder.baseType == BaseType.LOCATION && reminder.isRecurring) {
            reminder.copy(snoozedUntil = null, status = ReminderStatus.ACTIVE, updatedAt = now)
        } else if (next != null) {
            reminder.copy(triggerTime = next, snoozedUntil = null, status = ReminderStatus.ACTIVE, updatedAt = now)
        } else {
            reminder.copy(
                status = if (action == HistoryAction.COMPLETED) ReminderStatus.COMPLETED else ReminderStatus.DISMISSED,
                snoozedUntil = null,
                updatedAt = now,
            )
        }
        dao.update(updated)
        return updated
    }
}
