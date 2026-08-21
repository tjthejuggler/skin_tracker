package com.example.skin_tracker.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skin_tracker.domain.model.DayNote
import com.example.skin_tracker.domain.model.NoteTarget
import com.example.skin_tracker.ui.components.SelectableChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Sleek dialog for adding / editing / deleting day notes.
 *
 * - Pick any day (today or any past day — future dates are not selectable).
 * - Notes already on the selected day are listed; tap one to edit it, use the
 *   trash icon to delete it.
 * - A note can target Both charts (default), Face only, or Body only.
 *
 * The dialog stays open after a save so several notes can be added in a row;
 * tapping outside dismisses it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNoteDialog(
    /** All stored notes; the dialog filters them down to the selected day. */
    notes: List<DayNote>,
    /** Day to open on (e.g. the bottom-sheet photo's day); null = today. */
    initialEpochDay: Long? = null,
    onDismiss: () -> Unit,
    onSave: (DayNote) -> Unit,
    onDelete: (Long) -> Unit
) {
    var selectedEpochDay by remember(initialEpochDay) {
        mutableStateOf(initialEpochDay ?: LocalDate.now().toEpochDay())
    }
    var text by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(NoteTarget.BOTH) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val dayNotes = remember(selectedEpochDay, notes) {
        notes.filter { it.epochDay == selectedEpochDay }
    }

    fun resetEditor() {
        text = ""
        target = NoteTarget.BOTH
        editingId = null
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedEpochDay * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    DayNote.utcMillisToEpochDay(utcTimeMillis) <= todayEpochDay
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val newDay = DayNote.utcMillisToEpochDay(it)
                            if (newDay != selectedEpochDay) resetEditor()
                            selectedEpochDay = newDay
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Day note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Date selector ───────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (selectedEpochDay == todayEpochDay) "Today"
                            else LocalDate.ofEpochDay(selectedEpochDay)
                                .format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Change",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ── Existing notes for the selected day ─────────────────────
                if (dayNotes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dayNotes.forEach { note ->
                            val isEditing = editingId == note.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isEditing) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isEditing) {
                                            resetEditor()
                                        } else {
                                            editingId = note.id
                                            text = note.text
                                            target = note.target
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                        Text(
                                            "${note.target.label} · ${note.date.year}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            note.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isEditing) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editing",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (editingId == note.id) resetEditor()
                                            onDelete(note.id)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete note",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Editor ──────────────────────────────────────────────────
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (editingId == null) "Add a note for this day…"
                            else "Editing note…",
                            fontStyle = FontStyle.Italic
                        )
                    },
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteTarget.entries.forEach { t ->
                        SelectableChip(
                            label = t.label,
                            selected = target == t,
                            onClick = { target = t },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 4.dp
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return@FilledTonalButton
                    val now = System.currentTimeMillis()
                    val existing = dayNotes.firstOrNull { it.id == editingId }
                    onSave(
                        DayNote(
                            id = editingId ?: 0L,
                            epochDay = selectedEpochDay,
                            text = trimmed,
                            target = target,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                    resetEditor()
                },
                enabled = text.isNotBlank()
            ) { Text(if (editingId == null) "Save" else "Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
