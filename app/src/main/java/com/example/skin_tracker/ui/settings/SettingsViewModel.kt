package com.example.skin_tracker.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.debug.DebugPreferences
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import com.example.skin_tracker.domain.model.HabitEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HabitSlotSelection(
    val habitId: String = "",
    val habitName: String = ""
) {
    val isSet: Boolean get() = habitId.isNotBlank()
    val displayName: String get() = habitName.ifBlank { habitId }
}

data class SettingsUiState(
    val habitList: List<HabitEntry> = emptyList(),
    val isLoadingHabits: Boolean = false,
    val habitAppUnavailable: Boolean = false,
    val photoAddedHabit: HabitSlotSelection = HabitSlotSelection(),
    // ── Debug Mode ────────────────────────────────────────────────────────
    val debugModeEnabled: Boolean = false,
    val debugFileDirUri: String = ""
)

class SettingsViewModel(
    private val habitRepo: HabitIntegrationRepository,
    private val debugPrefs: DebugPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        // Observe debug prefs snapshot so UI reacts to changes
        viewModelScope.launch {
            debugPrefs.snapshot.collect { snap ->
                _uiState.value = _uiState.value.copy(
                    debugModeEnabled = snap.debugModeEnabled,
                    debugFileDirUri = snap.debugFileDirUri
                )
            }
        }
    }

    fun loadHabits() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHabits = true, habitAppUnavailable = false)
            val habits = habitRepo.fetchHabits()
            _uiState.value = _uiState.value.copy(
                habitList = habits,
                isLoadingHabits = false,
                habitAppUnavailable = habits.isEmpty()
            )
        }
    }

    fun selectHabit(slot: HabitIntegrationRepository.Slot, entry: HabitEntry) {
        habitRepo.setHabit(slot, entry)
        val selection = HabitSlotSelection(entry.habitId, entry.habitName)
        _uiState.value = _uiState.value.copy(photoAddedHabit = selection)
    }

    fun clearHabit(slot: HabitIntegrationRepository.Slot) {
        habitRepo.clearHabit(slot)
        _uiState.value = _uiState.value.copy(photoAddedHabit = HabitSlotSelection())
    }

    // ── Debug Mode ─────────────────────────────────────────────────────────

    fun setDebugModeEnabled(enabled: Boolean) {
        debugPrefs.debugModeEnabled = enabled
    }

    fun setDebugFileDir(uriString: String) {
        debugPrefs.debugFileDirUri = uriString
    }

    fun clearDebugFileDir() {
        debugPrefs.debugFileDirUri = ""
    }

    private fun buildInitialState(): SettingsUiState {
        val slot = HabitIntegrationRepository.Slot.PHOTO_ADDED
        return SettingsUiState(
            photoAddedHabit = HabitSlotSelection(
                habitId = habitRepo.getHabitId(slot),
                habitName = habitRepo.getHabitName(slot)
            ),
            debugModeEnabled = debugPrefs.debugModeEnabled,
            debugFileDirUri = debugPrefs.debugFileDirUri
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return SettingsViewModel(
                container.habitIntegrationRepository,
                container.debugPreferences
            ) as T
        }
    }
}
