package com.example.skin_tracker.data.repo

import com.example.skin_tracker.data.db.ComparisonDao
import com.example.skin_tracker.data.entity.ComparisonEntity
import com.example.skin_tracker.domain.model.Category
import kotlinx.coroutines.flow.Flow

class ComparisonRepository(private val comparisonDao: ComparisonDao) {

    fun getAll(): Flow<List<ComparisonEntity>> =
        comparisonDao.getAll()

    suspend fun getAllSync(): List<ComparisonEntity> =
        comparisonDao.getAllSync()

    suspend fun getById(id: Long): ComparisonEntity? =
        comparisonDao.getById(id)

    suspend fun insert(
        winnerPhotoId: Long,
        loserPhotoId: Long,
        category: Category,
        comparedAt: Long,
        winnerRatingBefore: Double,
        loserRatingBefore: Double,
        winnerRatingAfter: Double,
        loserRatingAfter: Double
    ): Long {
        return comparisonDao.insert(
            ComparisonEntity(
                winnerPhotoId = winnerPhotoId,
                loserPhotoId = loserPhotoId,
                category = category.name,
                comparedAt = comparedAt,
                winnerRatingBefore = winnerRatingBefore,
                loserRatingBefore = loserRatingBefore,
                winnerRatingAfter = winnerRatingAfter,
                loserRatingAfter = loserRatingAfter
            )
        )
    }

    suspend fun updateFlip(
        id: Long,
        newWinnerId: Long,
        newLoserId: Long,
        winnerRatingBefore: Double,
        loserRatingBefore: Double,
        winnerRatingAfter: Double,
        loserRatingAfter: Double
    ) {
        comparisonDao.updateFlip(id, newWinnerId, newLoserId, winnerRatingBefore, loserRatingBefore, winnerRatingAfter, loserRatingAfter)
    }

    suspend fun delete(id: Long) =
        comparisonDao.delete(id)

    suspend fun deleteByPhotoId(photoId: Long) =
        comparisonDao.deleteByPhotoId(photoId)

    suspend fun countToday(startOfDay: Long, endOfDay: Long): Int =
        comparisonDao.countToday(startOfDay, endOfDay)

    suspend fun totalCount(): Int =
        comparisonDao.totalCount()
}
