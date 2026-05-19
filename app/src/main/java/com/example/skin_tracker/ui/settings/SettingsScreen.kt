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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.skin_tracker.domain.model.Photo
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

    LaunchedEffect(Unit) {
        viewModel.loadHabits()
    }

    val context = LocalContext.current
    val debugDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.setDebugFileDir(uri.toString())
        }
    }

    Scaffold(
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
            // ── Stats ─────────────────────────────────────────────────────
            StatsCard(
                stats = state.stats,
                onRefresh = { viewModel.loadStats() }
            )

            TailAppIntegrationCard(
                habitList = state.habitList,
                isLoading = state.isLoadingHabits,
                habitAppUnavailable = state.habitAppUnavailable,
                photoAddedHabit = state.photoAddedHabit,
                onSelectHabit = { entry ->
                    viewModel.selectHabit(HabitIntegrationRepository.Slot.PHOTO_ADDED, entry)
                },
                onClearHabit = {
                    viewModel.clearHabit(HabitIntegrationRepository.Slot.PHOTO_ADDED)
                },
                onRefresh = { viewModel.loadHabits() }
            )

            // ── Debug Mode ───────────────────────────────────────────────
            DebugModeCard(
                debugModeEnabled = state.debugModeEnabled,
                debugFileDirUri = state.debugFileDirUri,
                onToggleDebugMode = { viewModel.setDebugModeEnabled(it) },
                onChooseDirectory = { debugDirLauncher.launch(null) },
                onClearDirectory = { viewModel.clearDebugFileDir() }
            )
        }
    }
}

// ── Stats Card ────────────────────────────────────────────────────────────────

@Composable
private fun StatsCard(
    stats: AppStats,
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
                Text("📊 Statistics", style = MaterialTheme.typography.titleMedium)
                if (stats.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (!stats.isLoading) {
                HorizontalDivider()

                // Photos section
                StatsSectionTitle("Photos")
                StatsRow("Total photos", "${stats.totalPhotos}")
                StatsRow("Face photos", "${stats.facePhotos}")
                StatsRow("Body photos", "${stats.bodyPhotos}")

                HorizontalDivider()

                // Comparisons section
                StatsSectionTitle("Comparisons")
                StatsRow("Total comparisons", "${stats.totalComparisons}")
                StatsRow("Face comparisons", "${stats.faceComparisons}")
                StatsRow("Body comparisons", "${stats.bodyComparisons}")

                HorizontalDivider()

                // Top rated section
                StatsSectionTitle("Top Rated")
                stats.topRatedPhoto?.let { photo ->
                    TopRatedRow("Overall best", photo)
                } ?: StatsRow("Overall best", "No photos yet")

                stats.topRatedFacePhoto?.let { photo ->
                    TopRatedRow("Best face", photo)
                } ?: StatsRow("Best face", "No face photos yet")

                stats.topRatedBodyPhoto?.let { photo ->
                    TopRatedRow("Best body", photo)
                } ?: StatsRow("Best body", "No body photos yet")

                HorizontalDivider()

                // Streaks section
                StatsSectionTitle("Streaks")
                StatsRow("Current streak", "${stats.currentStreakDays} day${if (stats.currentStreakDays != 1) "s" else ""}")
                StatsRow("Longest streak", "${stats.longestStreakDays} day${if (stats.longestStreakDays != 1) "s" else ""}")
            }
        }
    }
}

@Composable
private fun StatsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
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

@Composable
private fun TopRatedRow(label: String, photo: Photo) {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateStr = sdf.format(Date(photo.capturedAt))
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format("%.0f pts", photo.rating),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Tail App Integration Card ─────────────────────────────────────────────────

@Composable
private fun TailAppIntegrationCard(
    habitList: List<HabitEntry>,
    isLoading: Boolean,
    habitAppUnavailable: Boolean,
    photoAddedHabit: HabitSlotSelection,
    onSelectHabit: (HabitEntry) -> Unit,
    onClearHabit: () -> Unit,
    onRefresh: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

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
                        "Choose which habit to increment when a photo is added.",
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

            // Single slot: Photo Added
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Photo Added", style = MaterialTheme.typography.bodyMedium)
                        if (photoAddedHabit.isSet) {
                            Text(
                                photoAddedHabit.displayName,
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
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (photoAddedHabit.isSet) {
                            IconButton(onClick = onClearHabit, modifier = Modifier.size(32.dp)) {
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
                                if (photoAddedHabit.isSet) "Change" else "Set",
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
                        habitList.forEach { entry ->
                            HabitPickerRow(
                                entry = entry,
                                isSelected = entry.habitId == photoAddedHabit.habitId,
                                onClick = {
                                    onSelectHabit(entry)
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
