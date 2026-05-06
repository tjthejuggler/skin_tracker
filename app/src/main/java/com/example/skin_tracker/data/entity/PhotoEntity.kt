package com.example.skin_tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.skin_tracker.domain.model.Category

@Entity(
    tableName = "photos",
    indices = [
        Index("category", "capturedAt"),
        Index("category", "comparisonCount")
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val category: String,
    val capturedAt: Long,
    val importedAt: Long,
    val rating: Double = 1500.0,
    val comparisonCount: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val deleted: Boolean = false
)

fun PhotoEntity.toDomain() = com.example.skin_tracker.domain.model.Photo(
    id = id,
    uri = uri,
    category = Category.valueOf(category),
    capturedAt = capturedAt,
    importedAt = importedAt,
    rating = rating,
    comparisonCount = comparisonCount,
    wins = wins,
    losses = losses,
    deleted = deleted
)

fun com.example.skin_tracker.domain.model.Photo.toEntity() = PhotoEntity(
    id = id,
    uri = uri,
    category = category.name,
    capturedAt = capturedAt,
    importedAt = importedAt,
    rating = rating,
    comparisonCount = comparisonCount,
    wins = wins,
    losses = losses,
    deleted = deleted
)
