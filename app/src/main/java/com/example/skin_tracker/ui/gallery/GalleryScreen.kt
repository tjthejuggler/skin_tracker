package com.example.skin_tracker.ui.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Category
import java.io.File

@Composable
fun GalleryScreen(
    onPhotoClick: (Long) -> Unit,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val context = LocalContext.current
    val dayGroups by viewModel.dayGroups.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importCount by viewModel.importCount.collectAsState()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingUris = uris
            showCategoryDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (dayGroups.isEmpty() && !isImporting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No photos yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap + to import photos from your gallery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(dayGroups) { group ->
                    Text(
                        text = group.dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        group.photos.forEach { photo ->
                            AsyncImage(
                                model = File(photo.uri),
                                contentDescription = "Photo from ${group.dateLabel}",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPhotoClick(photo.id) },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Import FAB
        FloatingActionButton(
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import photos")
        }

        // Importing overlay
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Imported $importCount photos...")
                }
            }
        }
    }

    // Category selection dialog
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showCategoryDialog = false
                pendingUris = emptyList()
            },
            title = { Text("Select Category") },
            text = { Text("Are these Face or Body photos?") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    viewModel.importFromGallery(pendingUris, Category.FACE, context)
                    showCategoryDialog = false
                    pendingUris = emptyList()
                }) {
                    Text("Face")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showCategoryDialog = false
                        pendingUris = emptyList()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        viewModel.importFromGallery(pendingUris, Category.BODY, context)
                        showCategoryDialog = false
                        pendingUris = emptyList()
                    }) {
                        Text("Body")
                    }
                }
            }
        )
    }
}
