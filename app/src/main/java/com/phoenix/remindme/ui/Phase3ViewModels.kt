package com.phoenix.remindme.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoenix.remindme.ai.AiRepository
import com.phoenix.remindme.ai.AiServiceException
import com.phoenix.remindme.ai.ApiKeyRepository
import com.phoenix.remindme.ai.ReminderDraft
import com.phoenix.remindme.alerts.ReminderAlarmScheduler
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.data.ReminderEntity
import com.phoenix.remindme.data.ReminderRepository
import com.phoenix.remindme.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ApiKeyUiState(
    val key: String = "",
    val showKey: Boolean = false,
    val verifying: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
) {
    val validFormat get() = ApiKeyRepository.isValidFormat(key)
}

@HiltViewModel
class ApiKeyViewModel @Inject constructor(
    private val apiKeys: ApiKeyRepository,
    private val aiRepository: AiRepository,
) : ViewModel() {
    var state by mutableStateOf(ApiKeyUiState())
        private set

    fun setKey(key: String) {
        state = state.copy(key = key.trim(), error = null)
    }

    fun toggleVisibility() {
        state = state.copy(showKey = !state.showKey)
    }

    fun verify() {
        val candidate = state.key.trim()
        if (!ApiKeyRepository.isValidFormat(candidate) || state.verifying) return
        state = state.copy(verifying = true, error = null)
        viewModelScope.launch {
            runCatching { aiRepository.ping(candidate) }
                .onSuccess {
                    apiKeys.saveKey(candidate)
                    state = state.copy(verifying = false, success = true)
                }
                .onFailure { error ->
                    state = state.copy(
                        verifying = false,
                        error = when ((error as? AiServiceException)?.kind) {
                            AiServiceException.Kind.UNAUTHORIZED -> "That API key was rejected."
                            AiServiceException.Kind.TIMEOUT -> "The connection timed out. Try again."
                            else -> "Couldn't connect to Claude. Check your connection and key."
                        },
                    )
                }
        }
    }

    fun skip() = apiKeys.skipOnboarding()
}

sealed interface VoiceFlowState {
    data object Listening : VoiceFlowState
    data class Parsing(val transcript: String) : VoiceFlowState
    data class Confirmation(val transcript: String, val draft: ReminderDraft) : VoiceFlowState
    data class Failure(val transcript: String, val message: String) : VoiceFlowState
}

@HiltViewModel
class VoiceFlowViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderAlarmScheduler,
    private val locations: LocationRepository,
) : ViewModel() {
    private var saving = false
    var state by mutableStateOf<VoiceFlowState>(VoiceFlowState.Listening)
        private set

    fun parse(transcript: String) {
        if (state is VoiceFlowState.Parsing) return
        val cleaned = transcript.trim()
        if (cleaned.isBlank()) return
        state = VoiceFlowState.Parsing(cleaned)
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            runCatching { aiRepository.parse(cleaned) }
                .onSuccess { draft ->
                    if (draft.baseType == BaseType.LOCATION && !draft.locationName.isNullOrBlank()) {
                        viewModelScope.launch {
                            locations.prefetchNearDevice(draft.locationName)
                        }
                    }
                    delay((600L - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
                    state = VoiceFlowState.Confirmation(cleaned, draft)
                }
                .onFailure { error ->
                    state = VoiceFlowState.Failure(
                        cleaned,
                        when ((error as? AiServiceException)?.kind) {
                            AiServiceException.Kind.NO_KEY -> "Add your Claude key in Settings, then try again."
                            AiServiceException.Kind.UNAUTHORIZED -> "That Claude key was rejected. Check it in Settings."
                            AiServiceException.Kind.TIMEOUT -> "That took too long. Try again in a moment."
                            AiServiceException.Kind.SERVER -> "Claude is busy right now. Try again in a moment."
                            AiServiceException.Kind.INVALID_RESPONSE -> "I couldn't tell when or where to remind you. Add the rest yourself, or say it again."
                            else -> "Looks like you're offline. Check your connection and try again."
                        },
                    )
                }
        }
    }

    fun retry() {
        val transcript = when (val current = state) {
            is VoiceFlowState.Failure -> current.transcript
            is VoiceFlowState.Confirmation -> current.transcript
            is VoiceFlowState.Parsing -> current.transcript
            VoiceFlowState.Listening -> return
        }
        parse(transcript)
    }

    fun startOver() {
        state = VoiceFlowState.Listening
    }

    fun saveDraft(draft: ReminderDraft, onSaved: () -> Unit, onLocationRequired: () -> Unit) {
        if (saving) return
        if (draft.baseType == BaseType.LOCATION) {
            onLocationRequired()
            return
        }
        val triggerTime = draft.triggerTime ?: return
        saving = true
        viewModelScope.launch {
            runCatching {
                val reminder = ReminderEntity(
                    title = draft.title,
                    baseType = BaseType.TIME,
                    triggerTime = triggerTime,
                    isRecurring = draft.isRecurring,
                    recurrenceRule = draft.recurrenceRule,
                    alertFullScreen = draft.alertFullScreen,
                    alertSound = draft.alertSound,
                    alertHeadsUp = draft.alertHeadsUp,
                    alertLockScreen = draft.alertLockScreen,
                )
                val id = reminders.save(reminder)
                scheduler.schedule(reminder.copy(id = id))
            }.onSuccess {
                onSaved()
            }.onFailure {
                saving = false
            }
        }
    }
}
