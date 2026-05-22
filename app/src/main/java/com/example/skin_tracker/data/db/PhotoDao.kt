package com.example.skin_tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.skin_tracker.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE deleted = 0 ORDER BY capturedAt DESC")
    fun getAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deleted = 0 AND category = :category ORDER BY capturedAt ASC")
    fun getByCategory(category: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deleted = 0 AND category = :category ORDER BY capturedAt ASC")
    suspend fun getByCategorySync(category: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE deleted = 0 AND capturedAt >= :start AND capturedAt <= :end AND category = :category ORDER BY capturedAt ASC")
    fun getByCategoryAndDateRange(category: String, start: Long, end: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deleted = 0 AND capturedAt >= :start AND capturedAt <= :end ORDER BY capturedAt ASC")
    suspend fun getByDateRangeSync(start: Long, end: Long): List<PhotoEntity>

    @Query("UPDATE photos SET rating = :rating, comparisonCount = :comparisonCount, wins = :wins, losses = :losses WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Double, comparisonCount: Int, wins: Int, losses: Int)

    @Query("UPDATE photos SET deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("UPDATE photos SET capturedAt = :capturedAt WHERE id = :id")
    suspend fun updateCapturedAt(id: Long, capturedAt: Long)

    @Query("SELECT * FROM photos")
    suspend fun getAllSync(): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE deleted = 0 AND category = :category")
    suspend fun countByCategory(category: String): Int

    @Query("UPDATE photos SET category = :category, rating = 1500.0, comparisonCount = 0, wins = 0, losses = 0 WHERE id = :id")
    suspend fun updateCategoryAndResetStats(id: Long, category: String)

    @Query("SELECT COUNT(*) FROM photos WHERE deleted = 0 AND capturedAt >= :startOfDay AND capturedAt < :endOfDay")
    suspend fun countToday(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT * FROM photos WHERE deleted = 0 ORDER BY rating DESC LIMIT 1")
    suspend fun getTopRated(): PhotoEntity?

    @Query("SELECT * FROM photos WHERE deleted = 0 AND category = :category ORDER BY rating DESC LIMIT 1")
    suspend fun getTopRatedByCategory(category: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE deleted = 0 AND capturedAt >= :since ORDER BY rating DESC LIMIT 1")
    suspend fun getTopRatedSince(since: Long): PhotoEntity?

    @Query("SELECT capturedAt FROM photos WHERE deleted = 0 ORDER BY capturedAt ASC")
    suspend fun getAllCapturedAtAsc(): List<Long>

    @Query("SELECT COUNT(*) FROM photos WHERE deleted = 0 AND capturedAt >= :since")
    suspend fun countSince(since: Long): Int
}
