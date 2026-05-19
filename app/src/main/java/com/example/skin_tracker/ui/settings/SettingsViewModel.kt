package com.example.skin_tracker.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.debug.DebugPreferences
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class HabitSlotSelection(
    val habitId: String = "",
    val habitName: String = ""
) {
    val isSet: Boolean get() = habitId.isNotBlank()
    val displayName: String get() = habitName.ifBlank { habitId }
}

data class AppStats(
    val totalComparisons: Int = 0,
    val faceComparisons: Int = 0,
    val bodyComparisons: Int = 0,
    val totalPhotos: Int = 0,
    val facePhotos: Int = 0,
    val bodyPhotos: Int = 0,
    val topRatedPhoto: Photo? = null,
    val topRatedFacePhoto: Photo? = null,
    val topRatedBodyPhoto: Photo? = null,
    val longestStreakDays: Int = 0,
    val currentStreakDays: Int = 0,
    val isLoading: Boolean = true
)

data class SettingsUiState(
    val habitList: List<com.example.skin_tracker.domain.model.HabitEntry> = emptyList(),
    val isLoadingHabits: Boolean = false,
    val habitAppUnavailable: Boolean = false,
    val photoAddedHabit: HabitSlotSelection = HabitSlotSelection(),
    // ── Debug Mode ────────────────────────────────────────────────────────
    val debugModeEnabled: Boolean = false,
    val debugFileDirUri: String = "",
    // ── Stats ─────────────────────────────────────────────────────────────
    val stats: AppStats = AppStats()
)

class SettingsViewModel(
    private val habitRepo: HabitIntegrationRepository,
    private val debugPrefs: DebugPreferences,
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository
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
        loadStats()
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

    fun selectHabit(slot: HabitIntegrationRepository.Slot, entry: com.example.skin_tracker.domain.model.HabitEntry) {
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

    // ── Stats ──────────────────────────────────────────────────────────────

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stats = _uiState.value.stats.copy(isLoading = true))

            val totalComparisons = comparisonRepository.totalCount()
            val faceComparisons = comparisonRepository.totalCountByCategory(Category.FACE)
            val bodyComparisons = comparisonRepository.totalCountByCategory(Category.BODY)

            val facePhotos = photoRepository.countByCategory(Category.FACE)
            val bodyPhotos = photoRepository.countByCategory(Category.BODY)

            val topRated = photoRepository.getTopRated()
            val topRatedFace = photoRepository.getTopRatedByCategory(Category.FACE)
            val topRatedBody = photoRepository.getTopRatedByCategory(Category.BODY)

            val capturedAtList = photoRepository.getAllCapturedAtAsc()
            val (longestStreak, currentStreak) = computeStreaks(capturedAtList)

            _uiState.value = _uiState.value.copy(
                stats = AppStats(
                    totalComparisons = totalComparisons,
                    faceComparisons = faceComparisons,
                    bodyComparisons = bodyComparisons,
                    totalPhotos = facePhotos + bodyPhotos,
                    facePhotos = facePhotos,
                    bodyPhotos = bodyPhotos,
                    topRatedPhoto = topRated,
                    topRatedFacePhoto = topRatedFace,
                    topRatedBodyPhoto = topRatedBody,
                    longestStreakDays = longestStreak,
                    currentStreakDays = currentStreak,
                    isLoading = false
                )
            )
        }
    }

    /**
     * Computes longest and current consecutive-day streaks from a sorted list of timestamps.
     * A "day" is a calendar date in the device's local timezone.
     */
    private fun computeStreaks(capturedAtMillis: List<Long>): Pair<Int, Int> {
        if (capturedAtMillis.isEmpty()) return Pair(0, 0)

        // Deduplicate to unique calendar days
        val days = capturedAtMillis.map { toDayEpoch(it) }.distinct().sorted()

        var longest = 1
        var current = 1
        for (i in 1 until days.size) {
            if (days[i] - days[i - 1] == 1L) {
                current++
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }

        // Check if the streak is still active (last day is today or yesterday)
        val todayEpoch = toDayEpoch(System.currentTimeMillis())
        val lastDayEpoch = days.last()
        val activeStreak = if (todayEpoch - lastDayEpoch <= 1L) current else 0

        return Pair(longest, activeStreak)
    }

    /** Returns the number of days since epoch (UTC midnight) for a given timestamp. */
    private fun toDayEpoch(millis: Long): Long =
        TimeUnit.MILLISECONDS.toDays(millis)

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
                container.debugPreferences,
                container.photoRepository,
                container.comparisonRepository
            ) as T
        }
    }
}
