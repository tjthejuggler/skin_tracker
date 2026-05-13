package com.example.skin_tracker.ui.detail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotoDetailState(
    val photo: Photo? = null,
    val categoryPhotos: List<Photo> = emptyList(),
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
            val categoryPhotos = photoRepository.getByCategorySync(photo.category)
            val index = categoryPhotos.indexOfFirst { it.id == photoId }

            _state.value = _state.value.copy(
                photo = photo,
                categoryPhotos = categoryPhotos,
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

    fun updateCapturedAt(newCapturedAt: Long) {
        val photo = _state.value.photo ?: return
        viewModelScope.launch {
            photoRepository.updateCapturedAt(photo.id, newCapturedAt)
            loadPhoto(photo.id)
        }
    }

    fun changeCategory(newCategory: Category) {
        val photo = _state.value.photo ?: return
        viewModelScope.launch {
            // Wipe all comparisons involving this photo
            comparisonRepository.deleteByPhotoId(photo.id)
            // Update category and reset rating stats
            photoRepository.updateCategoryAndResetStats(photo.id, newCategory)
            // Reload to reflect changes
            loadPhoto(photo.id)
        }
    }

    fun setCurrentIndex(index: Int) {
        val currentPhoto = _state.value.categoryPhotos.getOrNull(index)
        _state.value = _state.value.copy(
            currentIndex = index,
            photo = currentPhoto ?: _state.value.photo
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return PhotoDetailViewModel(container.photoRepository, container.comparisonRepository, container.photoFileStore) as T
        }
    }
}
