package com.example.skin_tracker.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.data.entity.ComparisonEntity
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.domain.rating.Elo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComparisonWithPhotos(
    val comparison: ComparisonEntity,
    val winnerPhoto: Photo?,
    val loserPhoto: Photo?
)

data class HistoryState(
    val comparisons: List<ComparisonWithPhotos> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel(
    private val comparisonRepository: ComparisonRepository,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            val comparisons = comparisonRepository.getAllSync()
            val withPhotos = comparisons.map { comp ->
                val winner = photoRepository.getById(comp.winnerPhotoId)
                val loser = photoRepository.getById(comp.loserPhotoId)
                ComparisonWithPhotos(comp, winner, loser)
            }
            _state.value = HistoryState(comparisons = withPhotos, isLoading = false)
        }
    }

    /**
     * Flip a comparison: reverse winner/loser and recalculate Elo.
     * This reverts the original Elo change and applies the opposite.
     */
    fun flipComparison(comparisonId: Long) {
        viewModelScope.launch {
            val comp = comparisonRepository.getById(comparisonId) ?: return@launch
            val winner = photoRepository.getById(comp.winnerPhotoId) ?: return@launch
            val loser = photoRepository.getById(comp.loserPhotoId) ?: return@launch

            // Step 1: Revert the original Elo change
            val revertedWinner = winner.copy(
                rating = comp.winnerRatingBefore,
                comparisonCount = winner.comparisonCount - 1,
                wins = winner.wins - 1
            )
            val revertedLoser = loser.copy(
                rating = comp.loserRatingBefore,
                comparisonCount = loser.comparisonCount - 1,
                losses = loser.losses - 1
            )

            // Step 2: Apply the flipped comparison (original loser wins)
            val (newWinner, newLoser) = Elo.applyComparison(revertedLoser, revertedWinner)

            // Step 3: Update both photos
            photoRepository.updateRating(newWinner)
            photoRepository.updateRating(newLoser)

            // Step 4: Update the comparison record
            comparisonRepository.updateFlip(
                id = comparisonId,
                newWinnerId = newWinner.id,
                newLoserId = newLoser.id,
                winnerRatingBefore = newWinner.rating.let { comp.loserRatingBefore },
                loserRatingBefore = newLoser.rating.let { comp.winnerRatingBefore },
                winnerRatingAfter = newWinner.rating,
                loserRatingAfter = newLoser.rating
            )

            loadHistory()
        }
    }

    /**
     * Delete a comparison entirely and revert the Elo changes.
     */
    fun deleteComparison(comparisonId: Long) {
        viewModelScope.launch {
            val comp = comparisonRepository.getById(comparisonId) ?: return@launch
            val winner = photoRepository.getById(comp.winnerPhotoId) ?: return@launch
            val loser = photoRepository.getById(comp.loserPhotoId) ?: return@launch

            // Revert ratings
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
            comparisonRepository.delete(comparisonId)

            loadHistory()
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as SkinTrackerApp).container
            return HistoryViewModel(container.comparisonRepository, container.photoRepository) as T
        }
    }
}
