package com.example.skin_tracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comparisons")
data class ComparisonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val winnerPhotoId: Long,
    val loserPhotoId: Long,
    val category: String,
    val comparedAt: Long,
    val winnerRatingBefore: Double,
    val loserRatingBefore: Double,
    val winnerRatingAfter: Double,
    val loserRatingAfter: Double
)
