package com.phoenix.remindme.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class BaseType { TIME, LOCATION }
enum class LocationTrigger { ARRIVING, LEAVING, EITHER }
enum class ReminderStatus { ACTIVE, SNOOZED, TRIGGERED, COMPLETED, DISMISSED }
enum class HistoryAction { COMPLETED, SNOOZED, DISMISSED }

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val baseType: BaseType,
    val isRecurring: Boolean = false,
    val triggerTime: Long? = null,
    val locationId: Long? = null,
    val locationTrigger: LocationTrigger? = null,
    val locationOffsetMinutes: Int = 0,
    val recurrenceRule: String? = null,
    val alertFullScreen: Boolean = true,
    val alertSound: Boolean = true,
    val alertAlarm: Boolean = false,
    val alertHeadsUp: Boolean = true,
    val alertLockScreen: Boolean = true,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val snoozedUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val displayName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val osmId: Long? = null,
    val osmType: String? = null,
    val geofenceRadius: Float = 150f,
    val isSystemPlace: Boolean = false,
)

@Entity(tableName = "reminder_history")
data class ReminderHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val firedAt: Long,
    val action: HistoryAction,
    val snoozeDurationMinutes: Int? = null,
    val actionAt: Long = System.currentTimeMillis(),
)

class DatabaseConverters {
    @TypeConverter fun baseType(value: String): BaseType = BaseType.valueOf(value)
    @TypeConverter fun baseType(value: BaseType): String = value.name
    @TypeConverter fun locationTrigger(value: String?): LocationTrigger? = value?.let(LocationTrigger::valueOf)
    @TypeConverter fun locationTrigger(value: LocationTrigger?): String? = value?.name
    @TypeConverter fun status(value: String): ReminderStatus = ReminderStatus.valueOf(value)
    @TypeConverter fun status(value: ReminderStatus): String = value.name
    @TypeConverter fun action(value: String): HistoryAction = HistoryAction.valueOf(value)
    @TypeConverter fun action(value: HistoryAction): String = value.name
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY COALESCE(snoozedUntil, triggerTime, 9223372036854775807)")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observe(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query(
        "UPDATE reminders SET status = 'TRIGGERED', snoozedUntil = NULL, updatedAt = :now " +
            "WHERE id = :id AND status IN ('ACTIVE', 'SNOOZED')",
    )
    suspend fun markTriggered(id: Long, now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM reminders WHERE locationId = :locationId AND status IN ('ACTIVE', 'SNOOZED')")
    suspend fun countActiveForPlace(locationId: Long): Int

    @Query(
        "SELECT * FROM reminders " +
            "WHERE baseType = 'TIME' AND status IN ('ACTIVE', 'SNOOZED') " +
            "AND COALESCE(snoozedUntil, triggerTime) IS NOT NULL",
    )
    suspend fun getSchedulable(): List<ReminderEntity>

    @Query(
        "SELECT * FROM reminders " +
            "WHERE baseType = 'LOCATION' AND status IN ('ACTIVE', 'SNOOZED') AND locationId IS NOT NULL",
    )
    suspend fun getLocationSchedulable(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert
    suspend fun insertHistory(history: ReminderHistoryEntity)

    @Query("SELECT * FROM reminder_history WHERE reminderId = :reminderId ORDER BY actionAt DESC")
    fun observeHistory(reminderId: Long): Flow<List<ReminderHistoryEntity>>

    @Query("DELETE FROM reminder_history WHERE reminderId = :reminderId")
    suspend fun deleteHistory(reminderId: Long)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("UPDATE reminders SET status = 'COMPLETED', updatedAt = :now WHERE id = :id")
    suspend fun complete(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE reminders SET status = 'SNOOZED', snoozedUntil = :until, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun snooze(id: Long, until: Long, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE reminders SET status = 'DISMISSED', snoozedUntil = NULL, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun dismiss(id: Long, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun completeWithHistory(id: Long, now: Long = System.currentTimeMillis()) {
        complete(id, now)
        insertHistory(
            ReminderHistoryEntity(
                reminderId = id,
                firedAt = now,
                action = HistoryAction.COMPLETED,
                actionAt = now,
            ),
        )
    }

    @Transaction
    suspend fun snoozeWithHistory(
        id: Long,
        durationMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        snooze(id, now + durationMinutes * 60_000L, now)
        insertHistory(
            ReminderHistoryEntity(
                reminderId = id,
                firedAt = now,
                action = HistoryAction.SNOOZED,
                snoozeDurationMinutes = durationMinutes,
                actionAt = now,
            ),
        )
    }

    @Transaction
    suspend fun dismissWithHistory(id: Long, now: Long = System.currentTimeMillis()) {
        dismiss(id, now)
        insertHistory(
            ReminderHistoryEntity(
                reminderId = id,
                firedAt = now,
                action = HistoryAction.DISMISSED,
                actionAt = now,
            ),
        )
    }
}

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY isSystemPlace DESC, label, displayName")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places")
    suspend fun getAll(): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_places WHERE id = :id")
    suspend fun get(id: Long): SavedPlaceEntity?

    @Query("SELECT * FROM saved_places WHERE osmId = :osmId AND osmType = :osmType LIMIT 1")
    suspend fun findByOsmId(osmId: Long, osmType: String): SavedPlaceEntity?

    @Query(
        "SELECT * FROM saved_places WHERE latitude BETWEEN :minLat AND :maxLat " +
            "AND longitude BETWEEN :minLon AND :maxLon LIMIT 1",
    )
    suspend fun findNear(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): SavedPlaceEntity?

    @Insert
    suspend fun insert(place: SavedPlaceEntity): Long

    @Update
    suspend fun update(place: SavedPlaceEntity)

    @Delete
    suspend fun delete(place: SavedPlaceEntity)
}

@Database(
    entities = [ReminderEntity::class, SavedPlaceEntity::class, ReminderHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(DatabaseConverters::class)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun savedPlaceDao(): SavedPlaceDao
}
