package com.phoenix.remindme.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phoenix.remindme.data.ReminderDao
import com.phoenix.remindme.data.ReminderDatabase
import com.phoenix.remindme.data.SavedPlaceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ReminderDatabase =
        Room.databaseBuilder(context, ReminderDatabase::class.java, "remindme.db")
            .addMigrations(Migration1To2)
            .build()

    @Provides
    fun reminderDao(database: ReminderDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun savedPlaceDao(database: ReminderDatabase): SavedPlaceDao = database.savedPlaceDao()
}

private object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE reminders ADD COLUMN alertAlarm INTEGER NOT NULL DEFAULT 0",
        )
    }
}
