package com.example.skin_tracker.domain.model

/**
 * Represents a single debug note created by the user on a specific screen.
 * Written to [debug_skin_tracker.json] so a programmer LLM can quickly locate
 * relevant source files.
 */
data class DebugNote(
    val id: String,
    val timestamp: String,
    val screenRoute: String,
    val screenLabel: String,
    val sourceFile: String,
    val sourceFunctions: String,
    val noteType: NoteType,
    val noteText: String
)

enum class NoteType(val label: String) {
    BUG("Bug"),
    FEATURE("Feature"),
    NOTE("Note")
}
