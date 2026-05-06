package com.example.skin_tracker.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.skin_tracker.domain.model.Category

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val category by viewModel.category.collectAsState()
    val pendingPhoto by viewModel.pendingPhoto.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveComplete by viewModel.saveComplete.collectAsState()

    LaunchedEffect(saveComplete) {
        if (saveComplete) {
            Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
            viewModel.clearSaveComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (pendingPhoto != null) {
            ConfirmationView(
                bitmap = pendingPhoto!!.bitmap,
                category = category,
                isSaving = isSaving,
                onSave = { viewModel.savePhoto() },
                onRetake = { viewModel.retake() },
                onCategoryChange = { viewModel.setCategory(it) }
            )
        } else if (hasCameraPermission) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                onImageCaptured = { viewModel.onImageCaptured(it) }
            )

            // Category toggle
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Category.entries.forEach { cat ->
                    val selected = cat == category
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
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to take photos")
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onImageCaptured: (androidx.camera.core.ImageProxy) -> Unit
) {
    val context = LocalContext.current
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                    } catch (_: Exception) { }
                }, executor)
                previewView
            },
            update = { previewView ->
                val executor = ContextCompat.getMainExecutor(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                    } catch (_: Exception) { }
                }, executor)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Flip camera button
        IconButton(
            onClick = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
        ) {
            Icon(
                Icons.Default.Cameraswitch,
                contentDescription = "Flip camera",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Shutter button
        IconButton(
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                            onImageCaptured(image)
                        }
                        override fun onError(exception: ImageCaptureException) { }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Take photo",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun ConfirmationView(
    bitmap: android.graphics.Bitmap,
    category: Category,
    isSaving: Boolean,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onCategoryChange: (Category) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = bitmap,
            contentDescription = "Captured photo",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { cat ->
                val selected = cat == category
                FilledTonalButton(
                    onClick = { onCategoryChange(cat) },
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledTonalButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Icon(Icons.Default.Close, contentDescription = "Retake")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retake")
            }

            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSaving) "Saving..." else "Save")
            }
        }
    }
}
