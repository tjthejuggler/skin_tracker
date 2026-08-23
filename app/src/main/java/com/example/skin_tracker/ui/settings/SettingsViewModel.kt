package com.example.skin_tracker.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.backup.BackupManager
import com.example.skin_tracker.data.backup.BackupPreferences
import com.example.skin_tracker.data.debug.DebugPreferences
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class HabitSlotSelection(
    val habitId: String = "",
    val habitName: String = ""
) {
    val isSet: Boolean get() = habitId.isNotBlank()
    val displayName: String get() = habitName.ifBlank { habitId }
}

data class SettingsUiState(
    val habitList: List<com.example.skin_tracker.domain.model.HabitEntry> = emptyList(),
    val isLoadingHabits: Boolean = false,
    val habitAppUnavailable: Boolean = false,
    val photoAddedHabit: HabitSlotSelection = HabitSlotSelection(),
    val faceScoreHabit: HabitSlotSelection = HabitSlotSelection(),
    val bodyScoreHabit: HabitSlotSelection = HabitSlotSelection(),
    val scoreSyncInProgress: Boolean = false,
    // ── Debug Mode ────────────────────────────────────────────────────────
    val debugModeEnabled: Boolean = false,
    val debugFileDirUri: String = "",
    // ── Backup ────────────────────────────────────────────────────────────
    val backupDirUri: String = "",
    val autoBackupEnabled: Boolean = true,
    val lastBackupAt: Long = 0L,
    val lastBackupPhotoCount: Int = 0,
    val backupInProgress: Boolean = false,
    val restoreInProgress: Boolean = false
) {
    val backupConfigured: Boolean get() = backupDirUri.isNotBlank()
}

class SettingsViewModel(
    private val habitRepo: HabitIntegrationRepository,
    private val debugPrefs: DebugPreferences,
    private val backupPrefs: BackupPreferences,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    /** One-off user-facing messages (backup/restore results) surfaced as snackbars/toasts. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

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
        // Observe backup prefs snapshot so UI reacts to folder/toggle/last-backup changes
        viewModelScope.launch {
            backupPrefs.snapshot.collect { snap ->
                _uiState.value = _uiState.value.copy(
                    backupDirUri = snap.backupDirUri,
                    autoBackupEnabled = snap.autoBackupEnabled,
                    lastBackupAt = snap.lastBackupAt,
                    lastBackupPhotoCount = snap.lastBackupPhotoCount
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

    fun selectHabit(slot: HabitIntegrationRepository.Slot, entry: com.example.skin_tracker.domain.model.HabitEntry) {
        habitRepo.setHabit(slot, entry)
        val selection = HabitSlotSelection(entry.habitId, entry.habitName)
        _uiState.value = when (slot) {
            HabitIntegrationRepository.Slot.PHOTO_ADDED -> _uiState.value.copy(photoAddedHabit = selection)
            HabitIntegrationRepository.Slot.FACE_SCORE  -> _uiState.value.copy(faceScoreHabit = selection)
            HabitIntegrationRepository.Slot.BODY_SCORE  -> _uiState.value.copy(bodyScoreHabit = selection)
        }
    }

    fun clearHabit(slot: HabitIntegrationRepository.Slot) {
        habitRepo.clearHabit(slot)
        _uiState.value = when (slot) {
            HabitIntegrationRepository.Slot.PHOTO_ADDED -> _uiState.value.copy(photoAddedHabit = HabitSlotSelection())
            HabitIntegrationRepository.Slot.FACE_SCORE  -> _uiState.value.copy(faceScoreHabit = HabitSlotSelection())
            HabitIntegrationRepository.Slot.BODY_SCORE  -> _uiState.value.copy(bodyScoreHabit = HabitSlotSelection())
        }
    }

    /**
     * Force-push the full score history to Tail now (bypasses the daily
     * throttle). Surfaces the outcome as a snackbar message.
     */
    fun sendScoresNow() {
        if (_uiState.value.scoreSyncInProgress) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scoreSyncInProgress = true)
            val result = habitRepo.syncScoreHistory(force = true)
            _uiState.value = _uiState.value.copy(scoreSyncInProgress = false)
            val msg = when {
                result.slotsSynced.isEmpty() ->
                    "No score habits configured — pick a habit for Face or Body Skin Score first."
                result.datesSent > 0 ->
                    "Scores sent to Tail: ${result.datesSent} day${if (result.datesSent != 1) "s" else ""} updated."
                else -> "Scores already up to date — nothing to send."
            }
            _messages.emit(msg)
        }
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

    // ── Backup & Restore ───────────────────────────────────────────────────

    /** Persist the SAF folder the user picked and immediately run a first full backup. */
    fun setBackupDir(uriString: String) {
        backupPrefs.backupDirUri = uriString
        runManualBackup()
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        backupPrefs.autoBackupEnabled = enabled
    }

    /** Force a backup now, ignoring the auto-backup toggle. */
    fun runManualBackup() {
        if (_uiState.value.backupInProgress) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backupInProgress = true)
            val msg = when (val r = backupManager.backup(force = true)) {
                is BackupManager.Result.Success ->
                    "Backup complete: ${r.photoCount} photos (${r.imagesCopied} new images copied)"
                is BackupManager.Result.Skipped -> "Backup skipped: ${r.reason}"
                is BackupManager.Result.Failure -> "Backup failed: ${r.message}"
            }
            _uiState.value = _uiState.value.copy(backupInProgress = false)
            _messages.emit(msg)
        }
    }

    /** Restore from the configured backup folder. Replaces all current photos and comparisons. */
    fun runRestore() {
        if (_uiState.value.restoreInProgress) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(restoreInProgress = true)
            val msg = when (val r = backupManager.restore()) {
                is BackupManager.RestoreResult.Success ->
                    "Restore complete: ${r.photoCount} photos, ${r.imagesRestored} images, ${r.comparisonCount} comparisons"
                is BackupManager.RestoreResult.Failure -> "Restore failed: ${r.message}"
            }
            _uiState.value = _uiState.value.copy(restoreInProgress = false)
            _messages.emit(msg)
        }
    }

    private fun buildInitialState(): SettingsUiState {
        fun selection(slot: HabitIntegrationRepository.Slot) = HabitSlotSelection(
            habitId = habitRepo.getHabitId(slot),
            habitName = habitRepo.getHabitName(slot)
        )
        return SettingsUiState(
            photoAddedHabit = selection(HabitIntegrationRepository.Slot.PHOTO_ADDED),
            faceScoreHabit = selection(HabitIntegrationRepository.Slot.FACE_SCORE),
            bodyScoreHabit = selection(HabitIntegrationRepository.Slot.BODY_SCORE),
            debugModeEnabled = debugPrefs.debugModeEnabled,
            debugFileDirUri = debugPrefs.debugFileDirUri,
            backupDirUri = backupPrefs.backupDirUri,
            autoBackupEnabled = backupPrefs.autoBackupEnabled,
            lastBackupAt = backupPrefs.lastBackupAt,
            lastBackupPhotoCount = backupPrefs.lastBackupPhotoCount
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return SettingsViewModel(
                container.habitIntegrationRepository,
                container.debugPreferences,
                container.backupPreferences,
                container.backupManager
            ) as T
        }
    }
}
