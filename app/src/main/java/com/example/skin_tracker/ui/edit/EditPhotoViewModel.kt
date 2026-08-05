package com.example.skin_tracker.ui.edit

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

data class EditPhotoState(
    val photo: Photo? = null,
    val isDone: Boolean = false,
    val isLoading: Boolean = false
)

class EditPhotoViewModel(
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository,
    private val photoFileStore: PhotoFileStore,
    private val requestAutoBackup: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(EditPhotoState())
    val state: StateFlow<EditPhotoState> = _state.asStateFlow()

    fun loadPhoto(photoId: Long) {
        viewModelScope.launch {
            val photo = photoRepository.getById(photoId) ?: return@launch
            _state.value = _state.value.copy(photo = photo)
        }
    }

    fun applyCropAndRotate(
        cropLeft: Int,
        cropTop: Int,
        cropRight: Int,
        cropBottom: Int,
        rotationDegrees: Int
    ) {
        val photo = _state.value.photo ?: return
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            photoFileStore.crop(photo.uri, cropLeft, cropTop, cropRight, cropBottom, rotationDegrees)
            requestAutoBackup()
            _state.value = _state.value.copy(isLoading = false, isDone = true)
        }
    }

    fun changeCategory(newCategory: Category) {
        val photo = _state.value.photo ?: return
        viewModelScope.launch {
            // Wipe all comparisons involving this photo
            comparisonRepository.deleteByPhotoId(photo.id)
            // Update category and reset rating stats
            photoRepository.updateCategoryAndResetStats(photo.id, newCategory)
            requestAutoBackup()
            // Reload to reflect changes
            loadPhoto(photo.id)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as SkinTrackerApp
            val container = app.container
            return EditPhotoViewModel(
                container.photoRepository,
                container.comparisonRepository,
                container.photoFileStore,
                app::requestAutoBackup
            ) as T
        }
    }
}
