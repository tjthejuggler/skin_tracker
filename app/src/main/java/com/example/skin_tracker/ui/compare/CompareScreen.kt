package com.example.skin_tracker.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.skin_tracker.ui.components.SharedPhotoCorner
import com.example.skin_tracker.ui.components.rememberPhotoImageRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo

@Composable
fun CompareScreen(
    onHistoryClick: () -> Unit = {},
    onPhotoDetail: (Long) -> Unit = {},
    viewModel: CompareViewModel = viewModel(factory = CompareViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Top control row: undo, skip, zoom controls, history
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.undoLastComparison() },
                enabled = state.canUndo
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo last comparison",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { viewModel.skip() }) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Skip this pair",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Zoom controls: − [100%] +
            TextButton(
                onClick = { viewModel.zoomOut() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = "${(state.zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { viewModel.zoomIn() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }

            IconButton(onClick = onHistoryClick) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "Comparison History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats (+ focus-mode counter on the same line when active)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Comparisons today: ${state.comparisonsToday}  •  ${state.totalPhotos} photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.focusRemaining?.let { remaining ->
                Text(
                    text = "🎯 ${15 - remaining}/15",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (state.photoA == null || state.photoB == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Need at least 2 photos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add more ${state.category.label.lowercase()} photos to start comparing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Two photos stacked vertically — undo/skip flank the bottom photo
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ComparePhotoCard(
                    photo = state.photoA!!,
                    label = "A",
                    onTap = { viewModel.pickWinner(winnerIsA = true) },
                    onDetailClick = { onPhotoDetail(state.photoA!!.id) },
                    modifier = Modifier.weight(1f),
                    zoom = state.zoom
                )

                ComparePhotoCard(
                    photo = state.photoB!!,
                    label = "B",
                    onTap = { viewModel.pickWinner(winnerIsA = false) },
                    onDetailClick = { onPhotoDetail(state.photoB!!.id) },
                    modifier = Modifier.weight(1f),
                    zoom = state.zoom
                )
            }
        }
    }
}

@Composable
private fun ComparePhotoCard(
    photo: Photo,
    label: String,
    onTap: () -> Unit,
    onDetailClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    zoom: Float = 1f
) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(SharedPhotoCorner))) {
        AsyncImage(
            model = rememberPhotoImageRequest(photo),
            contentDescription = "Photo $label",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                }
                .clickable { onTap() },
            contentScale = ContentScale.Fit
        )
        
        // Detail button in top-right corner
        IconButton(
            onClick = onDetailClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Photo details",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
