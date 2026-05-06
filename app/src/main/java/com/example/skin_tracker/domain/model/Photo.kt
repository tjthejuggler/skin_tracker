package com.example.skin_tracker.domain.model

data class Photo(
    val id: Long = 0,
    val uri: String,
    val category: Category,
    val capturedAt: Long,
    val importedAt: Long,
    val rating: Double = DEFAULT_RATING,
    val comparisonCount: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val deleted: Boolean = false
) {
    companion object {
        const val DEFAULT_RATING = 1500.0
    }
}
