package com.example.skin_tracker.ui.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/**
 * Full-screen crop editor.
 * - The crop rectangle is transparent (see-through) so the user can see the image underneath.
 * - Drag a corner handle to resize (aspect ratio locked to 1:1 square).
 * - Drag anywhere inside the crop rect to move it.
 * - Tap Rotate to spin the image 90° clockwise.
 */
@Composable
fun CropOverlay(
    imagePath: String,
    onConfirm: (cropLeft: Int, cropTop: Int, cropRight: Int, cropBottom: Int, rotationDegrees: Int) -> Unit,
    onCancel: () -> Unit
) {
    // Decode the ACTUAL stored file dimensions (already downscaled by PhotoFileStore)
    val options = remember(imagePath) {
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, this)
        }
    }
    // These are the real pixel dimensions of the file on disk
    val imgW = options.outWidth.toFloat()
    val imgH = options.outHeight.toFloat()

    var rotationDegrees by remember { mutableIntStateOf(0) }

    // Crop rect in normalised coords (0..1) relative to the *drawn image area*
    var cropRect by remember { mutableStateOf(Rect(0.15f, 0.15f, 0.85f, 0.85f)) }
    var dragCorner by remember { mutableStateOf<DragCorner?>(null) }

    // The Box's pixel size (set via onGloballyPositioned)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // Compute the drawn image area within the Box (accounts for ContentScale.Fit letterboxing).
    // NOTE: graphicsLayer { rotationZ } is purely visual — it does NOT change layout bounds.
    // So imageDrawRect always uses the original imgW × imgH regardless of rotationDegrees.
    val imageDrawRect = remember(boxSize, imgW, imgH) {
        if (boxSize == IntSize.Zero || imgW <= 0f || imgH <= 0f) {
            Rect(Offset.Zero, Size.Zero)
        } else {
            val scale = minOf(boxSize.width.toFloat() / imgW, boxSize.height.toFloat() / imgH)
            val drawW = imgW * scale
            val drawH = imgH * scale
            val ox = (boxSize.width - drawW) / 2f
            val oy = (boxSize.height - drawH) / 2f
            Rect(Offset(ox, oy), Size(drawW, drawH))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Image + overlay ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { boxSize = it.size }
                .pointerInput(imageDrawRect, rotationDegrees) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Touch coords are in unrotated layout space; rotate them to match
                            // the visual orientation before comparing with crop corners
                            val rotatedOffset = rotateOffset(offset, -rotationDegrees, imageDrawRect)
                            dragCorner = nearestCorner(rotatedOffset, cropRect, imageDrawRect)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val drawW = imageDrawRect.size.width
                            val drawH = imageDrawRect.size.height
                            if (drawW > 0f && drawH > 0f) {
                                // Rotate drag delta to match visual orientation
                                val rotatedDrag = rotateDelta(dragAmount, -rotationDegrees)
                                val dx = rotatedDrag.x / drawW
                                val dy = rotatedDrag.y / drawH
                                cropRect = moveCorner(cropRect, dragCorner, dx, dy)
                            }
                        },
                        onDragEnd = { dragCorner = null },
                        onDragCancel = { dragCorner = null }
                    )
                }
        ) {
            // The photo, rotated visually
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Crop target",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotationDegrees.toFloat() },
                contentScale = ContentScale.Fit
            )

            // Overlay canvas — rotates with the image; Offscreen compositing for BlendMode.Clear
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotationDegrees.toFloat()
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                if (imageDrawRect.size == Size.Zero) return@Canvas

                val left   = imageDrawRect.left + cropRect.left   * imageDrawRect.size.width
                val top    = imageDrawRect.top  + cropRect.top    * imageDrawRect.size.height
                val right  = imageDrawRect.left + cropRect.right  * imageDrawRect.size.width
                val bottom = imageDrawRect.top  + cropRect.bottom * imageDrawRect.size.height

                // 1. Fill entire canvas with semi-transparent black
                drawRect(Color.Black.copy(alpha = 0.55f))

                // 2. Punch a transparent hole for the crop area (requires Offscreen layer)
                drawPath(
                    Path().apply { addRect(Rect(left, top, right, bottom)) },
                    color = Color.Transparent,
                    blendMode = BlendMode.Clear
                )

                // 3. White border
                drawRect(
                    Color.White,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 3f)
                )

                // 4. Corner handles
                val h = 28f
                listOf(
                    Offset(left, top),
                    Offset(right, top),
                    Offset(left, bottom),
                    Offset(right, bottom)
                ).forEach { c ->
                    drawRect(
                        Color.White,
                        topLeft = Offset(c.x - h / 2, c.y - h / 2),
                        size = Size(h, h)
                    )
                }

                // 5. Rule-of-thirds grid
                val tw = (right - left) / 3f
                val th = (bottom - top) / 3f
                val grid = Color.White.copy(alpha = 0.4f)
                for (i in 1..2) {
                    drawLine(grid, Offset(left + tw * i, top), Offset(left + tw * i, bottom), strokeWidth = 1.5f)
                    drawLine(grid, Offset(left, top + th * i), Offset(right, top + th * i), strokeWidth = 1.5f)
                }
            }
        }

        // ── Action buttons ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }

            // Rotate
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90°")
                }
                Text("Rotate", style = MaterialTheme.typography.labelSmall)
            }

            // Apply / confirm
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {
                        if (imgW > 0f && imgH > 0f) {
                            // PhotoFileStore.crop() rotates the bitmap first, then crops.
                            // So pixel coords must be relative to the rotated image dimensions.
                            val (effW, effH) = if (rotationDegrees % 180 == 0) imgW to imgH else imgH to imgW
                            val cropLeft   = (cropRect.left   * effW).toInt().coerceAtLeast(0)
                            val cropTop    = (cropRect.top    * effH).toInt().coerceAtLeast(0)
                            val cropRight  = (cropRect.right  * effW).toInt().coerceAtMost(effW.toInt())
                            val cropBottom = (cropRect.bottom * effH).toInt().coerceAtMost(effH.toInt())
                            onConfirm(cropLeft, cropTop, cropRight, cropBottom, rotationDegrees)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Apply crop")
                }
                Text("Apply", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Drag helpers ─────────────────────────────────────────────────────────────

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

/**
 * Determine which part of the crop rect the user touched.
 * Both [offset] (touch position) and the computed corner positions are in pixel coordinates
 * relative to the Box. The crop corners are converted from normalised (0..1) to pixels
 * using [imageDrawRect] (the actual drawn image area within the Box).
 */
private fun nearestCorner(offset: Offset, cropRect: Rect, imageDrawRect: Rect): DragCorner {
    if (imageDrawRect.size == Size.Zero) return DragCorner.CENTER

    // Convert normalised crop corners → pixel positions within the Box
    val tl = Offset(
        imageDrawRect.left + cropRect.left  * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.top    * imageDrawRect.size.height
    )
    val tr = Offset(
        imageDrawRect.left + cropRect.right * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.top    * imageDrawRect.size.height
    )
    val bl = Offset(
        imageDrawRect.left + cropRect.left  * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.bottom * imageDrawRect.size.height
    )
    val br = Offset(
        imageDrawRect.left + cropRect.right * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.bottom * imageDrawRect.size.height
    )

    // Generous touch target for fat fingers
    val threshold = 80f

    val distances = mapOf(
        DragCorner.TOP_LEFT     to (offset - tl).getDistance(),
        DragCorner.TOP_RIGHT    to (offset - tr).getDistance(),
        DragCorner.BOTTOM_LEFT  to (offset - bl).getDistance(),
        DragCorner.BOTTOM_RIGHT to (offset - br).getDistance()
    )
    val nearest = distances.minByOrNull { it.value }
    return if (nearest != null && nearest.value < threshold) nearest.key else DragCorner.CENTER
}

/**
 * Resize or translate the crop rect.
 * Corner drags maintain a 1:1 aspect ratio; CENTER drag translates the whole rect.
 * [dx]/[dy] are in normalised fractions relative to the image area.
 */
private fun moveCorner(rect: Rect, corner: DragCorner?, dx: Float, dy: Float): Rect {
    val minSize = 0.05f
    return when (corner) {
        DragCorner.TOP_LEFT -> {
            val newLeft = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            val newWidth = rect.right - newLeft
            val newTop = (rect.bottom - newWidth).coerceIn(0f, rect.bottom - minSize)
            Rect(newLeft, newTop, rect.right, rect.bottom)
        }
        DragCorner.TOP_RIGHT -> {
            val newRight = (rect.right + dx).coerceIn(rect.left + minSize, 1f)
            val newWidth = newRight - rect.left
            val newTop = (rect.bottom - newWidth).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, newTop, newRight, rect.bottom)
        }
        DragCorner.BOTTOM_LEFT -> {
            val newLeft = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            val newWidth = rect.right - newLeft
            val newBottom = (rect.top + newWidth).coerceIn(rect.top + minSize, 1f)
            Rect(newLeft, rect.top, rect.right, newBottom)
        }
        DragCorner.BOTTOM_RIGHT -> {
            val newRight = (rect.right + dx).coerceIn(rect.left + minSize, 1f)
            val newWidth = newRight - rect.left
            val newBottom = (rect.top + newWidth).coerceIn(rect.top + minSize, 1f)
            Rect(rect.left, rect.top, newRight, newBottom)
        }
        DragCorner.CENTER -> {
            val w = rect.right - rect.left
            val h = rect.bottom - rect.top
            val newLeft = (rect.left + dx).coerceIn(0f, 1f - w)
            val newTop  = (rect.top  + dy).coerceIn(0f, 1f - h)
            Rect(newLeft, newTop, newLeft + w, newTop + h)
        }
        null -> rect
    }
}

/**
 * Rotate an [Offset] (touch position in Box pixel space) by [degrees] around the centre
 * of [imageDrawRect]. Used to convert unrotated touch coords into the visual coordinate space.
 */
private fun rotateOffset(offset: Offset, degrees: Int, imageDrawRect: Rect): Offset {
    if (degrees == 0) return offset
    val cx = imageDrawRect.left + imageDrawRect.size.width / 2f
    val cy = imageDrawRect.top  + imageDrawRect.size.height / 2f
    val rad = Math.toRadians(degrees.toDouble())
    val cos = Math.cos(rad).toFloat()
    val sin = Math.sin(rad).toFloat()
    val dx = offset.x - cx
    val dy = offset.y - cy
    return Offset(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
}

/**
 * Rotate a drag delta vector by [degrees].
 * Used to convert unrotated drag deltas into the visual coordinate space.
 */
private fun rotateDelta(delta: Offset, degrees: Int): Offset {
    if (degrees == 0) return delta
    val rad = Math.toRadians(degrees.toDouble())
    val cos = Math.cos(rad).toFloat()
    val sin = Math.sin(rad).toFloat()
    return Offset(delta.x * cos - delta.y * sin, delta.x * sin + delta.y * cos)
}
