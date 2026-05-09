package com.example.skin_tracker.domain.model

/**
 * Represents a single habit fetched from the Tail app's Content Provider.
 *
 * @param habitId   The stable unique identifier for the habit (stored as an Intent extra
 *                  when sending the increment broadcast).
 * @param habitName Human-readable display name shown in the Settings picker.
 */
data class HabitEntry(
    val habitId: String,
    val habitName: String
)
