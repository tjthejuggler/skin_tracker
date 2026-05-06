package com.example.skin_tracker.ui.compare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

/**
 * A composable that shows an image with a draggable crop rectangle overlay.
 * Returns the crop rectangle in image pixel coordinates when confirmed.
 */
@Composable
fun CropOverlay(
    imagePath: String,
    onConfirm: (cropLeft: Int, cropTop: Int, cropRight: Int, cropBottom: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var displaySize by remember { mutableStateOf(IntSize.Zero) }

    // Decode image bounds
    val options = remember(imagePath) {
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, this)
        }
    }
    val imgW = options.outWidth.toFloat()
    val imgH = options.outHeight.toFloat()

    // Crop rect in display-fraction coordinates (0..1)
    var cropRect by remember { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }
    var dragCorner by remember { mutableStateOf<DragCorner?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Crop target",
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        displaySize = coords.size
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragCorner = nearestCorner(offset, cropRect, displaySize)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / displaySize.width
                                val dy = dragAmount.y / displaySize.height
                                cropRect = moveCorner(cropRect, dragCorner, dx, dy)
                            },
                            onDragEnd = {
                                dragCorner = null
                            },
                            onDragCancel = {
                                dragCorner = null
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )

            // Dark overlay with cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (displaySize != IntSize.Zero && imgW > 0f && imgH > 0f) {
                    // Calculate the Fit scaling
                    val scale = minOf(size.width / imgW, size.height / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val offsetX = (size.width - drawW) / 2f
                    val offsetY = (size.height - drawH) / 2f

                    val left = offsetX + cropRect.left * drawW
                    val top = offsetY + cropRect.top * drawH
                    val right = offsetX + cropRect.right * drawW
                    val bottom = offsetY + cropRect.bottom * drawH

                    // Semi-transparent overlay with cutout
                    val overlayColor = Color.Black.copy(alpha = 0.6f)
                    val cutoutPath = Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(left, top, right, bottom))
                    }
                    val fullPath = Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    }
                    drawPath(fullPath, overlayColor)
                    drawPath(cutoutPath, Color.Transparent, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)

                    // Crop border
                    drawRect(
                        Color.White,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 3f)
                    )

                    // Corner handles
                    val handleSize = 20f
                    val corners = listOf(
                        Offset(left, top),
                        Offset(right, top),
                        Offset(left, bottom),
                        Offset(right, bottom)
                    )
                    corners.forEach { corner ->
                        drawRect(
                            Color.White,
                            topLeft = Offset(corner.x - handleSize / 2, corner.y - handleSize / 2),
                            size = androidx.compose.ui.geometry.Size(handleSize, handleSize)
                        )
                    }
                }
            }
        }

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            FilledTonalButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(16.dp))
            FilledTonalButton(
                onClick = {
                    if (imgW > 0f && imgH > 0f) {
                        val cropLeft = (cropRect.left * imgW).toInt().coerceAtLeast(0)
                        val cropTop = (cropRect.top * imgH).toInt().coerceAtLeast(0)
                        val cropRight = (cropRect.right * imgW).toInt().coerceAtMost(imgW.toInt())
                        val cropBottom = (cropRect.bottom * imgH).toInt().coerceAtMost(imgH.toInt())
                        onConfirm(cropLeft, cropTop, cropRight, cropBottom)
                    }
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm crop")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crop")
            }
        }
    }
}

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

private fun nearestCorner(offset: Offset, cropRect: Rect, displaySize: IntSize): DragCorner {
    val threshold = 40f
    val tl = Offset(cropRect.left * displaySize.width, cropRect.top * displaySize.height)
    val tr = Offset(cropRect.right * displaySize.width, cropRect.top * displaySize.height)
    val bl = Offset(cropRect.left * displaySize.width, cropRect.bottom * displaySize.height)
    val br = Offset(cropRect.right * displaySize.width, cropRect.bottom * displaySize.height)

    val distances = mapOf(
        DragCorner.TOP_LEFT to (offset - tl).getDistance(),
        DragCorner.TOP_RIGHT to (offset - tr).getDistance(),
        DragCorner.BOTTOM_LEFT to (offset - bl).getDistance(),
        DragCorner.BOTTOM_RIGHT to (offset - br).getDistance()
    )

    val nearest = distances.minByOrNull { it.value }
    return if (nearest != null && nearest.value < threshold) {
        nearest.key
    } else {
        DragCorner.CENTER
    }
}

private fun moveCorner(rect: Rect, corner: DragCorner?, dx: Float, dy: Float): Rect {
    val minSize = 0.05f
    return when (corner) {
        DragCorner.TOP_LEFT -> Rect(
            (rect.left + dx).coerceIn(0f, rect.right - minSize),
            (rect.top + dy).coerceIn(0f, rect.bottom - minSize),
            rect.right,
            rect.bottom
        )
        DragCorner.TOP_RIGHT -> Rect(
            rect.left,
            (rect.top + dy).coerceIn(0f, rect.bottom - minSize),
            (rect.right + dx).coerceIn(rect.left + minSize, 1f),
            rect.bottom
        )
        DragCorner.BOTTOM_LEFT -> Rect(
            (rect.left + dx).coerceIn(0f, rect.right - minSize),
            rect.top,
            rect.right,
            (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
        )
        DragCorner.BOTTOM_RIGHT -> Rect(
            rect.left,
            rect.top,
            (rect.right + dx).coerceIn(rect.left + minSize, 1f),
            (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
        )
        DragCorner.CENTER -> Rect(
            (rect.left + dx).coerceIn(0f, 1f - (rect.right - rect.left)),
            (rect.top + dy).coerceIn(0f, 1f - (rect.bottom - rect.top)),
            (rect.right + dx).coerceIn(rect.right - rect.left, 1f),
            (rect.bottom + dy).coerceIn(rect.bottom - rect.top, 1f)
        )
        null -> rect
    }
}
