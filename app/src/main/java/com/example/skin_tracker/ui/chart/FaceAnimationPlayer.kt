package com.example.skin_tracker.ui.chart

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Displays the face animation area: the current normalised face image with a
 * crossfade transition, the date label, and playback controls.
 *
 * @param frames       Prepared animation frames (normalised bitmaps).
 * @param currentIndex Index of the frame currently shown.
 * @param isPlaying    Whether the animation is actively advancing.
 * @param isPreparing  Whether frames are still being prepared.
 * @param onPlay       Called when the user taps play.
 * @param onPause      Called when the user taps pause.
 * @param onStop       Called when the user taps stop (closes the animation).
 */
@Composable
fun FaceAnimationPlayer(
    frames: List<FaceAnimationFrame>,
    currentIndex: Int,
    isPlaying: Boolean,
    isPreparing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Face image area ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isPreparing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Normalising faces…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (frames.isNotEmpty() && currentIndex in frames.indices) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "faceCrossfade"
                ) { index ->
                    val f = frames.getOrNull(index)
                    if (f?.bitmap != null) {
                        Image(
                            bitmap = f.bitmap.asImageBitmap(),
                            contentDescription = "Face ${f.dateLabel}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback: load the original image
                        AsyncImage(
                            model = java.io.File(f?.photo?.uri ?: ""),
                            contentDescription = "Face ${f?.dateLabel}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Date label + frame counter ────────────────────────────────────
        if (frames.isNotEmpty() && currentIndex in frames.indices) {
            Text(
                text = "${frames[currentIndex].dateLabel}  " +
                        "(${currentIndex + 1}/${frames.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Playback controls ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause toggle
            IconButton(onClick = { if (isPlaying) onPause() else onPlay() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Stop / close
            IconButton(
                onClick = onStop,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Stop animation",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
