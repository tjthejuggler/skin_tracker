package com.example.skin_tracker.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import com.example.skin_tracker.domain.model.HabitEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadHabits()
    }

    // Surface backup/restore result messages as snackbars.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val context = LocalContext.current
    val debugDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        com.example.skin_tracker.MainActivity.expectingActivityResult = false
        uri?.let {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.setDebugFileDir(uri.toString())
        }
    }

    val backupDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        com.example.skin_tracker.MainActivity.expectingActivityResult = false
        uri?.let {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.setBackupDir(uri.toString())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TailAppIntegrationCard(
                habitList = state.habitList,
                isLoading = state.isLoadingHabits,
                habitAppUnavailable = state.habitAppUnavailable,
                photoAddedHabit = state.photoAddedHabit,
                faceScoreHabit = state.faceScoreHabit,
                bodyScoreHabit = state.bodyScoreHabit,
                scoreSyncInProgress = state.scoreSyncInProgress,
                onSelectHabit = { slot, entry -> viewModel.selectHabit(slot, entry) },
                onClearHabit = { slot -> viewModel.clearHabit(slot) },
                onSendScores = { viewModel.sendScoresNow() },
                onRefresh = { viewModel.loadHabits() }
            )

            // ── Backup & Restore ─────────────────────────────────────────
            BackupCard(
                backupDirUri = state.backupDirUri,
                autoBackupEnabled = state.autoBackupEnabled,
                lastBackupAt = state.lastBackupAt,
                lastBackupPhotoCount = state.lastBackupPhotoCount,
                backupInProgress = state.backupInProgress,
                restoreInProgress = state.restoreInProgress,
                onChooseFolder = {
                    com.example.skin_tracker.MainActivity.expectingActivityResult = true
                    backupDirLauncher.launch(null)
                },
                onToggleAutoBackup = { viewModel.setAutoBackupEnabled(it) },
                onBackupNow = { viewModel.runManualBackup() },
                onRestore = { viewModel.runRestore() }
            )

            // ── Debug Mode ───────────────────────────────────────────────
            DebugModeCard(
                debugModeEnabled = state.debugModeEnabled,
                debugFileDirUri = state.debugFileDirUri,
                onToggleDebugMode = { viewModel.setDebugModeEnabled(it) },
                onChooseDirectory = {
                    com.example.skin_tracker.MainActivity.expectingActivityResult = true
                    debugDirLauncher.launch(null)
                },
                onClearDirectory = { viewModel.clearDebugFileDir() }
            )
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Tail App Integration Card ─────────────────────────────────────────────────

@Composable
private fun TailAppIntegrationCard(
    habitList: List<HabitEntry>,
    isLoading: Boolean,
    habitAppUnavailable: Boolean,
    photoAddedHabit: HabitSlotSelection,
    faceScoreHabit: HabitSlotSelection,
    bodyScoreHabit: HabitSlotSelection,
    scoreSyncInProgress: Boolean,
    onSelectHabit: (HabitIntegrationRepository.Slot, HabitEntry) -> Unit,
    onClearHabit: (HabitIntegrationRepository.Slot) -> Unit,
    onSendScores: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tail App Integration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose which habit to increment when a photo is added, and which " +
                                "habits receive the daily skin scores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when {
                habitAppUnavailable ->
                    Text(
                        "Tail app not found. Make sure it is installed and tap Refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                !isLoading && habitList.isEmpty() ->
                    Text(
                        "No habits found. Tap Refresh to load from the Tail app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }

            HorizontalDivider()

            HabitSlotRow(
                title = "Photo Added",
                subtitle = "Incremented once per photo.",
                selection = photoAddedHabit,
                habitList = habitList,
                onSelect = { entry ->
                    onSelectHabit(HabitIntegrationRepository.Slot.PHOTO_ADDED, entry)
                },
                onClear = { onClearHabit(HabitIntegrationRepository.Slot.PHOTO_ADDED) }
            )

            HorizontalDivider()

            HabitSlotRow(
                title = "Face Skin Score",
                subtitle = "Daily average face rating is written as the habit's value per day.",
                selection = faceScoreHabit,
                habitList = habitList,
                onSelect = { entry ->
                    onSelectHabit(HabitIntegrationRepository.Slot.FACE_SCORE, entry)
                },
                onClear = { onClearHabit(HabitIntegrationRepository.Slot.FACE_SCORE) }
            )

            HabitSlotRow(
                title = "Body Skin Score",
                subtitle = "Daily average body rating is written as the habit's value per day.",
                selection = bodyScoreHabit,
                habitList = habitList,
                onSelect = { entry ->
                    onSelectHabit(HabitIntegrationRepository.Slot.BODY_SCORE, entry)
                },
                onClear = { onClearHabit(HabitIntegrationRepository.Slot.BODY_SCORE) }
            )

            HorizontalDivider()

            // Manual full-history push (bypasses the daily throttle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Score History", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Sent automatically once a day when a photo is added. " +
                                "Past scores shift as comparisons are made, so the full " +
                                "history is re-sent (only changed days are transmitted).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (scoreSyncInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    OutlinedButton(
                        onClick = onSendScores,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        enabled = faceScoreHabit.isSet || bodyScoreHabit.isSet
                    ) {
                        Text("Send Scores Now", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitSlotRow(
    title: String,
    subtitle: String,
    selection: HabitSlotSelection,
    habitList: List<HabitEntry>,
    onSelect: (HabitEntry) -> Unit,
    onClear: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (selection.isSet) {
                    Text(
                        selection.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selection.isSet) {
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (selection.isSet) "Change" else "Set",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (isExpanded) {
            Spacer(Modifier.height(8.dp))
            if (habitList.isEmpty()) {
                Text(
                    "No habits available. Tap Refresh above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                // Search filter — the Tail app can expose dozens of habits.
                var searchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search habits") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(6.dp))

                val filtered = if (searchQuery.isBlank()) habitList
                else habitList.filter {
                    it.habitName.contains(searchQuery.trim(), ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    Text(
                        "No habits match \"${searchQuery.trim()}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        filtered.forEach { entry ->
                            HabitPickerRow(
                                entry = entry,
                                isSelected = entry.habitId == selection.habitId,
                                onClick = {
                                    onSelect(entry)
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitPickerRow(
    entry: HabitEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.habitName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Text("✓", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ── Debug Mode card ──────────────────────────────────────────────────────────

@Composable
private fun DebugModeCard(
    debugModeEnabled: Boolean,
    debugFileDirUri: String,
    onToggleDebugMode: (Boolean) -> Unit,
    onChooseDirectory: () -> Unit,
    onClearDirectory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🐛 Debug Mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Show a floating bubble on every screen. Tap it to log bugs, features, or notes " +
                    "that are saved with the current screen's source file info to debug_skin_tracker.json.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Enable/disable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Debug Bubble", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (debugModeEnabled) "Bubble is visible" else "Bubble is hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (debugModeEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = debugModeEnabled,
                    onCheckedChange = onToggleDebugMode,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF4CAF50),
                        checkedThumbColor = Color.White
                    )
                )
            }

            // File directory (only shown when debug mode is on)
            if (debugModeEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "Choose the folder where debug_skin_tracker.json will be written.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (debugFileDirUri.isNotBlank()) {
                            val displayPath = try {
                                Uri.parse(debugFileDirUri).lastPathSegment ?: debugFileDirUri
                            } catch (_: Exception) { debugFileDirUri }
                            Text(
                                displayPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Using app internal storage (default)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (debugFileDirUri.isNotBlank()) {
                            IconButton(onClick = onClearDirectory, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onChooseDirectory,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (debugFileDirUri.isNotBlank()) "Change" else "Choose Folder",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Backup & Restore card ────────────────────────────────────────────────────

@Composable
private fun BackupCard(
    backupDirUri: String,
    autoBackupEnabled: Boolean,
    lastBackupAt: Long,
    lastBackupPhotoCount: Int,
    backupInProgress: Boolean,
    restoreInProgress: Boolean,
    onChooseFolder: () -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit
) {
    val configured = backupDirUri.isNotBlank()
    val busy = backupInProgress || restoreInProgress
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💾 Backup & Restore", style = MaterialTheme.typography.titleMedium)
            Text(
                "Copies all your photos and rating data to a folder OUTSIDE the app so it survives " +
                    "even a full uninstall. Pick a cloud-synced folder (Google Drive, Nextcloud…) or " +
                    "an SD card for the safest results. Backups run automatically on every launch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Folder chooser row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup folder", style = MaterialTheme.typography.bodyMedium)
                    if (configured) {
                        val displayPath = try {
                            Uri.parse(backupDirUri).lastPathSegment ?: backupDirUri
                        } catch (_: Exception) { backupDirUri }
                        Text(
                            displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Not set — choose a folder to enable backups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                OutlinedButton(
                    onClick = onChooseFolder,
                    enabled = !busy,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (configured) "Change" else "Choose Folder",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (configured) {
                // Auto-backup toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic backup", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (autoBackupEnabled) "On — backs up on every launch" else "Off — backup manually only",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoBackupEnabled) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = onToggleAutoBackup,
                        enabled = !busy,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF4CAF50),
                            checkedThumbColor = Color.White
                        )
                    )
                }

                // Last backup status
                val lastBackupText = if (lastBackupAt > 0L) {
                    val sdf = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
                    "${sdf.format(Date(lastBackupAt))} ($lastBackupPhotoCount photos)"
                } else {
                    "Never"
                }
                StatsRow("Last backup", lastBackupText)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBackupNow,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (backupInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Back up now")
                        }
                    }
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (restoreInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "This will REPLACE all photos and rating data currently in the app with the " +
                        "contents of the backup folder. Use this after reinstalling the app. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    onRestore()
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
