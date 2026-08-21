package com.example.skin_tracker.data.repo

import com.example.skin_tracker.data.db.NoteDao
import com.example.skin_tracker.data.entity.toDomain
import com.example.skin_tracker.data.entity.toEntity
import com.example.skin_tracker.domain.model.DayNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val noteDao: NoteDao) {

    fun getAll(): Flow<List<DayNote>> =
        noteDao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAllSync(): List<DayNote> =
        noteDao.getAllSync().map { it.toDomain() }

    suspend fun getByDay(epochDay: Long): List<DayNote> =
        noteDao.getByDay(epochDay).map { it.toDomain() }

    /** Insert or update; returns the row id. */
    suspend fun save(note: DayNote): Long =
        noteDao.upsert(note.toEntity())

    suspend fun delete(id: Long) =
        noteDao.deleteById(id)

    /** Wipe the notes table (restore only). */
    suspend fun clearAll() =
        noteDao.clearAll()

    /** Restore-time insert that preserves the note's id. */
    suspend fun upsertWithId(note: DayNote) =
        noteDao.upsertWithId(note.toEntity())
}
