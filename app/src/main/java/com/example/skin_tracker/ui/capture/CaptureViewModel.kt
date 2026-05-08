package com.example.skin_tracker.ui.capture

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.util.FaceDetectorUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CaptureViewModel(
    private val photoRepository: PhotoRepository,
    private val photoFileStore: PhotoFileStore
) : ViewModel() {

    private val _category = MutableStateFlow(Category.FACE)
    val category: StateFlow<Category> = _category

    private val _pendingPhoto = MutableStateFlow<PendingPhoto?>(null)
    val pendingPhoto: StateFlow<PendingPhoto?> = _pendingPhoto

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete

    fun setCategory(cat: Category) {
        _category.value = cat
    }

    fun onImageCaptured(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees

        // Rotate the bitmap if needed
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        // Mirror horizontally for front camera so the saved photo matches
        // the mirrored preview the user saw while composing the shot
        val final = if (isFrontCamera) {
            val matrix = Matrix().apply { postScale(-1f, 1f) }
            Bitmap.createBitmap(rotated, 0, 0, rotated.width, rotated.height, matrix, true)
        } else {
            rotated
        }

        // Auto-detect face vs body
        val detected = if (FaceDetectorUtil.hasFace(final)) Category.FACE else Category.BODY
        _category.value = detected

        _pendingPhoto.value = PendingPhoto(
            bitmap = final,
            rotation = rotation
        )

        imageProxy.close()
    }

    fun savePhoto() {
        val pending = _pendingPhoto.value ?: return
        _isSaving.value = true

        viewModelScope.launch {
            try {
                val relativePath = photoFileStore.saveBitmap(pending.bitmap, 0)
                val now = System.currentTimeMillis()

                val photo = Photo(
                    uri = relativePath,
                    category = _category.value,
                    capturedAt = now,
                    importedAt = now
                )

                photoRepository.insert(photo)
                _saveComplete.value = true
                _pendingPhoto.value = null
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun retake() {
        _pendingPhoto.value = null
    }

    fun clearSaveComplete() {
        _saveComplete.value = false
    }

    data class PendingPhoto(
        val bitmap: Bitmap,
        val rotation: Int
    )

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return CaptureViewModel(container.photoRepository, container.photoFileStore) as T
        }
    }
}
