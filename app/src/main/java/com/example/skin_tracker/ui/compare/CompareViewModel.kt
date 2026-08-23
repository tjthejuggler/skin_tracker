package com.example.skin_tracker.ui.compare

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.di.AppContainer
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.domain.rating.Elo
import com.example.skin_tracker.domain.rating.PairPicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CompareState(
    val category: Category = Category.FACE,
    val photoA: Photo? = null,
    val photoB: Photo? = null,
    val comparisonsToday: Int = 0,
    val totalPhotos: Int = 0,
    val canUndo: Boolean = false,
    val isLoading: Boolean = false
)

class CompareViewModel(
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository,
    private val appContainer: AppContainer,
    private val requestAutoBackup: () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(CompareState(category = appContainer.sharedCategory.value))
    val state: StateFlow<CompareState> = _state.asStateFlow()

    /** Comparison made most recently in this session, used to restore exact A/B positions on undo. */
    private var lastComparisonId: Long? = null
    private var lastPair: Pair<Photo, Photo>? = null

    /** Wall-clock time (ms) of when the current pair was presented; measures per-comparison decision time. */
    private var pairShownAt: Long = 0L

    companion object {
        /** Cap so a backgrounded/abandoned session doesn't inflate the time-per-comparison stats. */
        private const val MAX_DECISION_MS = 10L * 60 * 1000
    }

    init {
        loadPair()
        loadComparisonCount()
    }

    fun setCategory(category: Category) {
        appContainer.setSharedCategory(category)
        _state.value = _state.value.copy(category = category)
        loadPair()
    }

    fun loadPair() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val photos = photoRepository.getByCategorySync(_state.value.category)
            val pair = PairPicker.pickPair(photos)
            // Warm the handoff cache so PhotoDetail can render either photo on its
            // first frame (shared-element morph target) when the user opens details.
            pair?.let { (a, b) ->
                appContainer.photoHandoffCache.put(a.id, a)
                appContainer.photoHandoffCache.put(b.id, b)
            }
            _state.value = _state.value.copy(
                photoA = pair?.first,
                photoB = pair?.second,
                totalPhotos = photos.size,
                canUndo = comparisonRepository.getLatestByCategory(_state.value.category) != null,
                isLoading = false
            )
            // Start (or reset) the decision clock for the freshly presented pair.
            pairShownAt = if (pair != null) System.currentTimeMillis() else 0L
        }
    }

    fun pickWinner(winnerIsA: Boolean) {
        val state = _state.value
        val a = state.photoA ?: return
        val b = state.photoB ?: return

        val winner = if (winnerIsA) a else b
        val loser = if (winnerIsA) b else a

        // Stop the clock for this decision synchronously, before any async work.
        val decisionMs = if (pairShownAt > 0L) {
            (System.currentTimeMillis() - pairShownAt).coerceIn(0L, MAX_DECISION_MS)
        } else 0L
        pairShownAt = 0L

        val (updatedWinner, updatedLoser) = Elo.applyComparison(winner, loser)

        viewModelScope.launch {
            photoRepository.updateRating(updatedWinner)
            photoRepository.updateRating(updatedLoser)

            val comparisonId = comparisonRepository.insert(
                winnerPhotoId = updatedWinner.id,
                loserPhotoId = updatedLoser.id,
                category = state.category,
                comparedAt = System.currentTimeMillis(),
                winnerRatingBefore = winner.rating,
                loserRatingBefore = loser.rating,
                winnerRatingAfter = updatedWinner.rating,
                loserRatingAfter = updatedLoser.rating,
                durationMs = decisionMs
            )
            lastComparisonId = comparisonId
            lastPair = a to b

            requestAutoBackup()

            loadComparisonCount()
            loadPair()
        }
    }

    /**
     * Undo the most recent comparison in the current category: revert both photos'
     * ratings, delete the comparison record, and show the same pair again so the
     * user can redo it.
     */
    fun undoLastComparison() {
        val category = _state.value.category
        viewModelScope.launch {
            val comp = comparisonRepository.getLatestByCategory(category) ?: return@launch
            val winner = photoRepository.getById(comp.winnerPhotoId) ?: return@launch
            val loser = photoRepository.getById(comp.loserPhotoId) ?: return@launch

            // Revert the Elo changes (same revert logic as HistoryViewModel.deleteComparison)
            val revertedWinner = winner.copy(
                rating = comp.winnerRatingBefore,
                comparisonCount = (winner.comparisonCount - 1).coerceAtLeast(0),
                wins = (winner.wins - 1).coerceAtLeast(0)
            )
            val revertedLoser = loser.copy(
                rating = comp.loserRatingBefore,
                comparisonCount = (loser.comparisonCount - 1).coerceAtLeast(0),
                losses = (loser.losses - 1).coerceAtLeast(0)
            )

            photoRepository.updateRating(revertedWinner)
            photoRepository.updateRating(revertedLoser)
            comparisonRepository.delete(comp.id)

            requestAutoBackup()

            // Put the same pair back on screen, preserving the original A/B positions
            // when this comparison was made in the current session.
            val rememberedPair = lastPair
            val knownOrder = lastComparisonId == comp.id && rememberedPair != null
            val pair = if (knownOrder && rememberedPair.first.id == comp.loserPhotoId) {
                revertedLoser to revertedWinner
            } else {
                revertedWinner to revertedLoser
            }
            lastComparisonId = null
            lastPair = null

            // Keep the handoff cache warm for the restored pair as well.
            appContainer.photoHandoffCache.put(pair.first.id, pair.first)
            appContainer.photoHandoffCache.put(pair.second.id, pair.second)

            _state.value = _state.value.copy(
                photoA = pair.first,
                photoB = pair.second,
                canUndo = comparisonRepository.getLatestByCategory(category) != null
            )
            // The user re-decides this pair from scratch — restart the decision clock.
            pairShownAt = System.currentTimeMillis()
            loadComparisonCount()
        }
    }

    fun skip() {
        loadPair()
    }

    private fun loadComparisonCount() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000

            val count = comparisonRepository.countToday(startOfDay, endOfDay)
            _state.value = _state.value.copy(comparisonsToday = count)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as SkinTrackerApp
            val container = app.container
            return CompareViewModel(
                container.photoRepository,
                container.comparisonRepository,
                container,
                app::requestAutoBackup
            ) as T
        }
    }
}
