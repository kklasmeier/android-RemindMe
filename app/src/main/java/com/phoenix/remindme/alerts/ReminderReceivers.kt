package com.phoenix.remindme.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phoenix.remindme.data.ReminderRepository
import com.phoenix.remindme.data.ReminderStatus
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.location.GeofenceManager
import com.phoenix.remindme.location.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notifications: ReminderNotificationPublisher

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id < 0) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.markTriggered(id)?.let { reminder ->
                    notifications.publish(reminder)
                    if (reminder.alertAlarm) {
                        runCatching { AlarmSoundService.start(context, reminder.id, reminder.title) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderAlarmScheduler
    @Inject lateinit var notifications: ReminderNotificationPublisher
    @Inject lateinit var geofences: GeofenceManager
    @Inject lateinit var locations: LocationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id < 0) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { AlarmSoundService.stop(context, id) }
                val existing = repository.get(id)
                when (intent.action) {
                    ACTION_DONE -> {
                        val updated = repository.complete(id)
                        if (updated?.isRecurring == true) {
                            rearm(updated)
                        } else {
                            scheduler.cancel(id)
                            if (existing?.baseType == BaseType.LOCATION) geofences.remove(id)
                        }
                    }
                    ACTION_SNOOZE -> {
                        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 30).coerceAtLeast(1)
                        repository.snooze(id, minutes)
                        if (existing?.baseType == BaseType.LOCATION) {
                            geofences.remove(id)
                            scheduler.scheduleLocationOffset(id, minutes)
                        } else {
                            repository.get(id)?.let(scheduler::schedule)
                        }
                    }
                    ACTION_DISMISS -> {
                        val updated = repository.dismiss(id)
                        if (updated?.isRecurring == true) {
                            rearm(updated)
                        } else {
                            scheduler.cancel(id)
                            if (existing?.baseType == BaseType.LOCATION) geofences.remove(id)
                        }
                    }
                }
                notifications.cancel(id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rearm(reminder: com.phoenix.remindme.data.ReminderEntity) {
        if (reminder.baseType == BaseType.TIME) {
            scheduler.schedule(reminder)
        } else {
            scheduler.cancel(reminder.id)
            reminder.locationId
                ?.let { locations.getPlace(it) }
                ?.let { geofences.register(reminder, it) }
        }
    }

    companion object {
        const val ACTION_DONE = "com.phoenix.remindme.action.DONE"
        const val ACTION_SNOOZE = "com.phoenix.remindme.action.SNOOZE"
        const val ACTION_DISMISS = "com.phoenix.remindme.action.DISMISS"
    }
}

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderAlarmScheduler
    @Inject lateinit var geofences: GeofenceManager
    @Inject lateinit var locations: LocationRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
            )
        ) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getSchedulable().forEach(scheduler::schedule)
                repository.getLocationSchedulable().forEach { reminder ->
                    if (reminder.status == ReminderStatus.SNOOZED && reminder.snoozedUntil != null) {
                        val remainingMinutes =
                            ((reminder.snoozedUntil - System.currentTimeMillis() + 59_999L) / 60_000L)
                                .coerceAtLeast(1L)
                                .coerceAtMost(Int.MAX_VALUE.toLong())
                                .toInt()
                        scheduler.scheduleLocationOffset(reminder.id, remainingMinutes)
                    } else {
                        reminder.locationId
                            ?.let { locations.getPlace(it) }
                            ?.let { place -> geofences.register(reminder, place) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getSchedulable().forEach(scheduler::schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
