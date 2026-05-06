package com.example.skin_tracker.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Photo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photoId: Long,
    onBack: () -> Unit,
    onEditPhoto: (Long) -> Unit,
    viewModel: PhotoDetailViewModel = viewModel(factory = PhotoDetailViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(photoId) {
        viewModel.loadPhoto(photoId)
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Photo Detail") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    state.sameDayPhotos.getOrNull(state.currentIndex)?.let { onEditPhoto(it.id) }
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit photo")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        if (state.sameDayPhotos.isNotEmpty()) {
            val pagerState = rememberPagerState(
                initialPage = state.currentIndex,
                pageCount = { state.sameDayPhotos.size }
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.setCurrentIndex(pagerState.currentPage)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val photo = state.sameDayPhotos[page]
                PhotoDetailView(
                    photo = photo,
                    onDateChanged = { newDate -> viewModel.updateCapturedAt(newDate) }
                )
            }

            if (state.sameDayPhotos.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} of ${state.sameDayPhotos.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete this photo? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePhoto()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PhotoDetailView(photo: Photo, onDateChanged: (Long) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = File(photo.uri),
            contentDescription = "Photo detail",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(12.dp))

        val sdf = SimpleDateFormat("EEEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clickable { showDatePicker = true }
                .padding(4.dp)
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = "Change date",
                modifier = Modifier.padding(end = 6.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = sdf.format(Date(photo.capturedAt)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showDatePicker) {
            PhotoDatePickerDialog(
                currentCapturedAt = photo.capturedAt,
                onConfirm = { newDate ->
                    showDatePicker = false
                    onDateChanged(newDate)
                },
                onDismiss = { showDatePicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.0f", photo.rating),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Rating",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${photo.wins}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Wins",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${photo.losses}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Losses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${photo.comparisonCount}",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDatePickerDialog(
    currentCapturedAt: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Preserve the time-of-day from the original timestamp
    val originalCal = Calendar.getInstance().apply { timeInMillis = currentCapturedAt }
    val hour = originalCal.get(Calendar.HOUR_OF_DAY)
    val minute = originalCal.get(Calendar.MINUTE)
    val second = originalCal.get(Calendar.SECOND)

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentCapturedAt
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    // Combine the selected date with the original time-of-day
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = selectedMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, second)
                    }
                    onConfirm(newCal.timeInMillis)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
