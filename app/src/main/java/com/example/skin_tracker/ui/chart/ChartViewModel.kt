package com.example.skin_tracker.ui.chart

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.di.AppContainer
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import java.util.Calendar

enum class TimeRange(val label: String, val days: Int?, val movingAverageWindow: Int) {
    ONE_WEEK("1W", 7, 3),
    ONE_MONTH("1M", 30, 7),
    THREE_MONTHS("3M", 90, 14),
    SIX_MONTHS("6M", 180, 21),
    ONE_YEAR("1Y", 365, 30),
    ALL("All", null, 60)
}

data class ChartState(
    val category: Category = Category.FACE,
    val timeRange: TimeRange = TimeRange.ONE_MONTH,
    val photos: List<Photo> = emptyList(),
    val selectedPhoto: Photo? = null,
    val showBottomSheet: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChartViewModel(
    private val photoRepository: PhotoRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.ONE_MONTH)
    private val _selectedPhoto = MutableStateFlow<Photo?>(null)
    private val _showBottomSheet = MutableStateFlow(false)

    val state: StateFlow<ChartState> = combine(
        appContainer.sharedCategory.flatMapLatest { category ->
            photoRepository.getByCategory(category)
        },
        appContainer.sharedCategory,
        _timeRange,
        _selectedPhoto,
        _showBottomSheet
    ) { photos, category, timeRange, selectedPhoto, showBottomSheet ->
        val filtered = if (timeRange.days != null) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -timeRange.days!!)
            val cutoff = cal.timeInMillis
            photos.filter { it.capturedAt >= cutoff }
        } else {
            photos
        }
        ChartState(category, timeRange, filtered, selectedPhoto, showBottomSheet)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartState())

    fun setCategory(category: Category) {
        appContainer.setSharedCategory(category)
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
    }

    fun onPhotoSelected(photo: Photo) {
        _selectedPhoto.value = photo
        _showBottomSheet.value = true
    }

    fun dismissBottomSheet() {
        _showBottomSheet.value = false
        _selectedPhoto.value = null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return ChartViewModel(container.photoRepository, container) as T
        }
    }
}
