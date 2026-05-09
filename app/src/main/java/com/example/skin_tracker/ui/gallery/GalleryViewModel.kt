package com.example.skin_tracker.ui.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.util.ExifDateReader
import com.example.skin_tracker.util.FaceDetectorUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DayGroup(
    val dateLabel: String,
    val timestamp: Long,
    val photos: List<Photo>
)

class GalleryViewModel(
    private val photoRepository: PhotoRepository,
    private val photoFileStore: PhotoFileStore,
    private val habitIntegrationRepository: HabitIntegrationRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _dayGroups = MutableStateFlow<List<DayGroup>>(emptyList())
    val dayGroups: StateFlow<List<DayGroup>> = _dayGroups.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importCount = MutableStateFlow(0)
    val importCount: StateFlow<Int> = _importCount.asStateFlow()

    init {
        viewModelScope.launch {
            photoRepository.getAll().collect { photoList ->
                _photos.value = photoList
                _dayGroups.value = groupByDay(photoList)
            }
        }
    }

    /**
     * Import photos from gallery URIs. Category is auto-detected per photo
     * using face detection (FACE if a face is found, BODY otherwise).
     */
    fun importFromGallery(uris: List<Uri>, context: android.content.Context) {
        _isImporting.value = true
        _importCount.value = 0

        viewModelScope.launch {
            var count = 0
            for (uri in uris) {
                try {
                    val relativePath = photoFileStore.copyFromUri(uri)
                    val capturedAt = ExifDateReader.readCapturedAt(context, uri)
                    val now = System.currentTimeMillis()

                    // Auto-detect category from the saved file
                    val category = photoFileStore.loadBitmap(relativePath)?.let {
                        if (FaceDetectorUtil.hasFace(it)) Category.FACE else Category.BODY
                    } ?: Category.BODY

                    val photo = Photo(
                        uri = relativePath,
                        category = category,
                        capturedAt = capturedAt,
                        importedAt = now
                    )
                    photoRepository.insert(photo)
                    habitIntegrationRepository.sendHabitIncrement(HabitIntegrationRepository.Slot.PHOTO_ADDED)
                    count++
                    _importCount.value = count
                } catch (e: Exception) {
                    // Skip failed imports
                }
            }
            _isImporting.value = false
        }
    }

    private fun groupByDay(photos: List<Photo>): List<DayGroup> {
        val sdf = java.text.SimpleDateFormat("EEEE, MMM d, yyyy", java.util.Locale.getDefault())
        return photos
            .groupBy { photo ->
                // Group by day (midnight)
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = photo.capturedAt
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .map { (timestamp, dayPhotos) ->
                DayGroup(
                    dateLabel = sdf.format(java.util.Date(timestamp)),
                    timestamp = timestamp,
                    photos = dayPhotos
                )
            }
            .sortedByDescending { it.timestamp }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return GalleryViewModel(container.photoRepository, container.photoFileStore, container.habitIntegrationRepository) as T
        }
    }
}
