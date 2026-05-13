package com.example.skin_tracker.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skin_tracker.data.debug.QueuedNote
import com.example.skin_tracker.data.debug.SavedNote
import com.example.skin_tracker.data.debug.ScreenContextMapper.ScreenContext
import com.example.skin_tracker.domain.model.NoteType

private enum class DebugTab { NOTE, QUEUE, SAVED }

/**
 * Dialog shown when the user taps the debug bubble.
 *
 * Three tabs:
 * - **Note** — compose a note with Save and Queue buttons
 * - **Queue** — view queued notes (all screens) with Submit All and per-note delete
 * - **Saved** — view all saved notes, click to view/edit, queue, or delete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugNoteDialog(
    screenContext: ScreenContext,
    currentRoute: String,
    queuedNotes: List<QueuedNote>,
    savedNotes: List<SavedNote>,
    noteCountOnScreen: Int,
    onDismiss: () -> Unit,
    onSaveNote: (NoteType, String) -> Unit,
    onQueueNote: (NoteType, String) -> Unit,
    onSubmitQueue: () -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onUpdateSavedNote: (String, NoteType, String) -> Unit,
    onDeleteSavedNote: (String) -> Unit,
    onQueueSavedNote: (String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(NoteType.BUG) }
    var activeTab by remember { mutableStateOf(DebugTab.NOTE) }
    var editingNote by remember { mutableStateOf<SavedNote?>(null) }

    val totalSaved = savedNotes.size

    // If an editing note is set, show the edit dialog instead
    val currentEditNote = editingNote
    if (currentEditNote != null) {
        SavedNoteEditDialog(
            note = currentEditNote,
            onUpdate = { noteId, noteType, text ->
                onUpdateSavedNote(noteId, noteType, text)
                editingNote = null
            },
            onQueue = { noteId ->
                onQueueSavedNote(noteId)
                editingNote = null
            },
            onDelete = { noteId ->
                onDeleteSavedNote(noteId)
                editingNote = null
            },
            onDismiss = { editingNote = null }
        )
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(16.dp),
        title = {
            Column {
                // ── Tab row ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DebugTabButton(
                        label = "✏️ Note",
                        selected = activeTab == DebugTab.NOTE,
                        onClick = { activeTab = DebugTab.NOTE }
                    )
                    DebugTabButton(
                        label = "📋 Queue",
                        badge = queuedNotes.size.takeIf { it > 0 },
                        badgeColor = DebugOrange,
                        selected = activeTab == DebugTab.QUEUE,
                        onClick = { activeTab = DebugTab.QUEUE }
                    )
                    DebugTabButton(
                        label = "💾 Saved",
                        badge = totalSaved.takeIf { it > 0 },
                        badgeColor = DebugGreen,
                        selected = activeTab == DebugTab.SAVED,
                        onClick = { activeTab = DebugTab.SAVED }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // ── Screen context (shown on Note tab only) ───────────────────
                if (activeTab == DebugTab.NOTE) {
                    Text(
                        "Screen: ${screenContext.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                    Text(
                        "Source: ${screenContext.sourceFile}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebugCyan
                    )
                    if (noteCountOnScreen > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "$noteCountOnScreen note(s) queued on this screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = DebugOrange
                        )
                    }
                }
            }
        },
        text = {
            when (activeTab) {
                DebugTab.NOTE -> NoteComposeContent(
                    noteText = noteText,
                    selectedType = selectedType,
                    onNoteTextChange = { noteText = it },
                    onTypeChange = { selectedType = it }
                )

                DebugTab.QUEUE -> QueueContent(
                    queuedNotes = queuedNotes,
                    onRemoveFromQueue = onRemoveFromQueue
                )

                DebugTab.SAVED -> SavedContent(
                    savedNotes = savedNotes,
                    onClickNote = { editingNote = it }
                )
            }
        },
        confirmButton = {
            when (activeTab) {
                DebugTab.NOTE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (noteText.isNotBlank()) {
                                onSaveNote(selectedType, noteText)
                                noteText = ""
                                selectedType = NoteType.BUG
                            }
                        },
                        enabled = noteText.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = onSurface,
                            disabledContentColor = onSurfaceVariant
                        )
                    ) {
                        Text("Save")
                    }
                    Button(
                        onClick = {
                            if (noteText.isNotBlank()) {
                                onQueueNote(selectedType, noteText)
                                noteText = ""
                                selectedType = NoteType.BUG
                            }
                        },
                        enabled = noteText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DebugOrange,
                            contentColor = Color.Black,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = onSurfaceVariant
                        )
                    ) {
                        Text("Queue")
                    }
                }

                DebugTab.QUEUE -> if (queuedNotes.isNotEmpty()) {
                    Button(
                        onClick = {
                            onSubmitQueue()
                            activeTab = DebugTab.NOTE
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DebugGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Submit All (${queuedNotes.size})")
                    }
                }

                DebugTab.SAVED -> { /* no action button needed */ }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = onSurfaceVariant)
            }
        }
    )
}

// ── Private sub-composables ───────────────────────────────────────────────────

@Composable
private fun DebugTabButton(
    label: String,
    selected: Boolean,
    badge: Int? = null,
    badgeColor: Color = DebugOrange,
    onClick: () -> Unit
) {
    BadgedBox(
        badge = {
            if (badge != null && badge > 0) {
                Badge(containerColor = badgeColor, contentColor = Color.Black) {
                    Text(badge.toString(), fontSize = 9.sp)
                }
            }
        }
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (selected) DebugCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun NoteComposeContent(
    noteText: String,
    selectedType: NoteType,
    onNoteTextChange: (String) -> Unit,
    onTypeChange: (NoteType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NoteType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onTypeChange(type) },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (type) {
                            NoteType.BUG -> DebugRed.copy(alpha = 0.3f)
                            NoteType.FEATURE -> DebugGreen.copy(alpha = 0.3f)
                            NoteType.NOTE -> DebugCyan.copy(alpha = 0.3f)
                        },
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteTextChange,
            label = { Text("Describe the ${selectedType.label.lowercase()}…") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DebugCyan,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                cursorColor = DebugCyan,
                focusedLabelColor = DebugCyan,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun QueueContent(
    queuedNotes: List<QueuedNote>,
    onRemoveFromQueue: (String) -> Unit
) {
    if (queuedNotes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No notes queued",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(queuedNotes, key = { it.id }) { note ->
                NoteCard(
                    typeLabel = note.noteType.label,
                    typeColor = noteTypeColor(note.noteType),
                    screenLabel = note.screenLabel,
                    noteText = note.noteText,
                    sourceFile = note.sourceFile,
                    trailingContent = {
                        androidx.compose.material3.IconButton(
                            onClick = { onRemoveFromQueue(note.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SavedContent(
    savedNotes: List<SavedNote>,
    onClickNote: (SavedNote) -> Unit
) {
    if (savedNotes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No saved notes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(savedNotes, key = { it.id }) { note ->
                NoteCard(
                    typeLabel = note.noteType.label,
                    typeColor = noteTypeColor(note.noteType),
                    screenLabel = note.screenLabel,
                    noteText = note.noteText,
                    sourceFile = note.sourceFile,
                    trailingContent = null,
                    modifier = Modifier.clickable { onClickNote(note) }
                )
            }
        }
    }
}

/**
 * Full-screen edit dialog for a saved note.
 * Allows viewing the full text, editing, queuing, or deleting.
 */
@Composable
private fun SavedNoteEditDialog(
    note: SavedNote,
    onUpdate: (String, NoteType, String) -> Unit,
    onQueue: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editType by remember(note.id) { mutableStateOf(note.noteType) }
    var editText by remember(note.id) { mutableStateOf(note.noteText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Column {
                Text(
                    "📝 Saved Note",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "📍 ${note.screenLabel}  •  ${note.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    note.sourceFile,
                    style = MaterialTheme.typography.labelSmall,
                    color = DebugCyan.copy(alpha = 0.7f)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NoteType.entries.forEach { type ->
                        FilterChip(
                            selected = editType == type,
                            onClick = { editType = type },
                            label = { Text(type.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (type) {
                                    NoteType.BUG -> DebugRed.copy(alpha = 0.3f)
                                    NoteType.FEATURE -> DebugGreen.copy(alpha = 0.3f)
                                    NoteType.NOTE -> DebugCyan.copy(alpha = 0.3f)
                                },
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                // Editable text
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Note text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DebugCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        cursorColor = DebugCyan,
                        focusedLabelColor = DebugCyan,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Delete button
                OutlinedButton(
                    onClick = { onDelete(note.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DebugRed)
                ) {
                    Text("Delete")
                }
                // Queue button
                Button(
                    onClick = { onQueue(note.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DebugOrange,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Queue")
                }
                // Save (update) button
                Button(
                    onClick = { onUpdate(note.id, editType, editText) },
                    enabled = editText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DebugGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Update")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun NoteCard(
    typeLabel: String,
    typeColor: Color,
    screenLabel: String,
    noteText: String,
    sourceFile: String,
    trailingContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        screenLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    sourceFile,
                    style = MaterialTheme.typography.labelSmall,
                    color = DebugCyan.copy(alpha = 0.7f)
                )
            }
            trailingContent?.invoke()
        }
    }
}

private fun noteTypeColor(type: NoteType): Color = when (type) {
    NoteType.BUG -> DebugRed
    NoteType.FEATURE -> DebugGreen
    NoteType.NOTE -> DebugCyan
}
