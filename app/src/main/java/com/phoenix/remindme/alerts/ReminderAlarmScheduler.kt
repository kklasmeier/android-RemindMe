package com.phoenix.remindme.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.phoenix.remindme.MainActivity
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.data.ReminderEntity
import com.phoenix.remindme.data.ReminderStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_REMINDER_ID = "reminder_id"
const val EXTRA_REMINDER_TITLE = "reminder_title"
const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
const val EXTRA_OPEN_ACTIVE_REMINDERS = "open_active_reminders"

@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: ReminderEntity) {
        if (reminder.baseType != BaseType.TIME) return
        if (reminder.status != ReminderStatus.ACTIVE && reminder.status != ReminderStatus.SNOOZED) return
        val requestedTime = reminder.snoozedUntil ?: reminder.triggerTime ?: return
        val triggerAt = requestedTime.coerceAtLeast(System.currentTimeMillis() + 500L)

        val pendingIntent = alarmPendingIntent(reminder.id)
        val showIntent = PendingIntent.getActivity(
            context,
            reminder.id.toRequestCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleAlarmClock(triggerAt, showIntent, pendingIntent)
    }

    fun scheduleLocationOffset(reminderId: Long, delayMinutes: Int) {
        val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L
        val pendingIntent = alarmPendingIntent(reminderId)
        val showIntent = PendingIntent.getActivity(
            context,
            reminderId.toRequestCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleAlarmClock(triggerAt, showIntent, pendingIntent)
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(alarmPendingIntent(reminderId))
    }

    private fun scheduleAlarmClock(
        triggerAt: Long,
        showIntent: PendingIntent,
        operation: PendingIntent,
    ) {
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun alarmPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            context,
            reminderId.toRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal fun Long.toRequestCode(): Int = (this xor (this ushr 32)).toInt()
