package com.example.skin_tracker.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
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
    val photoAddedHabit: HabitSlotSelection = HabitSlotSelection()
)

class SettingsViewModel(
    private val habitRepo: HabitIntegrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<SettingsUiState> = _uiState

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

    private fun buildInitialState(): SettingsUiState {
        val slot = HabitIntegrationRepository.Slot.PHOTO_ADDED
        return SettingsUiState(
            photoAddedHabit = HabitSlotSelection(
                habitId = habitRepo.getHabitId(slot),
                habitName = habitRepo.getHabitName(slot)
            )
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return SettingsViewModel(container.habitIntegrationRepository) as T
        }
    }
}
