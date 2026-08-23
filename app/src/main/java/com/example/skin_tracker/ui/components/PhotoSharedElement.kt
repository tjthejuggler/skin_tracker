package com.example.skin_tracker.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.example.skin_tracker.domain.model.Photo
import java.io.File

/**
 * Tags an image as the shared element for [photoId] so it smoothly morphs between
 * screens (Compare card -> PhotoDetail image and back).
 *
 * Uses [SharedTransitionScope.sharedBounds] with a soft non-bouncy spring for the
 * bounds and animated corner rounding, so the growth glides instead of snapping.
 * Returns an empty [Modifier] when the transition scopes are unavailable (e.g. previews).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun photoSharedElementModifier(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    photoId: Long
): Modifier {
    val sts = sharedTransitionScope ?: return Modifier
    val avs = animatedVisibilityScope ?: return Modifier
    return with(sts) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = "photo-$photoId"),
            animatedVisibilityScope = avs,
            boundsTransform = BoundsTransform { _, _ ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp))
        )
    }
}

/**
 * Coil request shared by every screen that shows [photo]. Pinning the memory-cache
 * key and the decode size (screen pixels) means the detail screen gets an instant
 * memory-cache hit for the bitmap the compare card already displayed — no re-decode
 * flash in the middle of the shared-element morph.
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
