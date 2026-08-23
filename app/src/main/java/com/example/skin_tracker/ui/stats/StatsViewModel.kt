package com.example.skin_tracker.ui.stats

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class AppStats(
    val totalComparisons: Int = 0,
    val faceComparisons: Int = 0,
    val bodyComparisons: Int = 0,
    // Total recorded time spent deciding comparisons (ms); face and body tracked separately
    val totalComparisonTimeMs: Long = 0L,
    val faceComparisonTimeMs: Long = 0L,
    val bodyComparisonTimeMs: Long = 0L,
    // Time per comparison rate (ms), averaged over comparisons that have a recorded duration
    val avgComparisonMs: Double = 0.0,
    val avgFaceComparisonMs: Double = 0.0,
    val avgBodyComparisonMs: Double = 0.0,
    val totalPhotos: Int = 0,
    val facePhotos: Int = 0,
    val bodyPhotos: Int = 0,
    val topRatedPhoto: Photo? = null,
    val topRatedFacePhoto: Photo? = null,
    val topRatedBodyPhoto: Photo? = null,
    // Top-rated per time window
    val topRatedThisWeek: Photo? = null,
    val topRatedThisMonth: Photo? = null,
    val topRatedThisYear: Photo? = null,
    // Activity counts per time window
    val photosThisWeek: Int = 0,
    val photosThisMonth: Int = 0,
    val comparisonsThisWeek: Int = 0,
    val comparisonsThisMonth: Int = 0,
    // Streaks
    val longestStreakDays: Int = 0,
    val currentStreakDays: Int = 0,
    // Averages
    val avgComparisonsPerDay: Double = 0.0,
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(AppStats(isLoading = true))
    val stats: StateFlow<AppStats> = _stats.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _stats.value = _stats.value.copy(isLoading = true)

            val now = System.currentTimeMillis()
            val weekAgo = now - 7L * 24 * 60 * 60 * 1000
            val monthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
            val yearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis

            val totalComparisons = comparisonRepository.totalCount()
            val faceComparisons = comparisonRepository.totalCountByCategory(Category.FACE)
            val bodyComparisons = comparisonRepository.totalCountByCategory(Category.BODY)

            val totalComparisonTimeMs = comparisonRepository.totalDuration()
            val faceComparisonTimeMs = comparisonRepository.totalDurationByCategory(Category.FACE)
            val bodyComparisonTimeMs = comparisonRepository.totalDurationByCategory(Category.BODY)

            // Rate: time per comparison, averaged only over comparisons with a recorded duration
            val trackedTotal = comparisonRepository.trackedCount()
            val trackedFace = comparisonRepository.trackedCountByCategory(Category.FACE)
            val trackedBody = comparisonRepository.trackedCountByCategory(Category.BODY)
            val avgComparisonMs = if (trackedTotal > 0) totalComparisonTimeMs.toDouble() / trackedTotal else 0.0
            val avgFaceComparisonMs = if (trackedFace > 0) faceComparisonTimeMs.toDouble() / trackedFace else 0.0
            val avgBodyComparisonMs = if (trackedBody > 0) bodyComparisonTimeMs.toDouble() / trackedBody else 0.0

            val facePhotos = photoRepository.countByCategory(Category.FACE)
            val bodyPhotos = photoRepository.countByCategory(Category.BODY)

            val topRated = photoRepository.getTopRated()
            val topRatedFace = photoRepository.getTopRatedByCategory(Category.FACE)
            val topRatedBody = photoRepository.getTopRatedByCategory(Category.BODY)

            val topRatedWeek = photoRepository.getTopRatedSince(weekAgo)
            val topRatedMonth = photoRepository.getTopRatedSince(monthAgo)
            val topRatedYear = photoRepository.getTopRatedSince(yearAgo)

            val photosThisWeek = photoRepository.countSince(weekAgo)
            val photosThisMonth = photoRepository.countSince(monthAgo)
            val comparisonsThisWeek = comparisonRepository.countSince(weekAgo)
            val comparisonsThisMonth = comparisonRepository.countSince(monthAgo)

            val capturedAtList = photoRepository.getAllCapturedAtAsc()
            val (longestStreak, currentStreak) = computeStreaks(capturedAtList)

            // Average comparisons per active day (days that have at least one comparison)
            val comparedAtList = comparisonRepository.getAllComparedAtAsc()
            val avgComparisonsPerDay = if (comparedAtList.isNotEmpty()) {
                val activeDays = comparedAtList.map { toDayEpoch(it) }.distinct().size
                if (activeDays > 0) totalComparisons.toDouble() / activeDays else 0.0
            } else 0.0

            _stats.value = AppStats(
                totalComparisons = totalComparisons,
                faceComparisons = faceComparisons,
                bodyComparisons = bodyComparisons,
                totalComparisonTimeMs = totalComparisonTimeMs,
                faceComparisonTimeMs = faceComparisonTimeMs,
                bodyComparisonTimeMs = bodyComparisonTimeMs,
                avgComparisonMs = avgComparisonMs,
                avgFaceComparisonMs = avgFaceComparisonMs,
                avgBodyComparisonMs = avgBodyComparisonMs,
                totalPhotos = facePhotos + bodyPhotos,
                facePhotos = facePhotos,
                bodyPhotos = bodyPhotos,
                topRatedPhoto = topRated,
                topRatedFacePhoto = topRatedFace,
                topRatedBodyPhoto = topRatedBody,
                topRatedThisWeek = topRatedWeek,
                topRatedThisMonth = topRatedMonth,
                topRatedThisYear = topRatedYear,
                photosThisWeek = photosThisWeek,
                photosThisMonth = photosThisMonth,
                comparisonsThisWeek = comparisonsThisWeek,
                comparisonsThisMonth = comparisonsThisMonth,
                longestStreakDays = longestStreak,
                currentStreakDays = currentStreak,
                avgComparisonsPerDay = avgComparisonsPerDay,
                isLoading = false
            )
        }
    }

    /**
     * Computes longest and current consecutive-day streaks from a sorted list of timestamps.
     * A "day" is a calendar date in the device's local timezone.
     */
    private fun computeStreaks(capturedAtMillis: List<Long>): Pair<Int, Int> {
        if (capturedAtMillis.isEmpty()) return Pair(0, 0)

        // Deduplicate to unique calendar days
        val days = capturedAtMillis.map { toDayEpoch(it) }.distinct().sorted()

        var longest = 1
        var current = 1
        for (i in 1 until days.size) {
            if (days[i] - days[i - 1] == 1L) {
                current++
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }

        // Check if the streak is still active (last day is today or yesterday)
        val todayEpoch = toDayEpoch(System.currentTimeMillis())
        val lastDayEpoch = days.last()
        val activeStreak = if (todayEpoch - lastDayEpoch <= 1L) current else 0

        return Pair(longest, activeStreak)
    }

    /** Returns the number of days since epoch (UTC midnight) for a given timestamp. */
    private fun toDayEpoch(millis: Long): Long =
        TimeUnit.MILLISECONDS.toDays(millis)

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return StatsViewModel(
                container.photoRepository,
                container.comparisonRepository
            ) as T
        }
    }
}
