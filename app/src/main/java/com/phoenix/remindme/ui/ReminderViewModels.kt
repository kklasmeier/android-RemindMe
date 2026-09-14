package com.phoenix.remindme.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoenix.remindme.alerts.ReminderAlarmScheduler
import com.phoenix.remindme.alerts.AlertPreferences
import com.phoenix.remindme.alerts.ReminderNotificationPublisher
import com.phoenix.remindme.ai.ReminderDraft
import com.phoenix.remindme.location.GeofenceManager
import com.phoenix.remindme.location.LocationRepository
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.data.ReminderEntity
import com.phoenix.remindme.data.ReminderHistoryEntity
import com.phoenix.remindme.data.ReminderRepository
import com.phoenix.remindme.data.ReminderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoaded: Boolean = false,
    val activeAlerts: List<ReminderEntity> = emptyList(),
    val overdue: List<ReminderEntity> = emptyList(),
    val today: List<ReminderEntity> = emptyList(),
    val upcoming: List<ReminderEntity> = emptyList(),
    val completed: List<ReminderEntity> = emptyList(),
    val placeNames: Map<Long, String> = emptyMap(),
) {
    val dueTodayCount get() = today.size + overdue.size
    val locationCount get() = (overdue + today + upcoming).count { it.baseType == BaseType.LOCATION }
    val snoozedCount get() = (overdue + today + upcoming).count { it.status == ReminderStatus.SNOOZED }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderAlarmScheduler,
    private val notifications: ReminderNotificationPublisher,
    private val geofences: GeofenceManager,
    private val locations: LocationRepository,
) : ViewModel() {
    val state: StateFlow<DashboardUiState> = combine(
        repository.observeAll(),
        locations.observeSavedPlaces(),
    ) { reminders, places ->
        groupReminders(reminders).copy(
            isLoaded = true,
            placeNames = places.associate { it.id to it.label },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun complete(id: Long) {
        viewModelScope.launch {
            val reminder = repository.get(id)
            val updated = repository.complete(id)
            notifications.cancel(id)
            if (updated?.isRecurring == true) {
                rearm(updated)
            } else {
                scheduler.cancel(id)
                if (reminder?.baseType == BaseType.LOCATION) geofences.remove(id)
            }
        }
    }

    fun snooze(id: Long, durationMinutes: Int) {
        viewModelScope.launch {
            notifications.cancel(id)
            val reminder = repository.get(id)
            repository.snooze(id, durationMinutes)
            if (reminder?.baseType == BaseType.LOCATION) {
                geofences.remove(id)
                scheduler.scheduleLocationOffset(id, durationMinutes)
            } else {
                repository.get(id)?.let(scheduler::schedule)
            }
        }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch {
            val reminder = repository.get(id)
            val updated = repository.dismiss(id)
            notifications.cancel(id)
            if (updated?.isRecurring == true) {
                rearm(updated)
            } else {
                scheduler.cancel(id)
                if (reminder?.baseType == BaseType.LOCATION) geofences.remove(id)
            }
        }
    }

    private suspend fun rearm(reminder: ReminderEntity) {
        if (reminder.baseType == BaseType.TIME) {
            scheduler.schedule(reminder)
        } else {
            scheduler.cancel(reminder.id)
            reminder.locationId
                ?.let { locations.getPlace(it) }
                ?.let { geofences.register(reminder, it) }
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            val reminder = repository.get(id) ?: return@launch
            scheduler.cancel(id)
            notifications.cancel(id)
            if (reminder.baseType == BaseType.LOCATION) geofences.remove(id)
            repository.delete(reminder)
        }
    }

    private fun groupReminders(reminders: List<ReminderEntity>): DashboardUiState {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val active = reminders.filter {
            it.status == ReminderStatus.ACTIVE || it.status == ReminderStatus.SNOOZED
        }
        return DashboardUiState(
            activeAlerts = reminders.filter { it.status == ReminderStatus.TRIGGERED },
            overdue = active.filter {
                it.baseType == BaseType.TIME && effectiveTime(it) < now
            },
            today = active.filter {
                it.baseType == BaseType.TIME &&
                    effectiveTime(it) >= now &&
                    dateOf(effectiveTime(it), zone) == today
            },
            upcoming = active.filter {
                it.baseType == BaseType.LOCATION ||
                    (it.baseType == BaseType.TIME && dateOf(effectiveTime(it), zone) > today)
            },
            completed = reminders.filter {
                it.status == ReminderStatus.COMPLETED || it.status == ReminderStatus.DISMISSED
            },
        )
    }

    private fun effectiveTime(reminder: ReminderEntity): Long =
        reminder.snoozedUntil ?: reminder.triggerTime ?: Long.MAX_VALUE

    private fun dateOf(time: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
}

data class EditReminderUiState(
    val title: String = "",
    val triggerTime: Long? = null,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    val alertFullScreen: Boolean = true,
    val alertSound: Boolean = true,
    val alertHeadsUp: Boolean = true,
    val alertLockScreen: Boolean = true,
) {
    val canSave get() = title.isNotBlank() && triggerTime != null
}

@HiltViewModel
class EditReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderAlarmScheduler,
    alertPreferences: AlertPreferences,
) : ViewModel() {
    private var draftLoaded = false
    private val defaults = alertPreferences.get()
    var state = androidx.compose.runtime.mutableStateOf(
        EditReminderUiState(
            alertFullScreen = defaults.fullScreen,
            alertSound = defaults.sound,
            alertHeadsUp = defaults.headsUp,
            alertLockScreen = defaults.lockScreen,
        ),
    )
        private set

    fun update(transform: (EditReminderUiState) -> EditReminderUiState) {
        state.value = transform(state.value)
    }

    fun loadDraft(draft: ReminderDraft?) {
        if (draft == null || draftLoaded) return
        draftLoaded = true
        state.value = EditReminderUiState(
            title = draft.title,
            triggerTime = draft.triggerTime,
            isRecurring = draft.isRecurring,
            recurrenceRule = draft.recurrenceRule,
            alertFullScreen = defaults.fullScreen,
            alertSound = defaults.sound,
            alertHeadsUp = defaults.headsUp,
            alertLockScreen = defaults.lockScreen,
        )
    }

    fun save(onSaved: () -> Unit) {
        val draft = state.value
        if (!draft.canSave) return
        viewModelScope.launch {
            val reminder = ReminderEntity(
                title = draft.title.trim(),
                baseType = BaseType.TIME,
                triggerTime = draft.triggerTime,
                isRecurring = draft.isRecurring,
                recurrenceRule = draft.recurrenceRule,
                alertFullScreen = draft.alertFullScreen,
                alertSound = draft.alertSound,
                alertHeadsUp = draft.alertHeadsUp,
                alertLockScreen = draft.alertLockScreen,
            )
            val id = repository.save(reminder)
            scheduler.schedule(reminder.copy(id = id))
            onSaved()
        }
    }
}

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val repository: ReminderRepository,
) : ViewModel() {
    var reminder = androidx.compose.runtime.mutableStateOf<ReminderEntity?>(null)
        private set
    var history = androidx.compose.runtime.mutableStateOf<List<ReminderHistoryEntity>>(emptyList())
        private set
    private var loadedId: Long? = null

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch { repository.observe(id).collect { reminder.value = it } }
        viewModelScope.launch { repository.observeHistory(id).collect { history.value = it } }
    }
}
