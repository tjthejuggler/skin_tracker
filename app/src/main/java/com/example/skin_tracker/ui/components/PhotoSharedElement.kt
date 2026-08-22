package com.example.skin_tracker.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Returns a [Modifier] tagging an image as the shared element for [photoId], so it
 * smoothly morphs between screens (e.g. Compare card -> PhotoDetail image and back).
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
        Modifier.sharedElement(
            rememberSharedContentState(key = "photo-$photoId"),
            animatedVisibilityScope = avs
        )
    }
}
