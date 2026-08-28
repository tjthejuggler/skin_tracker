package com.example.skin_tracker.ui.chart

import android.app.Application
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.NoteRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.di.AppContainer
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.DayNote
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.util.FaceNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TimeRange(val label: String, val days: Int?, val movingAverageWindow: Int) {
    ONE_WEEK("1W", 7, 3),
    ONE_MONTH("1M", 30, 7),
    THREE_MONTHS("3M", 90, 14),
    SIX_MONTHS("6M", 180, 21),
    ONE_YEAR("1Y", 365, 30),
    ALL("All", null, 60)
}

/** How the face animation orders the photos when playing. */
enum class PlayOrder(val label: String) {
    TIME_ASC("Time ↑"),
    TIME_DESC("Time ↓"),
    QUALITY_ASC("Quality ↑"),
    QUALITY_DESC("Quality ↓")
}

/** A single frame in the face animation. */
data class FaceAnimationFrame(
    val photo: Photo,
    val bitmap: Bitmap?,
    val dateLabel: String
)

/** Internal mutable animation state. */
private data class AnimationState(
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val currentIndex: Int = 0,
    val frames: List<FaceAnimationFrame> = emptyList()
)

data class ChartState(
    val category: Category = Category.FACE,
    val timeRange: TimeRange = TimeRange.ONE_MONTH,
    val periodOffset: Int = 0,
    val photos: List<Photo> = emptyList(),
    val notes: List<DayNote> = emptyList(),
    val selectedPhoto: Photo? = null,
    val showBottomSheet: Boolean = false,
    val periodLabel: String = "",
    // ── Animation ────────────────────────────────────────────────────────
    val animationPreparing: Boolean = false,
    val animationPlaying: Boolean = false,
    val animationIndex: Int = 0,
    val animationFrames: List<FaceAnimationFrame> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChartViewModel(
    private val photoRepository: PhotoRepository,
    private val noteRepository: NoteRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.ONE_MONTH)
    private val _periodOffset = MutableStateFlow(0)
    private val _selectedPhoto = MutableStateFlow<Photo?>(null)
    private val _showBottomSheet = MutableStateFlow(false)

    private val _animationState = MutableStateFlow(AnimationState())

    private data class ChartData(
        val photos: List<Photo>,
        val notes: List<DayNote>,
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
            _periodOffset,
            noteRepository.getAll()
        ) { photos, category, timeRange, offset, notes ->
            ChartData(photos, notes, category, timeRange, offset)
        },
        _selectedPhoto,
        _showBottomSheet,
        _animationState
    ) { data, selectedPhoto, showBottomSheet, anim ->
        val (filtered, label) = filterByTimeRange(data.photos, data.timeRange, data.periodOffset)
        ChartState(
            category = data.category,
            timeRange = data.timeRange,
            periodOffset = data.periodOffset,
            photos = filtered,
            notes = data.notes,
            selectedPhoto = selectedPhoto,
            showBottomSheet = showBottomSheet,
            periodLabel = label,
            animationPreparing = anim.isPreparing,
            animationPlaying = anim.isPlaying,
            animationIndex = anim.currentIndex,
            animationFrames = anim.frames
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartState())

    // ── Animation control ────────────────────────────────────────────────

    companion object {
        /** Milliseconds each face is shown before advancing. */
        private const val FRAME_DURATION_MS = 400L
    }

    private var animationJob: Job? = null
    private var timerJob: Job? = null

    /** True when the animation can be started (FACE category + has photos). */
    val canAnimate: Boolean
        get() = state.value.category == Category.FACE && state.value.photos.isNotEmpty()

    /**
     * Prepares and starts the face animation. Loads all current face photos,
     * normalises each one via [FaceNormalizer], then plays them in sequence
     * ordered by [order] (time or quality, ascending or descending).
     */
    fun startAnimation(order: PlayOrder = PlayOrder.TIME_ASC) {
        if (_animationState.value.isPreparing || _animationState.value.isPlaying) return

        val photos = when (order) {
            PlayOrder.TIME_ASC -> state.value.photos.sortedBy { it.capturedAt }
            PlayOrder.TIME_DESC -> state.value.photos.sortedByDescending { it.capturedAt }
            PlayOrder.QUALITY_ASC -> state.value.photos.sortedBy { it.rating }
            PlayOrder.QUALITY_DESC -> state.value.photos.sortedByDescending { it.rating }
        }
        if (photos.isEmpty()) return

        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            val cache = appContainer.faceBitmapCache

            // Only show the loading spinner if some photos need normalisation
            val allCached = photos.all { cache.get(it.uri) != null }
            if (!allCached) {
                _animationState.value = AnimationState(isPreparing = true)
            }

            val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val frames = mutableListOf<FaceAnimationFrame>()

            val diskDir = appContainer.faceDiskCacheDir
            for (photo in photos) {
                // Memory cache → disk cache → full re-normalisation.
                // The disk copy (JPEG, 512×512) decodes in a few ms, so
                // replaying the animation is effectively instant even after
                // process death or memory-cache eviction.
                val diskFile = File(diskDir, File(photo.uri).name + ".jpg")
                var bitmap = cache.get(photo.uri)
                if (bitmap == null && diskFile.exists()) {
                    bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (bitmap != null) cache.put(photo.uri, bitmap)
                }
                if (bitmap == null) {
                    bitmap = try {
                        FaceNormalizer.normalizeFromFile(photo.uri)
                    } catch (e: Exception) {
                        null
                    }
                    if (bitmap != null) {
                        cache.put(photo.uri, bitmap)
                        try {
                            FileOutputStream(diskFile).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                            }
                        } catch (_: Exception) { /* disk cache is best-effort */ }
                    }
                }
                frames.add(
                    FaceAnimationFrame(
                        photo = photo,
                        bitmap = bitmap,
                        dateLabel = dateFmt.format(Date(photo.capturedAt))
                    )
                )
            }

            if (frames.isEmpty()) {
                _animationState.value = AnimationState()
                return@launch
            }

            _animationState.value = AnimationState(
                isPreparing = false,
                isPlaying = true,
                currentIndex = 0,
                frames = frames
            )
            startTimer()
        }
    }

    /** Pauses the animation at the current frame. */
    fun pauseAnimation() {
        timerJob?.cancel()
        timerJob = null
        _animationState.value = _animationState.value.copy(isPlaying = false)
    }

    /** Resumes the animation from the current frame. */
    fun resumeAnimation() {
        val s = _animationState.value
        if (s.frames.isEmpty()) return
        if (s.currentIndex >= s.frames.lastIndex) {
            // Restart from beginning if at end
            _animationState.value = s.copy(currentIndex = 0, isPlaying = true)
        } else {
            _animationState.value = s.copy(isPlaying = true)
        }
        startTimer()
    }

    /** Stops the animation entirely. Bitmaps remain in the LruCache for next time. */
    fun stopAnimation() {
        timerJob?.cancel()
        timerJob = null
        animationJob?.cancel()
        animationJob = null
        _animationState.value = AnimationState()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(FRAME_DURATION_MS)
                val s = _animationState.value
                if (s.currentIndex >= s.frames.lastIndex) {
                    // Reached the end — stop
                    _animationState.value = s.copy(isPlaying = false)
                    break
                }
                _animationState.value = s.copy(currentIndex = s.currentIndex + 1)
            }
        }
    }

    // ── Chart controls (unchanged) ───────────────────────────────────────

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

    // ── Day notes ────────────────────────────────────────────────────────

    /** Inserts or updates a day note (id == 0 inserts). */
    fun saveNote(note: DayNote) {
        viewModelScope.launch { noteRepository.save(note) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { noteRepository.delete(id) }
    }

    /**
     * Steps the bottom-sheet photo to the nearest photo on an earlier/later
     * day (within the currently visible time range). Multiple photos on the
     * same day are skipped — the sheet navigates one day at a time.
     */
    fun stepSelectedPhotoDay(forward: Boolean) {
        val current = _selectedPhoto.value ?: return
        val zone = ZoneId.systemDefault()
        val currentDay = Instant.ofEpochMilli(current.capturedAt).atZone(zone).toLocalDate()
        val candidates = state.value.photos.filter { photo ->
            val day = Instant.ofEpochMilli(photo.capturedAt).atZone(zone).toLocalDate()
            if (forward) day > currentDay else day < currentDay
        }
        val next = if (forward) candidates.minByOrNull { it.capturedAt }
                   else candidates.maxByOrNull { it.capturedAt }
        if (next != null) _selectedPhoto.value = next
    }

    fun onPhotoSelected(photo: Photo) {
        _selectedPhoto.value = photo
        _showBottomSheet.value = true
    }

    fun dismissBottomSheet() {
        _showBottomSheet.value = false
        _selectedPhoto.value = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        animationJob?.cancel()
        // Bitmaps are owned by the AppContainer LruCache — do not recycle here.
    }

class Factory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = (application as SkinTrackerApp).container
        return ChartViewModel(container.photoRepository, container.noteRepository, container) as T
    }
}
}
