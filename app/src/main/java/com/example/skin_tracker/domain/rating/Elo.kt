package com.example.skin_tracker.domain.rating

import com.example.skin_tracker.domain.model.Photo

object Elo {

    const val K_FACTOR = 32.0
    const val INITIAL_RATING = 1500.0

    /**
     * Calculate expected score for player A against player B.
     */
    fun expectedScore(ratingA: Double, ratingB: Double): Double {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0))
    }

    /**
     * Calculate new rating after a comparison.
     * @param currentRating The player's current rating
     * @param expectedScore The expected score (from [expectedScore])
     * @param actualScore 1.0 for win, 0.0 for loss
     */
    fun newRating(currentRating: Double, expectedScore: Double, actualScore: Double): Double {
        return currentRating + K_FACTOR * (actualScore - expectedScore)
    }

    /**
     * Apply a comparison result to both photos and return the updated pair.
     * First = winner, Second = loser.
     */
    fun applyComparison(winner: Photo, loser: Photo): Pair<Photo, Photo> {
        val expectedWinner = expectedScore(winner.rating, loser.rating)
        val expectedLoser = expectedScore(loser.rating, winner.rating)

        val newWinnerRating = newRating(winner.rating, expectedWinner, 1.0)
        val newLoserRating = newRating(loser.rating, expectedLoser, 0.0)

        val updatedWinner = winner.copy(
            rating = newWinnerRating,
            comparisonCount = winner.comparisonCount + 1,
            wins = winner.wins + 1
        )
        val updatedLoser = loser.copy(
            rating = newLoserRating,
            comparisonCount = loser.comparisonCount + 1,
            losses = loser.losses + 1
        )

        return updatedWinner to updatedLoser
    }
}
