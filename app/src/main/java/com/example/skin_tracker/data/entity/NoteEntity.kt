package com.example.skin_tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.skin_tracker.domain.model.DayNote
import com.example.skin_tracker.domain.model.NoteTarget

/**
 * Room entity for day notes (see [DayNote]). One row per note; multiple notes
 * per day are allowed. `epochDay` is [java.time.LocalDate.toEpochDay].
 */
@Entity(
    tableName = "notes",
    indices = [Index("epochDay")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val text: String,
    val target: String = NoteTarget.BOTH.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun NoteEntity.toDomain() = DayNote(
    id = id,
    epochDay = epochDay,
    text = text,
    target = runCatching { NoteTarget.valueOf(target) }.getOrDefault(NoteTarget.BOTH),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DayNote.toEntity() = NoteEntity(
    id = id,
    epochDay = epochDay,
    text = text,
    target = target.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)
