package com.example.skin_tracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.example.skin_tracker.domain.model.Photo
import java.io.File

/** Corner radius used by photo cards (compare cards). */
val SharedPhotoCorner = 12.dp

/**
 * Coil request shared by every screen that shows [photo]. Pinning the memory-cache
 * key and the decode size (screen pixels) means the detail screen gets an instant
 * memory-cache hit for the bitmap the compare card already displayed — no re-decode
 * flash during screen transitions.
 */
@Composable
fun rememberPhotoImageRequest(photo: Photo): ImageRequest {
    val context = LocalContext.current
    return remember(photo.id, photo.uri) {
        val metrics = context.resources.displayMetrics
        ImageRequest.Builder(context)
            .data(File(photo.uri))
            .memoryCacheKey("photo-${photo.id}")
            .size(metrics.widthPixels, metrics.heightPixels)
            .crossfade(false)
            .build()
    }
}
