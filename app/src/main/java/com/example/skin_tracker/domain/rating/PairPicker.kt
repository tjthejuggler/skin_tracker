package com.example.skin_tracker.domain.rating

import com.example.skin_tracker.domain.model.Photo
import kotlin.random.Random

object PairPicker {

    /**
     * Temporal half-life used to bias pair selection. A candidate photo
     * captured this many milliseconds away from the first pick gets half
     * of its base weight. Two weeks is a reasonable scale for skin changes.
     */
    private const val TEMPORAL_HALF_LIFE_MS = 14.0 * 24 * 60 * 60 * 1000

    /**
     * Pick a random pair of photos from the given list, biased toward
     * photos with fewer comparisons so new uploads catch up faster, and
     * (for the second photo) toward photos closer in time to the first.
     *
     * Base weight for each photo = 1 / (1 + comparisonCount)
     *
     * The second pick's weight additionally gets a temporal factor
     * 1 / (1 + |capturedAt difference| / TEMPORAL_HALF_LIFE_MS),
     * so any-vs-any pairings remain possible, but photos close in time
     * (which are hardest to tell apart and thus most informative) are
     * more likely to be matched against each other.
     *
     * @return a pair of distinct photos, or null if fewer than 2 photos
     */
    fun pickPair(photos: List<Photo>): Pair<Photo, Photo>? {
        if (photos.size < 2) return null

        val weights = photos.map { 1.0 / (1.0 + it.comparisonCount) }
        val totalWeight = weights.sum()

        val first = pickWeighted(photos, weights, totalWeight, null)
        val secondWeights = weights.mapIndexed { i, w ->
            if (i == first.second) {
                0.0
            } else {
                w * temporalFactor(first.first.capturedAt, photos[i].capturedAt)
            }
        }
        val second = pickWeighted(
            photos,
            secondWeights,
            secondWeights.sum(),
            null
        )

        return first.first to second.first
    }

    private fun temporalFactor(timeA: Long, timeB: Long): Double {
        val distance = Math.abs(timeA - timeB).toDouble()
        return 1.0 / (1.0 + distance / TEMPORAL_HALF_LIFE_MS)
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
