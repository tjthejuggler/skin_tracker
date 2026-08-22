package com.example.skin_tracker.ui.chart

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.DayNote
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.ui.components.SelectableChip
import com.example.skin_tracker.ui.notes.DayNoteDialog
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
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    onPhotoClick: (Long) -> Unit,
    playTrigger: Int = 0,
    viewModel: ChartViewModel = viewModel(factory = ChartViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    var chartRef by remember { mutableStateOf<LineChart?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    // Day the note dialog should open on (null = today). Set when opening
    // from the photo bottom sheet so the note targets the shown photo's day.
    var noteDialogDay by remember { mutableStateOf<Long?>(null) }

    val animationActive = state.animationPlaying || state.animationPreparing

    // ── React to play button taps from the TopAppBar ─────────────────────
    LaunchedEffect(playTrigger) {
        if (playTrigger > 0) viewModel.startAnimation()
    }

    // ── Highlight the chart node for the current animation frame ──────────
    // Uses notifyListener = false so it doesn't trigger the bottom sheet.
    LaunchedEffect(state.animationIndex, state.animationFrames, animationActive) {
        val chart = chartRef ?: return@LaunchedEffect
        if (!animationActive) {
            chart.highlightValue(null, false)
            return@LaunchedEffect
        }
        val frame = state.animationFrames.getOrNull(state.animationIndex)
        if (frame != null) {
            chart.highlightValue(
                frame.photo.capturedAt.toFloat(),
                frame.photo.rating.toFloat(),
                0,
                false
            )
        }
    }

    // ── Keep the chart highlight in sync with the bottom-sheet photo ──────
    // Fires on tap-selection and when day-stepping via the sheet arrows, so
    // the highlighted node behind the sheet always matches the shown photo.
    LaunchedEffect(state.selectedPhoto, state.showBottomSheet, animationActive) {
        val chart = chartRef ?: return@LaunchedEffect
        if (animationActive) return@LaunchedEffect
        val photo = state.selectedPhoto
        if (!state.showBottomSheet || photo == null) {
            chart.highlightValue(null, false)
            return@LaunchedEffect
        }
        chart.highlightValue(photo.capturedAt.toFloat(), photo.rating.toFloat(), 0, false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (animationActive) {
            // ── Animation mode: show player, hide chart controls ───────────
            FaceAnimationPlayer(
                frames = state.animationFrames,
                currentIndex = state.animationIndex,
                isPlaying = state.animationPlaying,
                isPreparing = state.animationPreparing,
                onPlay = { viewModel.resumeAnimation() },
                onPause = { viewModel.pauseAnimation() },
                onStop = { viewModel.stopAnimation() }
            )

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // ── Normal mode: category tabs + play button ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
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

            // Period navigation: ← label →  (+ day-note button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
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

                // Day notes: tinted when at least one note is visible on this chart
                val hasVisibleNotes = state.notes.any { it.target.appliesTo(state.category) }
                IconButton(
                    onClick = {
                        noteDialogDay = null
                        showNoteDialog = true
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (hasVisibleNotes) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.StickyNote2,
                        contentDescription = "Day notes",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

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
            val notes = state.notes
            val category = state.category
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
                    chartRef = chart

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
                    updateChart(chart, photos, timeRange, notes, category)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    // Day notes dialog
    if (showNoteDialog) {
        DayNoteDialog(
            notes = state.notes,
            initialEpochDay = noteDialogDay,
            onDismiss = { showNoteDialog = false },
            onSave = viewModel::saveNote,
            onDelete = viewModel::deleteNote
        )
    }

    // Bottom sheet for selected photo
    if (state.showBottomSheet && state.selectedPhoto != null) {
        val photo = state.selectedPhoto!!
        val zone = ZoneId.systemDefault()
        val photoDay = Instant.ofEpochMilli(photo.capturedAt).atZone(zone).toLocalDate()
        val hasEarlierDay = state.photos.any {
            Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() < photoDay
        }
        val hasLaterDay = state.photos.any {
            Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() > photoDay
        }
        val dayNotes = state.notes.filter { it.epochDay == photoDay.toEpochDay() }

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissBottomSheet() },
            sheetState = sheetState
        ) {
            PhotoBottomSheet(
                photo = photo,
                dayNotes = dayNotes,
                hasEarlierDay = hasEarlierDay,
                hasLaterDay = hasLaterDay,
                onStepDay = { forward -> viewModel.stepSelectedPhotoDay(forward) },
                onAddNote = {
                    noteDialogDay = photoDay.toEpochDay()
                    showNoteDialog = true
                },
                onPhotoClick = {
                    onPhotoClick(photo.id)
                    viewModel.dismissBottomSheet()
                }
            )
        }
    }
}

private fun updateChart(
    chart: LineChart,
    photos: List<Photo>,
    timeRange: TimeRange,
    notes: List<DayNote>,
    category: Category
) {
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

    // ── Day-note markers ─────────────────────────────────────────────────
    // Small amber dots just below the data on every day that carries a note
    // applicable to the current category (Both / Face / Body).
    val visibleNotes = notes.filter { it.target.appliesTo(category) }
    if (visibleNotes.isEmpty()) {
        chart.axisLeft.resetAxisMinimum()
    } else {
        val minY = photos.minOf { it.rating }
        val maxY = photos.maxOf { it.rating }
        val span = maxOf(maxY - minY, 1.0)
        val markY = (minY - span * 0.12).toFloat()

        val noteEntries = visibleNotes.map { note ->
            Entry(note.chartTimestampMillis().toFloat(), markY)
        }
        dataSets.add(
            LineDataSet(noteEntries, "Notes").apply {
                color = Color.TRANSPARENT          // no connecting line
                lineWidth = 0f
                setCircleColor(Color.parseColor("#FFD54F"))  // Amber
                circleRadius = 5f
                setDrawCircleHole(false)
                setDrawValues(false)
                isHighlightEnabled = false
            }
        )
        // Make room beneath the curve so the markers stay visible.
        chart.axisLeft.axisMinimum = markY - (span * 0.04).toFloat()
    }

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
    dayNotes: List<DayNote>,
    hasEarlierDay: Boolean,
    hasLaterDay: Boolean,
    onStepDay: (forward: Boolean) -> Unit,
    onAddNote: () -> Unit,
    onPhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Photo with day-stepping arrows overlaid on the sides
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(photo.uri),
                contentDescription = "Selected photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            if (hasEarlierDay) {
                HoldRepeatArrowButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous day (hold to cycle)",
                    onClick = { onStepDay(false) },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            if (hasLaterDay) {
                HoldRepeatArrowButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next day (hold to cycle)",
                    onClick = { onStepDay(true) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

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

        // Notes attached to this day
        if (dayNotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            dayNotes.forEach { note ->
                Text(
                    text = "📝 ${note.target.label}: ${note.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onPhotoClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("View Full Photo")
            }
            OutlinedButton(onClick = onAddNote) {
                Icon(
                    Icons.Default.StickyNote2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Note")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Translucent circular arrow button that fires [onClick] once on press and
 * then keeps re-firing at a quickening interval while held down —
 * press-and-hold to rapidly cycle through days.
 */
@Composable
private fun HoldRepeatArrowButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    // Initial fire on press, a short pause, then accelerating repeats.
    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        onClick()
        delay(320L)
        var interval = 200L
        while (isPressed) {
            onClick()
            delay(interval)
            interval = (interval - 30L).coerceAtLeast(80L)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    }
                )
            }
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
