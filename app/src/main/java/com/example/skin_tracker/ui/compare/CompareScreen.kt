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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import java.io.File

@Composable
fun CompareScreen(
    onHistoryClick: () -> Unit = {},
    viewModel: CompareViewModel = viewModel(factory = CompareViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top row: category tabs + history button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Category.entries.forEach { cat ->
                    val selected = cat == state.category
                    FilledTonalButton(
                        onClick = { viewModel.setCategory(cat) },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = cat.label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onHistoryClick) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "Comparison History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats
        Text(
            text = "Comparisons today: ${state.comparisonsToday}  •  ${state.totalPhotos} photos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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
            // Two photos stacked vertically
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ComparePhotoCard(
                    photo = state.photoA!!,
                    label = "A",
                    onTap = { viewModel.pickWinner(winnerIsA = true) },
                    modifier = Modifier.weight(1f)
                )

                ComparePhotoCard(
                    photo = state.photoB!!,
                    label = "B",
                    onTap = { viewModel.pickWinner(winnerIsA = false) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Tap the photo where your skin looks better",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { viewModel.skip() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Skip")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip this pair")
                }
            }
        }
    }
}

@Composable
private fun ComparePhotoCard(
    photo: Photo,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = File(photo.uri),
            contentDescription = "Photo $label",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onTap() },
            contentScale = ContentScale.Fit
        )
    }
}
