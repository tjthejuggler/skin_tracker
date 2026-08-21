package com.example.skin_tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.skin_tracker.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Insert or replace (same id) — used for both create and edit. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    /** Restore-time insert that preserves the entity's primary key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWithId(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY epochDay ASC, createdAt ASC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY epochDay ASC, createdAt ASC")
    suspend fun getAllSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE epochDay = :epochDay ORDER BY createdAt ASC")
    suspend fun getByDay(epochDay: Long): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Wipe the whole notes table (used by restore before re-importing). */
    @Query("DELETE FROM notes")
    suspend fun clearAll()
}
