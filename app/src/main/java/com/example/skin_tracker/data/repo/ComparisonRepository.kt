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

    /** Most recent comparison recorded for a category (undo support). */
    suspend fun getLatestByCategory(category: Category): ComparisonEntity? =
        comparisonDao.getLatestByCategory(category.name)

    /** Restore-time insert that preserves the comparison's id. */
    suspend fun upsertWithId(comparison: ComparisonEntity) =
        comparisonDao.upsertWithId(comparison)

    /** Wipe the comparisons table (restore only). */
    suspend fun clearAll() =
        comparisonDao.clearAll()

    suspend fun insert(
        winnerPhotoId: Long,
        loserPhotoId: Long,
        category: Category,
        comparedAt: Long,
        winnerRatingBefore: Double,
        loserRatingBefore: Double,
        winnerRatingAfter: Double,
        loserRatingAfter: Double,
        durationMs: Long = 0L
    ): Long {
        return comparisonDao.insert(
            ComparisonEntity(
                winnerPhotoId = winnerPhotoId,
                loserPhotoId = loserPhotoId,
                category = category.name,
                comparedAt = comparedAt,
                durationMs = durationMs,
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

    suspend fun totalCountByCategory(category: Category): Int =
        comparisonDao.totalCountByCategory(category.name)

    /** Total time (ms) spent deciding comparisons, across all categories. */
    suspend fun totalDuration(): Long =
        comparisonDao.totalDuration()

    /** Total time (ms) spent deciding comparisons in one category (face/body differ). */
    suspend fun totalDurationByCategory(category: Category): Long =
        comparisonDao.totalDurationByCategory(category.name)

    /** Count of comparisons with a recorded decision time (denominator for the time-per-comparison rate). */
    suspend fun trackedCount(): Int =
        comparisonDao.trackedCount()

    suspend fun trackedCountByCategory(category: Category): Int =
        comparisonDao.trackedCountByCategory(category.name)

    suspend fun getAllComparedAtAsc(): List<Long> =
        comparisonDao.getAllComparedAtAsc()

    suspend fun countSince(since: Long): Int =
        comparisonDao.countSince(since)
}
