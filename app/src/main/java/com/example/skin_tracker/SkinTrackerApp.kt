package com.example.skin_tracker

import android.app.Application
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class SkinTrackerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Clean up orphaned records on startup
        appScope.launch {
            cleanupOrphanedRecords(
                container.photoRepository,
                container.comparisonRepository
            )
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
}
