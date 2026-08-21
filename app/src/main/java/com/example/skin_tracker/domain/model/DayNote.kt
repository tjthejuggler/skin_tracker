package com.example.skin_tracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which chart(s) a note's marker appears on.
 *
 * [BOTH] is the default so a note shows up on the Face and Body charts alike;
 * [FACE]/[BODY] restrict the marker to a single chart.
 */
enum class NoteTarget(val label: String) {
    BOTH("Both"),
    FACE("Face"),
    BODY("Body");

    /** True when a marker for this target should be drawn on [category]'s chart. */
    fun appliesTo(category: Category): Boolean =
        this == BOTH || name == category.name
}

/**
 * A free-text note attached to a specific calendar day (past or present),
 * rendered as a small marker on the chart(s) identified by [target].
 *
 * @param epochDay  The day as [LocalDate.toEpochDay] — timezone-independent and
 *                  stable across DST changes (unlike epoch milliseconds).
 * @param createdAt Millis when the note was first written.
 * @param updatedAt Millis of the last edit.
 */
data class DayNote(
    val id: Long = 0,
    val epochDay: Long,
    val text: String,
    val target: NoteTarget = NoteTarget.BOTH,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    /**
     * A representative x-axis timestamp (noon local time on the note's day)
     * for drawing the marker on the time-based chart.
     */
    fun chartTimestampMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    companion object {
        /** Converts DatePicker UTC day-millis to an epoch-day. */
        fun utcMillisToEpochDay(utcMillis: Long): Long =
            Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
    }
}
