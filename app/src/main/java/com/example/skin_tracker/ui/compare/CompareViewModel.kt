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
    val isLoading: Boolean = false
)

class CompareViewModel(
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    private val _state = MutableStateFlow(CompareState(category = appContainer.sharedCategory.value))
    val state: StateFlow<CompareState> = _state.asStateFlow()

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
            _state.value = _state.value.copy(
                photoA = pair?.first,
                photoB = pair?.second,
                totalPhotos = photos.size,
                isLoading = false
            )
        }
    }

    fun pickWinner(winnerIsA: Boolean) {
        val state = _state.value
        val a = state.photoA ?: return
        val b = state.photoB ?: return

        val winner = if (winnerIsA) a else b
        val loser = if (winnerIsA) b else a

        val (updatedWinner, updatedLoser) = Elo.applyComparison(winner, loser)

        viewModelScope.launch {
            photoRepository.updateRating(updatedWinner)
            photoRepository.updateRating(updatedLoser)

            comparisonRepository.insert(
                winnerPhotoId = updatedWinner.id,
                loserPhotoId = updatedLoser.id,
                category = state.category,
                comparedAt = System.currentTimeMillis(),
                winnerRatingBefore = winner.rating,
                loserRatingBefore = loser.rating,
                winnerRatingAfter = updatedWinner.rating,
                loserRatingAfter = updatedLoser.rating
            )

            loadComparisonCount()
            loadPair()
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
            val container = (application as SkinTrackerApp).container
            return CompareViewModel(container.photoRepository, container.comparisonRepository, container) as T
        }
    }
}
