package com.phoenix.remindme.alerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AlertDefaults(
    val fullScreen: Boolean = true,
    val sound: Boolean = true,
    val headsUp: Boolean = true,
    val lockScreen: Boolean = true,
)

@Singleton
class AlertPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("alert_defaults", Context.MODE_PRIVATE)

    fun get(): AlertDefaults = AlertDefaults(
        fullScreen = preferences.getBoolean("full_screen", true),
        sound = preferences.getBoolean("sound", true),
        headsUp = preferences.getBoolean("heads_up", true),
        lockScreen = preferences.getBoolean("lock_screen", true),
    )

    fun save(defaults: AlertDefaults) {
        preferences.edit()
            .putBoolean("full_screen", defaults.fullScreen)
            .putBoolean("sound", defaults.sound)
            .putBoolean("heads_up", defaults.headsUp)
            .putBoolean("lock_screen", defaults.lockScreen)
            .apply()
    }

    fun snoozeMinutes(): Int = preferences.getInt("snooze_minutes", 30)
    fun autoSubmitSeconds(): Int = preferences.getInt("auto_submit_seconds", 3)
    fun geofenceRadius(): Float = preferences.getFloat("geofence_radius", 150f)

    fun saveSnoozeMinutes(minutes: Int) = preferences.edit().putInt("snooze_minutes", minutes).apply()
    fun saveAutoSubmitSeconds(seconds: Int) =
        preferences.edit().putInt("auto_submit_seconds", seconds).apply()
    fun saveGeofenceRadius(radius: Float) =
        preferences.edit().putFloat("geofence_radius", radius).apply()

    fun displayName(): String = preferences.getString("display_name", "").orEmpty()

    fun saveDisplayName(name: String) =
        preferences.edit().putString("display_name", name.trim()).apply()
}
