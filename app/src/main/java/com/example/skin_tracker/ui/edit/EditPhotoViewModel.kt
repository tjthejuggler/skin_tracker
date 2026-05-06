package com.example.skin_tracker.ui.edit

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
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
    private val photoFileStore: PhotoFileStore
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
            _state.value = _state.value.copy(isLoading = false, isDone = true)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return EditPhotoViewModel(container.photoRepository, container.photoFileStore) as T
        }
    }
}
