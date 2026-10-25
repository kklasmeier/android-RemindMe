package com.phoenix.remindme.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phoenix.remindme.MainActivity
import com.phoenix.remindme.R
import com.phoenix.remindme.data.ReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object ReminderChannels {
    const val FULL_SCREEN = "remindme_fullscreen"
    const val FULL_SCREEN_SILENT = "remindme_fullscreen_silent"
    const val HEADS_UP = "remindme_headsup"
    const val HEADS_UP_SILENT = "remindme_headsup_silent"
    const val SILENT = "remindme_silent"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    FULL_SCREEN,
                    "Full-screen reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Urgent reminders that can appear over the lock screen"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                },
                NotificationChannel(
                    HEADS_UP,
                    "Heads-up reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Reminder banners and sounds"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                },
                NotificationChannel(
                    FULL_SCREEN_SILENT,
                    "Full-screen reminders (no sound)",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Urgent reminders without sound or vibration"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    HEADS_UP_SILENT,
                    "Heads-up reminders (no sound)",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Reminder banners without sound or vibration"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    SILENT,
                    "Quiet reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Reminders without an interruptive banner"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    setSound(null, null)
                    enableVibration(false)
                },
            ),
        )
    }
}

@Singleton
class ReminderNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AlertPreferences,
) {
    fun publish(reminder: ReminderEntity) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channel = when {
            reminder.alertAlarm && reminder.alertFullScreen && canUseFullScreenIntent() ->
                ReminderChannels.FULL_SCREEN_SILENT
            reminder.alertAlarm && reminder.alertHeadsUp -> ReminderChannels.HEADS_UP_SILENT
            reminder.alertFullScreen && canUseFullScreenIntent() && reminder.alertSound -> ReminderChannels.FULL_SCREEN
            reminder.alertFullScreen && canUseFullScreenIntent() -> ReminderChannels.FULL_SCREEN_SILENT
            reminder.alertHeadsUp && reminder.alertSound -> ReminderChannels.HEADS_UP
            reminder.alertHeadsUp -> ReminderChannels.HEADS_UP_SILENT
            else -> ReminderChannels.SILENT
        }
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            reminder.id.toRequestCode(),
            Intent(context, FullScreenAlertActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                )
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_REMINDER_TITLE, reminder.title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.toRequestCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_ACTIVE_REMINDERS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText("Scheduled reminder · just now")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(
                if (reminder.alertFullScreen) NotificationCompat.PRIORITY_MAX
                else if (reminder.alertHeadsUp) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setVisibility(
                if (reminder.alertLockScreen) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_PRIVATE,
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_done, "Done", actionIntent(reminder.id, ReminderActionReceiver.ACTION_DONE, 1))
            .addAction(
                R.drawable.ic_snooze,
                "Snooze ${preferences.snoozeMinutes()}m",
                actionIntent(reminder.id, ReminderActionReceiver.ACTION_SNOOZE, 2, preferences.snoozeMinutes()),
            )
            .addAction(
                R.drawable.ic_dismiss,
                "Dismiss",
                actionIntent(reminder.id, ReminderActionReceiver.ACTION_DISMISS, 3),
            )
            .apply {
                if (reminder.alertFullScreen && canUseFullScreenIntent()) {
                    setFullScreenIntent(fullScreenIntent, true)
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(reminder.id.toRequestCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify call.
        }
    }

    fun cancel(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId.toRequestCode())
    }

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun actionIntent(
        reminderId: Long,
        action: String,
        offset: Int,
        snoozeMinutes: Int? = null,
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
        snoozeMinutes?.let { intent.putExtra(EXTRA_SNOOZE_MINUTES, it) }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toRequestCode() * 10 + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
