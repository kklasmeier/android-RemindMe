package com.phoenix.remindme.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoenix.remindme.ai.ReminderDraft
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.data.LocationTrigger
import com.phoenix.remindme.data.ReminderEntity
import com.phoenix.remindme.data.ReminderRepository
import com.phoenix.remindme.data.SavedPlaceEntity
import com.phoenix.remindme.location.GeofenceManager
import com.phoenix.remindme.location.LocationRepository
import com.phoenix.remindme.location.PlaceCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocationSearchUiState(
    val searching: Boolean = false,
    val results: List<PlaceCandidate> = emptyList(),
    val selected: PlaceCandidate? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locations: LocationRepository,
    private val reminders: ReminderRepository,
    private val geofences: GeofenceManager,
) : ViewModel() {
    var state by mutableStateOf(LocationSearchUiState())
        private set

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = locations.observeSavedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(query: String) {
        if (query.isBlank() || state.searching) return
        state = state.copy(searching = true, error = null)
        viewModelScope.launch {
            val results = locations.searchNearDevice(query)
            state = state.copy(
                searching = false,
                results = results,
                error = if (results.isEmpty()) "No places found — try adding a city or ZIP code." else null,
            )
        }
    }

    fun select(candidate: PlaceCandidate) {
        state = state.copy(selected = candidate, error = null)
    }

    fun select(place: SavedPlaceEntity) {
        select(
            PlaceCandidate(
                name = place.displayName,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                osmId = place.osmId,
                osmType = place.osmType,
            ),
        )
    }

    fun clearSelection() {
        state = state.copy(selected = null)
    }

    fun clearSearch() {
        state = state.copy(results = emptyList(), selected = null, error = null)
    }

    fun updatePlace(place: SavedPlaceEntity) {
        viewModelScope.launch { locations.update(place) }
    }

    fun deletePlace(place: SavedPlaceEntity, onBlocked: (Int) -> Unit) {
        viewModelScope.launch {
            val linked = reminders.countActiveForPlace(place.id)
            if (linked == 0) locations.delete(place) else onBlocked(linked)
        }
    }

    fun savePlace(
        candidate: PlaceCandidate,
        radius: Float,
        label: String,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            locations.savePlace(candidate, radius, label)
            onSaved()
        }
    }

    fun saveReminder(
        draft: ReminderDraft,
        candidate: PlaceCandidate,
        trigger: LocationTrigger,
        offsetMinutes: Int,
        radius: Float,
        onSaved: () -> Unit,
    ) {
        if (state.saving) return
        if (!geofences.hasRequiredPermissions()) {
            state = state.copy(error = "Allow precise and background location before saving.")
            return
        }
        state = state.copy(saving = true, error = null)
        viewModelScope.launch {
            var createdReminder: ReminderEntity? = null
            val result = runCatching {
                val place = locations.save(candidate, radius)
                val reminder = ReminderEntity(
                    title = draft.title,
                    baseType = BaseType.LOCATION,
                    locationId = place.id,
                    locationTrigger = trigger,
                    locationOffsetMinutes = offsetMinutes,
                    isRecurring = draft.isRecurring,
                    recurrenceRule = draft.recurrenceRule,
                    alertFullScreen = draft.alertFullScreen,
                    alertSound = draft.alertSound,
                    alertAlarm = draft.alertAlarm,
                    alertHeadsUp = draft.alertHeadsUp,
                    alertLockScreen = draft.alertLockScreen,
                )
                val id = reminders.save(reminder)
                val savedReminder = reminder.copy(id = id)
                createdReminder = savedReminder
                geofences.register(savedReminder, place).getOrThrow()
            }
            if (result.isSuccess) {
                state = state.copy(saving = false)
                onSaved()
            } else {
                createdReminder?.let { reminders.delete(it) }
                val failure = result.exceptionOrNull()
                state = state.copy(
                    saving = false,
                    error = failure?.message ?: "Couldn't register this location reminder.",
                )
            }
        }
    }
}
