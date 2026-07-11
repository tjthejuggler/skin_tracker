package com.example.skin_tracker

import android.app.Application
import android.util.Log
import com.example.skin_tracker.data.backup.BackupManager
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class SkinTrackerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: AppContainer by lazy { AppContainer(this) }

    /** Debounce so a burst of writes coalesces into a single backup pass. */
    @Volatile
    private var pendingBackupJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Clean up orphaned records on startup
            cleanupOrphanedRecords(
                container.photoRepository,
                container.comparisonRepository
            )
            // Back up on every launch. This is the guaranteed safety net: it captures any
            // changes made in previous sessions (the app fully closes when backgrounded).
            runAutoBackup(reason = "app-start")
        }
    }

    /**
     * Request a debounced automatic backup after a data change.
     * Multiple calls within [BACKUP_DEBOUNCE_MS] collapse into one backup pass.
     * Safe to call from any ViewModel after inserting/updating/deleting a photo or comparison.
     */
    fun requestAutoBackup() {
        pendingBackupJob?.cancel()
        pendingBackupJob = appScope.launch {
            delay(BACKUP_DEBOUNCE_MS)
            runAutoBackup(reason = "data-change")
        }
    }

    private suspend fun runAutoBackup(reason: String) {
        when (val result = container.backupManager.backup()) {
            is BackupManager.Result.Success ->
                Log.i(TAG, "Auto-backup ($reason): ${result.photoCount} photos, ${result.imagesCopied} new images")
            is BackupManager.Result.Skipped ->
                Log.d(TAG, "Auto-backup ($reason) skipped: ${result.reason}")
            is BackupManager.Result.Failure ->
                Log.w(TAG, "Auto-backup ($reason) failed: ${result.message}")
        }
    }

    /**
     * Remove photos whose image files no longer exist on disk,
     * and delete any comparisons that reference those photos.
     */
    private suspend fun cleanupOrphanedRecords(
        photoRepository: PhotoRepository,
        comparisonRepository: ComparisonRepository
    ) {
        val allPhotos = photoRepository.getAllSync()
        for (photo in allPhotos) {
            val file = File(photo.uri)
            if (!file.exists()) {
                // Delete comparisons referencing this photo
                comparisonRepository.deleteByPhotoId(photo.id)
                // Hard delete the photo record
                photoRepository.hardDelete(photo.id)
            }
        }
    }

    companion object {
        private const val TAG = "SkinTrackerApp"
        private const val BACKUP_DEBOUNCE_MS = 2000L
    }
}
