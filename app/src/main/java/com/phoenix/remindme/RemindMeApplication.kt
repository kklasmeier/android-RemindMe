package com.phoenix.remindme

import android.app.Application
import com.phoenix.remindme.alerts.ReminderChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RemindMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderChannels.create(this)
    }
}
