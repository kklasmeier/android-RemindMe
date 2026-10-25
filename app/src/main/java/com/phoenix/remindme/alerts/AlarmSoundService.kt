package com.phoenix.remindme.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phoenix.remindme.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmSoundService : Service() {
    @Inject lateinit var preferences: AlertPreferences

    private val players = mutableMapOf<Long, MediaPlayer>()
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest

    override fun onCreate() {
        super.onCreate()
        running = this
        audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm(
                reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L),
                title = intent.getStringExtra(EXTRA_REMINDER_TITLE).orEmpty(),
            )
            ACTION_STOP -> stopAlarm(intent.getLongExtra(EXTRA_REMINDER_ID, -1L))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (running === this) running = null
        players.values.forEach(MediaPlayer::release)
        players.clear()
        if (::audioFocusRequest.isInitialized) audioManager.abandonAudioFocusRequest(audioFocusRequest)
        super.onDestroy()
    }

    private fun startAlarm(reminderId: Long, title: String) {
        if (reminderId < 0L || players.containsKey(reminderId)) return
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(
            this,
            RingtoneManager.TYPE_ALARM,
        ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmSoundService, alarmUri)
            isLooping = true
            prepare()
        }
        if (players.isEmpty()) audioManager.requestAudioFocus(audioFocusRequest)
        players[reminderId] = player
        player.start()
        startForeground(NOTIFICATION_ID, buildNotification(reminderId, title))
    }

    private fun stopAlarm(reminderId: Long) {
        players.remove(reminderId)?.let { player ->
            player.stop()
            player.release()
        }
        if (players.isEmpty()) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(reminderId: Long, title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Reminder alarm" })
            .setContentText("Alarm sounding — acknowledge this reminder to stop it")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(
                R.drawable.ic_done,
                "Done",
                actionIntent(reminderId, ReminderActionReceiver.ACTION_DONE, 1),
            )
            .addAction(
                R.drawable.ic_snooze,
                "Snooze ${preferences.snoozeMinutes()}m",
                actionIntent(
                    reminderId,
                    ReminderActionReceiver.ACTION_SNOOZE,
                    2,
                    preferences.snoozeMinutes(),
                ),
            )
            .addAction(
                R.drawable.ic_dismiss,
                "Dismiss",
                actionIntent(reminderId, ReminderActionReceiver.ACTION_DISMISS, 3),
            )
            .build()
    }

    private fun actionIntent(
        reminderId: Long,
        action: String,
        offset: Int,
        snoozeMinutes: Int? = null,
    ): PendingIntent {
        val intent = Intent(this, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
        snoozeMinutes?.let { intent.putExtra(EXTRA_SNOOZE_MINUTES, it) }
        return PendingIntent.getBroadcast(
            this,
            reminderId.toInt() * 10 + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Persistent reminder alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Controls the notification shown while a reminder alarm is sounding"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        private var running: AlarmSoundService? = null
        private const val CHANNEL_ID = "remindme_persistent_alarm"
        private const val NOTIFICATION_ID = 47001
        private const val ACTION_START = "com.phoenix.remindme.action.START_ALARM_SOUND"
        private const val ACTION_STOP = "com.phoenix.remindme.action.STOP_ALARM_SOUND"

        fun start(context: Context, reminderId: Long, title: String) {
            val intent = Intent(context, AlarmSoundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_REMINDER_TITLE, title)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, reminderId: Long) {
            val service = running
            if (service != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    service.stopAlarm(reminderId)
                } else {
                    Handler(Looper.getMainLooper()).post { service.stopAlarm(reminderId) }
                }
                return
            }
            runCatching {
                context.startService(
                    Intent(context, AlarmSoundService::class.java)
                        .setAction(ACTION_STOP)
                        .putExtra(EXTRA_REMINDER_ID, reminderId),
                )
            }
        }
    }
}
