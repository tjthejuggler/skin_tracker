package com.example.skin_tracker.ui.chart

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.ui.components.SelectableChip
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    onPhotoClick: (Long) -> Unit,
    viewModel: ChartViewModel = viewModel(factory = ChartViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Category tabs (Face / Body)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Category.entries.forEach { cat ->
                SelectableChip(
                    label = cat.label,
                    selected = cat == state.category,
                    onClick = { viewModel.setCategory(cat) },
                    shape = RoundedCornerShape(20.dp)
                )
                if (cat != Category.entries.last()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time range chips — compact, all fit on one row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimeRange.entries.forEach { range ->
                SelectableChip(
                    label = range.label,
                    selected = range == state.timeRange,
                    onClick = { viewModel.setTimeRange(range) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp, vertical = 4.dp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Period navigation: ← label →
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = { viewModel.stepBack() },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous period",
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = state.periodLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = { viewModel.stepForward() },
                enabled = state.periodOffset > 0,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next period",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (state.photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No data yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add photos and compare them to see your trend",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Chart
            val photos = state.photos
            val timeRange = state.timeRange
            val onPhotoSelected: (Photo) -> Unit = { photo -> viewModel.onPhotoSelected(photo) }

            AndroidView(
                factory = { ctx ->
                    LineChart(ctx).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        isDoubleTapToZoomEnabled = true
                        axisRight.isEnabled = false

                        // Dark background for the chart
                        setBackgroundColor(Color.TRANSPARENT)

                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.granularity = 1f
                        xAxis.textColor = Color.WHITE
                        xAxis.gridColor = Color.argb(60, 255, 255, 255)

                        axisLeft.textColor = Color.WHITE
                        axisLeft.gridColor = Color.argb(60, 255, 255, 255)
                        axisLeft.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return String.format("%.0f", value)
                            }
                        }

                        legend.textColor = Color.WHITE
                    }
                },
                update = { chart ->
                    // Update x-axis formatter based on current time range
                    val xPattern = when (timeRange) {
                        TimeRange.ONE_WEEK, TimeRange.ONE_MONTH -> "d"
                        TimeRange.THREE_MONTHS -> "M/d"
                        else -> "MMM yyyy"
                    }
                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val sdf = SimpleDateFormat(xPattern, Locale.getDefault())
                            return sdf.format(Date(value.toLong()))
                        }
                    }

                    // Re-register listener here so it always captures the current photos list
                    chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry, h: Highlight) {
                            val photo = photos.minByOrNull {
                                Math.abs(it.capturedAt - e.x.toLong()) + Math.abs(it.rating - e.y.toDouble())
                            }
                            if (photo != null) {
                                onPhotoSelected(photo)
                            }
                        }

                        override fun onNothingSelected() {}
                    })
                    updateChart(chart, photos, timeRange)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    // Bottom sheet for selected photo
    if (state.showBottomSheet && state.selectedPhoto != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissBottomSheet() },
            sheetState = sheetState
        ) {
            PhotoBottomSheet(
                photo = state.selectedPhoto!!,
                onPhotoClick = {
                    onPhotoClick(state.selectedPhoto!!.id)
                    viewModel.dismissBottomSheet()
                }
            )
        }
    }
}

private fun updateChart(chart: LineChart, photos: List<Photo>, timeRange: TimeRange) {
    if (photos.isEmpty()) return

    // Per-photo entries
    val photoEntries = photos.map { photo ->
        Entry(photo.capturedAt.toFloat(), photo.rating.toFloat())
    }

    val photoDataSet = LineDataSet(photoEntries, "Rating").apply {
        color = Color.parseColor("#64B5F6")  // Light blue
        setCircleColor(Color.parseColor("#64B5F6"))
        circleRadius = 4f
        lineWidth = 2f
        setDrawValues(false)
        isHighlightEnabled = true
        highLightColor = Color.parseColor("#FFB74D")  // Orange highlight
    }

    // Centered moving average — window size scales with time range
    val window = timeRange.movingAverageWindow
    val avgEntries = computeMovingAverage(photoEntries, window)

    val avgDataSet = if (avgEntries.size > 1) {
        LineDataSet(avgEntries, "Trend (${window}pt)").apply {
            color = Color.parseColor("#FFB74D")  // Orange
            lineWidth = 2.5f
            setDrawCircles(false)
            setDrawValues(false)
            isHighlightEnabled = false
        }
    } else null

    val dataSets = mutableListOf(photoDataSet)
    avgDataSet?.let { dataSets.add(it) }

    chart.data = LineData(dataSets.toList())
    chart.invalidate()
}

/**
 * Centered moving average over [window] data-points.
 * At the edges the window shrinks so we still produce a value (no truncation).
 */
private fun computeMovingAverage(entries: List<Entry>, window: Int): List<Entry> {
    if (entries.size < 2 || window < 2) return emptyList()
    val half = window / 2
    return entries.mapIndexed { i, entry ->
        val from = maxOf(0, i - half)
        val to = minOf(entries.lastIndex, i + half)
        val avg = entries.subList(from, to + 1).map { it.y }.average().toFloat()
        Entry(entry.x, avg)
    }
}

@Composable
private fun PhotoBottomSheet(
    photo: Photo,
    onPhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = File(photo.uri),
            contentDescription = "Selected photo",
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(12.dp))

        val sdf = SimpleDateFormat("EEEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault())
        Text(
            text = sdf.format(Date(photo.capturedAt)),
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Rating: ${String.format("%.0f", photo.rating)}  •  " +
                    "W/L: ${photo.wins}/${photo.losses}  •  " +
                    "Comparisons: ${photo.comparisonCount}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        androidx.compose.material3.FilledTonalButton(onClick = onPhotoClick) {
            Text("View Full Photo")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
