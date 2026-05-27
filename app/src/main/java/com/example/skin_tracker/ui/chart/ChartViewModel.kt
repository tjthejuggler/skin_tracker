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
    val periodOffset: Int = 0,
    val photos: List<Photo> = emptyList(),
    val selectedPhoto: Photo? = null,
    val showBottomSheet: Boolean = false,
    val periodLabel: String = ""
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChartViewModel(
    private val photoRepository: PhotoRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.ONE_MONTH)
    private val _periodOffset = MutableStateFlow(0)
    private val _selectedPhoto = MutableStateFlow<Photo?>(null)
    private val _showBottomSheet = MutableStateFlow(false)

    private data class ChartData(
        val photos: List<Photo>,
        val category: Category,
        val timeRange: TimeRange,
        val periodOffset: Int
    )

    val state: StateFlow<ChartState> = combine(
        combine(
            appContainer.sharedCategory.flatMapLatest { category ->
                photoRepository.getByCategory(category)
            },
            appContainer.sharedCategory,
            _timeRange,
            _periodOffset
        ) { photos, category, timeRange, offset ->
            ChartData(photos, category, timeRange, offset)
        },
        _selectedPhoto,
        _showBottomSheet
    ) { data, selectedPhoto, showBottomSheet ->
        val (filtered, label) = filterByTimeRange(data.photos, data.timeRange, data.periodOffset)
        ChartState(data.category, data.timeRange, data.periodOffset, filtered, selectedPhoto, showBottomSheet, label)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartState())

    private fun filterByTimeRange(
        photos: List<Photo>,
        timeRange: TimeRange,
        offset: Int
    ): Pair<List<Photo>, String> {
        if (timeRange == TimeRange.ALL) {
            return Pair(photos, "All Time")
        }

        val days = timeRange.days ?: return Pair(photos, "All Time")

        // Calculate the date window shifted by offset
        val endCal = Calendar.getInstance()
        // Step back by offset * days
        endCal.add(Calendar.DAY_OF_YEAR, -(offset * days))
        val endMs = endCal.timeInMillis

        val startCal = Calendar.getInstance()
        startCal.timeInMillis = endMs
        startCal.add(Calendar.DAY_OF_YEAR, -days)
        val startMs = startCal.timeInMillis

        val filtered = photos.filter { it.capturedAt in startMs until endMs }

        val label = buildPeriodLabel(timeRange, startCal, endCal)
        return Pair(filtered, label)
    }

    private fun buildPeriodLabel(timeRange: TimeRange, startCal: Calendar, endCal: Calendar): String {
        val sdf = java.text.SimpleDateFormat(when (timeRange) {
            TimeRange.ONE_WEEK -> "MMM d"
            TimeRange.ONE_MONTH -> "MMM yyyy"
            TimeRange.THREE_MONTHS -> "MMM yyyy"
            TimeRange.SIX_MONTHS -> "MMM yyyy"
            TimeRange.ONE_YEAR -> "yyyy"
            TimeRange.ALL -> "MMM yyyy"
        }, java.util.Locale.getDefault())

        return when (timeRange) {
            TimeRange.ONE_WEEK -> {
                val startFmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                val endFmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                "${startFmt.format(startCal.time)} – ${endFmt.format(endCal.time)}"
            }
            TimeRange.ONE_MONTH -> {
                val fmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                // Show the month that the period ends in (the "current" month for this offset)
                fmt.format(endCal.time)
            }
            TimeRange.THREE_MONTHS -> {
                val fmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                "${fmt.format(startCal.time)} – ${fmt.format(endCal.time)}"
            }
            TimeRange.SIX_MONTHS -> {
                val fmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                "${fmt.format(startCal.time)} – ${fmt.format(endCal.time)}"
            }
            TimeRange.ONE_YEAR -> {
                val fmt = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
                fmt.format(endCal.time)
            }
            TimeRange.ALL -> "All Time"
        }
    }

    fun setCategory(category: Category) {
        appContainer.setSharedCategory(category)
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
        _periodOffset.value = 0
    }

    fun stepBack() {
        _periodOffset.value = _periodOffset.value + 1
    }

    fun stepForward() {
        if (_periodOffset.value > 0) {
            _periodOffset.value = _periodOffset.value - 1
        }
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
