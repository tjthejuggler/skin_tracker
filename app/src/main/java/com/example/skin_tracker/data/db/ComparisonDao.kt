package com.example.skin_tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.skin_tracker.data.entity.ComparisonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComparisonDao {

    @Insert
    suspend fun insert(comparison: ComparisonEntity): Long

    @Query("SELECT * FROM comparisons ORDER BY comparedAt DESC")
    fun getAll(): Flow<List<ComparisonEntity>>

    @Query("SELECT * FROM comparisons ORDER BY comparedAt DESC")
    suspend fun getAllSync(): List<ComparisonEntity>

    @Query("SELECT * FROM comparisons WHERE id = :id")
    suspend fun getById(id: Long): ComparisonEntity?

    @Query("UPDATE comparisons SET winnerPhotoId = :newWinnerId, loserPhotoId = :newLoserId, winnerRatingBefore = :wBefore, loserRatingBefore = :lBefore, winnerRatingAfter = :wAfter, loserRatingAfter = :lAfter WHERE id = :id")
    suspend fun updateFlip(id: Long, newWinnerId: Long, newLoserId: Long, wBefore: Double, lBefore: Double, wAfter: Double, lAfter: Double)

    @Query("DELETE FROM comparisons WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM comparisons WHERE winnerPhotoId = :photoId OR loserPhotoId = :photoId")
    suspend fun deleteByPhotoId(photoId: Long)

    @Query("SELECT COUNT(*) FROM comparisons WHERE comparedAt >= :startOfDay AND comparedAt < :endOfDay")
    suspend fun countToday(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM comparisons")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM comparisons WHERE category = :category")
    suspend fun totalCountByCategory(category: String): Int

    @Query("SELECT comparedAt FROM comparisons ORDER BY comparedAt ASC")
    suspend fun getAllComparedAtAsc(): List<Long>
}
