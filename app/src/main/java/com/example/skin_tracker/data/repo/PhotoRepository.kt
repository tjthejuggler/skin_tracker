package com.example.skin_tracker.data.repo

import com.example.skin_tracker.data.db.PhotoDao
import com.example.skin_tracker.data.entity.toDomain
import com.example.skin_tracker.data.entity.toEntity
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PhotoRepository(private val photoDao: PhotoDao) {

    fun getAll(): Flow<List<Photo>> =
        photoDao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByCategory(category: Category): Flow<List<Photo>> =
        photoDao.getByCategory(category.name).map { list -> list.map { it.toDomain() } }

    suspend fun getByCategorySync(category: Category): List<Photo> =
        photoDao.getByCategorySync(category.name).map { it.toDomain() }

    suspend fun getById(id: Long): Photo? =
        photoDao.getById(id)?.toDomain()

    fun getByCategoryAndDateRange(category: Category, start: Long, end: Long): Flow<List<Photo>> =
        photoDao.getByCategoryAndDateRange(category.name, start, end).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun insert(photo: Photo): Long =
        photoDao.insert(photo.toEntity())

    suspend fun updateRating(photo: Photo) {
        photoDao.updateRating(
            id = photo.id,
            rating = photo.rating,
            comparisonCount = photo.comparisonCount,
            wins = photo.wins,
            losses = photo.losses
        )
    }

    suspend fun softDelete(id: Long) =
        photoDao.softDelete(id)

    suspend fun hardDelete(id: Long) =
        photoDao.hardDelete(id)

    suspend fun getAllSync(): List<Photo> =
        photoDao.getAllSync().map { it.toDomain() }

    suspend fun countByCategory(category: Category): Int =
        photoDao.countByCategory(category.name)
}
