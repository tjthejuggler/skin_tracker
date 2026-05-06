package com.example.skin_tracker.ui.detail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotoDetailState(
    val photo: Photo? = null,
    val sameDayPhotos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val isDeleted: Boolean = false
)

class PhotoDetailViewModel(
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository,
    private val photoFileStore: PhotoFileStore
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoDetailState())
    val state: StateFlow<PhotoDetailState> = _state.asStateFlow()

    fun loadPhoto(photoId: Long) {
        viewModelScope.launch {
            val photo = photoRepository.getById(photoId) ?: return@launch
            val allPhotos = photoRepository.getByCategorySync(photo.category)

            // Find photos from the same day
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = photo.capturedAt
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000

            val sameDay = allPhotos.filter { it.capturedAt in dayStart until dayEnd }
            val index = sameDay.indexOfFirst { it.id == photoId }

            _state.value = _state.value.copy(
                photo = photo,
                sameDayPhotos = sameDay,
                currentIndex = if (index >= 0) index else 0
            )
        }
    }

    fun deletePhoto() {
        val photo = _state.value.photo ?: return
        viewModelScope.launch {
            // Delete all comparisons involving this photo
            comparisonRepository.deleteByPhotoId(photo.id)
            // Delete the file
            photoFileStore.delete(photo.uri)
            // Hard delete the photo record
            photoRepository.hardDelete(photo.id)
            _state.value = _state.value.copy(isDeleted = true)
        }
    }

    fun setCurrentIndex(index: Int) {
        _state.value = _state.value.copy(currentIndex = index)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return PhotoDetailViewModel(container.photoRepository, container.comparisonRepository, container.photoFileStore) as T
        }
    }
}
