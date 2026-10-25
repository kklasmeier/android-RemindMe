package com.phoenix.remindme.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.phoenix.remindme.alerts.AlarmSoundService
import com.phoenix.remindme.alerts.ReminderAlarmScheduler
import com.phoenix.remindme.alerts.ReminderNotificationPublisher
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.data.LocationTrigger
import com.phoenix.remindme.data.ReminderEntity
import com.phoenix.remindme.data.ReminderRepository
import com.phoenix.remindme.data.ReminderStatus
import com.phoenix.remindme.data.SavedPlaceEntity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class GeofenceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getGeofencingClient(context)
    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            4_210,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun hasRequiredPermissions(): Boolean {
        val foreground = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return foreground && background
    }

    @SuppressLint("MissingPermission")
    suspend fun register(reminder: ReminderEntity, place: SavedPlaceEntity): Result<Unit> {
        if (!hasRequiredPermissions()) {
            return Result.failure(SecurityException("Foreground and background location are required"))
        }
        val transitions = when (reminder.locationTrigger ?: LocationTrigger.ARRIVING) {
            LocationTrigger.ARRIVING -> Geofence.GEOFENCE_TRANSITION_ENTER
            LocationTrigger.LEAVING -> Geofence.GEOFENCE_TRANSITION_EXIT
            LocationTrigger.EITHER -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        }
        val geofence = Geofence.Builder()
            .setRequestId(reminder.id.toString())
            .setCircularRegion(place.latitude, place.longitude, place.geofenceRadius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            .setNotificationResponsiveness(30_000)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        return suspendCancellableCoroutine { continuation ->
            client.addGeofences(request, pendingIntent)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { continuation.resume(Result.failure(it)) }
        }
    }

    suspend fun remove(reminderId: Long) {
        suspendCancellableCoroutine { continuation ->
            client.removeGeofences(listOf(reminderId.toString()))
                .addOnCompleteListener { continuation.resume(Unit) }
        }
    }
}

@AndroidEntryPoint
class GeofenceBroadcastReceiver : android.content.BroadcastReceiver() {
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var notifications: ReminderNotificationPublisher
    @Inject lateinit var scheduler: ReminderAlarmScheduler
    @Inject lateinit var geofences: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        val event = com.google.android.gms.location.GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ids.forEach { id ->
                    val reminder = reminders.get(id) ?: return@forEach
                    if (
                        reminder.baseType != BaseType.LOCATION ||
                        (reminder.status != ReminderStatus.ACTIVE && reminder.status != ReminderStatus.SNOOZED)
                    ) return@forEach
                    if (reminder.locationOffsetMinutes > 0) {
                        scheduler.scheduleLocationOffset(id, reminder.locationOffsetMinutes)
                    } else {
                        reminders.markTriggered(id)?.let { triggered ->
                            notifications.publish(triggered)
                            if (triggered.alertAlarm) {
                                runCatching { AlarmSoundService.start(context, triggered.id, triggered.title) }
                            }
                        }
                    }
                    if (!reminder.isRecurring) {
                        geofences.remove(id)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
