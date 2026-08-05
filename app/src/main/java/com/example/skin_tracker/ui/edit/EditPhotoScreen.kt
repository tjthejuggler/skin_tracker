package com.example.skin_tracker.ui.edit

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skin_tracker.domain.model.Category

/**
 * Full-screen edit screen: shows the CropOverlay for the given photo.
 * Navigates back when the edit is confirmed or cancelled.
 */
@Composable
fun EditPhotoScreen(
    photoId: Long,
    onBack: () -> Unit,
    viewModel: EditPhotoViewModel = viewModel(
        factory = EditPhotoViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(photoId) {
        viewModel.loadPhoto(photoId)
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onBack()
    }

    var showCategoryDialog by remember { mutableStateOf(false) }

    when {
        state.isLoading || state.photo == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Category change button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current type: ${state.photo!!.category.label}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Tap to change type (resets rating)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { showCategoryDialog = true }) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Change type",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Change Type")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Crop overlay takes remaining space
                Box(modifier = Modifier.weight(1f)) {
                    CropOverlay(
                        imagePath = state.photo!!.uri,
                        onConfirm = { left, top, right, bottom, rotation ->
                            viewModel.applyCropAndRotate(left, top, right, bottom, rotation)
                        },
                        onCancel = onBack
                    )
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryChangeDialog(
            currentCategory = state.photo?.category ?: Category.FACE,
            onConfirm = { newCategory ->
                viewModel.changeCategory(newCategory)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false }
        )
    }
}

@Composable
private fun CategoryChangeDialog(
    currentCategory: Category,
    onConfirm: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(currentCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Image Type") },
        text = {
            Column {
                Text(
                    "Changing the image type will permanently delete all rating history " +
                        "for this photo and reset its rating to 1500. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Category.entries.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = category }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCategory) },
                enabled = selectedCategory != currentCategory
            ) {
                Text(
                    "Change",
                    color = if (selectedCategory != currentCategory)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
