package com.example.skin_tracker.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

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

    when {
        state.isLoading || state.photo == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
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
