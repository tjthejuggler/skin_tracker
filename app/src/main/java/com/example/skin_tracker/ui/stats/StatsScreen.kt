package com.example.skin_tracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skin_tracker.domain.model.Photo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
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
            StatsCard(
                stats = stats,
                onRefresh = { viewModel.loadStats() }
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
                StatsRow("This week", "${stats.photosThisWeek}")
                StatsRow("This month", "${stats.photosThisMonth}")

                HorizontalDivider()

                // Comparisons — counts with the calculated time (count × time-per-comparison)
                // in parentheses, each category followed by its own rate.
                StatsSectionTitle("Comparisons")
                StatsRow("Total comparisons", countWithTime(stats.totalComparisons, stats.avgComparisonMs))
                if (stats.avgComparisonMs > 0) {
                    StatsRow("Time per comparison", formatPerComparison(stats.avgComparisonMs))
                }
                StatsRow("Face comparisons", countWithTime(stats.faceComparisons, stats.avgFaceComparisonMs))
                if (stats.avgFaceComparisonMs > 0) {
                    StatsRow("Time per face comparison", formatPerComparison(stats.avgFaceComparisonMs))
                }
                StatsRow("Body comparisons", countWithTime(stats.bodyComparisons, stats.avgBodyComparisonMs))
                if (stats.avgBodyComparisonMs > 0) {
                    StatsRow("Time per body comparison", formatPerComparison(stats.avgBodyComparisonMs))
                }
                StatsRow("This week", countWithTime(stats.comparisonsThisWeek, stats.avgComparisonMs))
                StatsRow("This month", countWithTime(stats.comparisonsThisMonth, stats.avgComparisonMs))
                if (stats.avgComparisonsPerDay > 0) {
                    StatsRow(
                        "Avg / active day",
                        valueWithTime(
                            String.format("%.1f", stats.avgComparisonsPerDay),
                            stats.avgComparisonsPerDay * stats.avgComparisonMs,
                            stats.avgComparisonMs
                        )
                    )
                }

                HorizontalDivider()

                // Top rated section
                StatsSectionTitle("Top Rated — All Time")
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

                // Top rated by period
                StatsSectionTitle("Top Rated — This Week")
                stats.topRatedThisWeek?.let { photo ->
                    TopRatedRow("Best this week", photo)
                } ?: StatsRow("Best this week", "No photos yet")

                StatsSectionTitle("Top Rated — This Month")
                stats.topRatedThisMonth?.let { photo ->
                    TopRatedRow("Best this month", photo)
                } ?: StatsRow("Best this month", "No photos yet")

                StatsSectionTitle("Top Rated — This Year")
                stats.topRatedThisYear?.let { photo ->
                    TopRatedRow("Best this year", photo)
                } ?: StatsRow("Best this year", "No photos yet")

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

// ── Formatting helpers ────────────────────────────────────────────────────────

/**
 * Count with the CALCULATED total time in parentheses: count × time-per-comparison.
 * Shown only once a rate exists (needs at least one timed comparison).
 */
private fun countWithTime(count: Int, rateMs: Double): String =
    if (rateMs > 0.0) "$count (${formatDuration((count * rateMs).toLong())})" else "$count"

/** Arbitrary value with a calculated time in parentheses (e.g. avg/day × time-per-comparison). */
private fun valueWithTime(value: String, calculatedMs: Double, rateMs: Double): String =
    if (rateMs > 0.0) "$value (${formatDuration(calculatedMs.toLong())})" else value

/** Per-comparison average time: "7.9s" (or "1m 03s" for slow categories). */
private fun formatPerComparison(ms: Double): String {
    val seconds = ms / 1000.0
    return if (seconds < 60.0) String.format("%.1fs", seconds) else formatDuration(ms.toLong())
}

/** Compact duration: "1h 4m", "4m 32s", or "45s". */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
