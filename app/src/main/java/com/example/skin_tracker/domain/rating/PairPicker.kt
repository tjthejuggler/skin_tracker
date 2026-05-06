package com.example.skin_tracker.domain.rating

import com.example.skin_tracker.domain.model.Photo
import kotlin.random.Random

object PairPicker {

    /**
     * Pick a random pair of photos from the given list, biased toward
     * photos with fewer comparisons so new uploads catch up faster.
     *
     * Weight for each photo = 1 / (1 + comparisonCount)
     *
     * @return a pair of distinct photos, or null if fewer than 2 photos
     */
    fun pickPair(photos: List<Photo>): Pair<Photo, Photo>? {
        if (photos.size < 2) return null

        val weights = photos.map { 1.0 / (1.0 + it.comparisonCount) }
        val totalWeight = weights.sum()

        val first = pickWeighted(photos, weights, totalWeight, null)
        val second = pickWeighted(
            photos,
            weights.mapIndexed { i, w -> if (i == first.second) 0.0 else w },
            totalWeight - weights[first.second],
            null
        )

        return first.first to second.first
    }

    private fun pickWeighted(
        items: List<Photo>,
        weights: List<Double>,
        totalWeight: Double,
        excludeIndex: Int?
    ): Pair<Photo, Int> {
        var random = Random.nextDouble(totalWeight)
        for (i in items.indices) {
            if (i == excludeIndex) continue
            random -= weights[i]
            if (random <= 0.0) {
                return items[i] to i
            }
        }
        // Fallback: return last non-excluded
        val idx = items.indices.last { it != excludeIndex }
        return items[idx] to idx
    }
}
