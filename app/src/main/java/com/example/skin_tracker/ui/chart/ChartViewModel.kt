package com.example.skin_tracker.ui.chart

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeRange(val label: String, val days: Int?) {
    ONE_WEEK("1W", 7),
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365),
    ALL("All", null)
}

data class ChartState(
    val category: Category = Category.FACE,
    val timeRange: TimeRange = TimeRange.ALL,
    val photos: List<Photo> = emptyList(),
    val selectedPhoto: Photo? = null,
    val showBottomSheet: Boolean = false
)

class ChartViewModel(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChartState())
    val state: StateFlow<ChartState> = _state.asStateFlow()

    init {
        loadPhotos()
    }

    fun setCategory(category: Category) {
        _state.value = _state.value.copy(category = category)
        loadPhotos()
    }

    fun setTimeRange(range: TimeRange) {
        _state.value = _state.value.copy(timeRange = range)
        loadPhotos()
    }

    fun onPhotoSelected(photo: Photo) {
        _state.value = _state.value.copy(
            selectedPhoto = photo,
            showBottomSheet = true
        )
    }

    fun dismissBottomSheet() {
        _state.value = _state.value.copy(
            showBottomSheet = false,
            selectedPhoto = null
        )
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            val state = _state.value
            val allPhotos = photoRepository.getByCategorySync(state.category)

            val filtered = if (state.timeRange.days != null) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -state.timeRange.days!!)
                val cutoff = cal.timeInMillis
                allPhotos.filter { it.capturedAt >= cutoff }
            } else {
                allPhotos
            }

            _state.value = state.copy(photos = filtered)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return ChartViewModel(container.photoRepository) as T
        }
    }
}
