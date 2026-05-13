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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/**
 * Full-screen crop editor.
 *
 * Coordinate plan
 * ───────────────
 * • imgW × imgH    — actual pixel dimensions of the file on disk (no EXIF, downscaled).
 * • visW × visH    — visual dimensions after rotation: swapped for 90°/270°.
 * • imageDrawRect  — the on-screen rect where the rotated image is drawn (visual dims fitted
 *                    into the Box with ContentScale.Fit-like letterboxing).
 * • cropRect       — normalised (0..1) rectangle relative to imageDrawRect; drawn in screen
 *                    space (the crop frame stays upright while the image rotates beneath).
 *
 * Rendering trick
 * ───────────────
 * To make the rotated image fill imageDrawRect exactly:
 *   1. Layout an AsyncImage container at size (imgW, imgH) aspect, *transposed* for 90°/270°
 *      so that its size BEFORE rotation is the rect that AFTER rotation becomes imageDrawRect.
 *      For 0°/180° this is just imageDrawRect.size; for 90°/270° it is (height, width) of
 *      imageDrawRect.
 *   2. Position the container so its centre matches imageDrawRect's centre.
 *   3. Apply graphicsLayer { rotationZ } — rotates around the container's centre, producing
 *      an image whose visible bounding box is exactly imageDrawRect.
 *
 * Apply
 * ─────
 * cropRect (normalised against imageDrawRect, which uses visual dims) maps directly to
 * rotated-image pixel coords by multiplying by (effW, effH) where (effW, effH) are the
 * rotated-image's pixel dimensions. PhotoFileStore.crop() rotates the bitmap first, then
 * crops at exactly those pixel coords.
 *
 * IMPORTANT
 * ─────────
 * PhotoFileStore.crop() uses BitmapFactory.Options(inScaled=false) when decoding so that
 * bitmap.width × bitmap.height match the file's actual dimensions (which is what
 * inJustDecodeBounds reports here). Without this, display-density rescaling makes crop
 * pixel coords land on the wrong region.
 */
@Composable
fun CropOverlay(
    imagePath: String,
    onConfirm: (cropLeft: Int, cropTop: Int, cropRight: Int, cropBottom: Int, rotationDegrees: Int) -> Unit,
    onCancel: () -> Unit
) {
    val options = remember(imagePath) {
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, this)
        }
    }
    val imgW = options.outWidth.toFloat()
    val imgH = options.outHeight.toFloat()

    var rotationDegrees by remember { mutableIntStateOf(0) }

    var cropRect by remember { mutableStateOf(Rect(0.15f, 0.15f, 0.85f, 0.85f)) }
    var dragCorner by remember { mutableStateOf<DragCorner?>(null) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val imageDrawRect = remember(boxSize, imgW, imgH, rotationDegrees) {
        if (boxSize == IntSize.Zero || imgW <= 0f || imgH <= 0f) {
            Rect(Offset.Zero, Size.Zero)
        } else {
            val (visW, visH) = if (rotationDegrees % 180 == 0) imgW to imgH else imgH to imgW
            val scale = minOf(boxSize.width.toFloat() / visW, boxSize.height.toFloat() / visH)
            val drawW = visW * scale
            val drawH = visH * scale
            val ox = (boxSize.width - drawW) / 2f
            val oy = (boxSize.height - drawH) / 2f
            Rect(Offset(ox, oy), Size(drawW, drawH))
        }
    }

    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Image + overlay ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { boxSize = it.size }
                .pointerInput(imageDrawRect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragCorner = nearestCorner(offset, cropRect, imageDrawRect)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val drawW = imageDrawRect.size.width
                            val drawH = imageDrawRect.size.height
                            if (drawW > 0f && drawH > 0f) {
                                val dx = dragAmount.x / drawW
                                val dy = dragAmount.y / drawH
                                cropRect = moveCorner(cropRect, dragCorner, dx, dy)
                            }
                        },
                        onDragEnd = { dragCorner = null },
                        onDragCancel = { dragCorner = null }
                    )
                }
        ) {
            // ── Image container, sized so rotation bounding box == imageDrawRect ──
            if (imageDrawRect.size != Size.Zero) {
                // Container size BEFORE rotation:
                //   0°/180° → same as imageDrawRect (visW, visH)
                //   90°/270° → transposed (visH, visW) so that after rotation the bounding box
                //             equals imageDrawRect.
                val isQuarter = rotationDegrees % 180 != 0
                val containerW = if (isQuarter) imageDrawRect.size.height else imageDrawRect.size.width
                val containerH = if (isQuarter) imageDrawRect.size.width else imageDrawRect.size.height
                val centerX = imageDrawRect.left + imageDrawRect.size.width / 2f
                val centerY = imageDrawRect.top + imageDrawRect.size.height / 2f
                val offsetX = (centerX - containerW / 2f).roundToInt()
                val offsetY = (centerY - containerH / 2f).roundToInt()

                AsyncImage(
                    model = File(imagePath),
                    contentDescription = "Crop target",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .size(
                            width = with(density) { containerW.toDp() },
                            height = with(density) { containerH.toDp() }
                        )
                        .graphicsLayer { rotationZ = rotationDegrees.toFloat() }
                )
            }

            // ── Crop overlay — drawn in screen space, no rotation ──
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                if (imageDrawRect.size == Size.Zero) return@Canvas

                val left   = imageDrawRect.left + cropRect.left   * imageDrawRect.size.width
                val top    = imageDrawRect.top  + cropRect.top    * imageDrawRect.size.height
                val right  = imageDrawRect.left + cropRect.right  * imageDrawRect.size.width
                val bottom = imageDrawRect.top  + cropRect.bottom * imageDrawRect.size.height

                drawRect(Color.Black.copy(alpha = 0.55f))

                drawPath(
                    Path().apply { addRect(Rect(left, top, right, bottom)) },
                    color = Color.Transparent,
                    blendMode = BlendMode.Clear
                )

                drawRect(
                    Color.White,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 3f)
                )

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90°")
                }
                Text("Rotate", style = MaterialTheme.typography.labelSmall)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {
                        if (imgW > 0f && imgH > 0f) {
                            // cropRect is in normalised (0..1) coords relative to the visual
                            // (post-rotation) image. PhotoFileStore.crop() now expects pixel
                            // coords in the ORIGINAL (un-rotated) bitmap, then it rotates the
                            // cropped result. So we must transform visual-normalised → original-px.
                            val (oL, oT, oR, oB) = visualToOriginalCrop(
                                cropRect, imgW, imgH, rotationDegrees
                            )
                            val cropLeft   = oL.toInt().coerceAtLeast(0)
                            val cropTop    = oT.toInt().coerceAtLeast(0)
                            val cropRight  = oR.toInt().coerceAtMost(imgW.toInt())
                            val cropBottom = oB.toInt().coerceAtMost(imgH.toInt())
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

// ── Coordinate helpers ───────────────────────────────────────────────────────

/**
 * Convert a normalised (0..1) crop rect from the VISUAL (post-rotation) image space to
 * pixel coordinates in the ORIGINAL (un-rotated) bitmap.
 *
 * Visual axes after rotation:
 *   • 0°/180° — visW × visH = imgW × imgH (same orientation, possibly flipped)
 *   • 90°/270° — visW × visH = imgH × imgW (transposed)
 *
 * Mapping derived for clockwise rotation (matches both Compose graphicsLayer { rotationZ }
 * and Android Matrix.postRotate, which agree on direction in screen coordinates).
 *
 * Returns Quadruple-as-FloatArray [left, top, right, bottom] in original-bitmap pixel units.
 */
private fun visualToOriginalCrop(
    crop: Rect,
    imgW: Float,
    imgH: Float,
    rotationDegrees: Int
): FloatArray {
    val nL = crop.left
    val nT = crop.top
    val nR = crop.right
    val nB = crop.bottom
    val rot = ((rotationDegrees % 360) + 360) % 360
    return when (rot) {
        0 -> floatArrayOf(nL * imgW, nT * imgH, nR * imgW, nB * imgH)
        90 -> floatArrayOf(nT * imgW, (1f - nR) * imgH, nB * imgW, (1f - nL) * imgH)
        180 -> floatArrayOf((1f - nR) * imgW, (1f - nB) * imgH, (1f - nL) * imgW, (1f - nT) * imgH)
        270 -> floatArrayOf((1f - nB) * imgW, nL * imgH, (1f - nT) * imgW, nR * imgH)
        else -> floatArrayOf(nL * imgW, nT * imgH, nR * imgW, nB * imgH)
    }
}

private operator fun FloatArray.component1(): Float = this[0]
private operator fun FloatArray.component2(): Float = this[1]
private operator fun FloatArray.component3(): Float = this[2]
private operator fun FloatArray.component4(): Float = this[3]

// ── Drag helpers ─────────────────────────────────────────────────────────────

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

private fun nearestCorner(offset: Offset, cropRect: Rect, imageDrawRect: Rect): DragCorner {
    if (imageDrawRect.size == Size.Zero) return DragCorner.CENTER

    val tl = Offset(
        imageDrawRect.left + cropRect.left  * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.top   * imageDrawRect.size.height
    )
    val tr = Offset(
        imageDrawRect.left + cropRect.right * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.top   * imageDrawRect.size.height
    )
    val bl = Offset(
        imageDrawRect.left + cropRect.left  * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.bottom * imageDrawRect.size.height
    )
    val br = Offset(
        imageDrawRect.left + cropRect.right  * imageDrawRect.size.width,
        imageDrawRect.top  + cropRect.bottom * imageDrawRect.size.height
    )

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

private fun moveCorner(rect: Rect, corner: DragCorner?, dx: Float, dy: Float): Rect {
    val minSize = 0.05f
    return when (corner) {
        DragCorner.TOP_LEFT -> {
            val newLeft = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            val newTop = (rect.top + dy).coerceIn(0f, rect.bottom - minSize)
            Rect(newLeft, newTop, rect.right, rect.bottom)
        }
        DragCorner.TOP_RIGHT -> {
            val newRight = (rect.right + dx).coerceIn(rect.left + minSize, 1f)
            val newTop = (rect.top + dy).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, newTop, newRight, rect.bottom)
        }
        DragCorner.BOTTOM_LEFT -> {
            val newLeft = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            val newBottom = (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
            Rect(newLeft, rect.top, rect.right, newBottom)
        }
        DragCorner.BOTTOM_RIGHT -> {
            val newRight = (rect.right + dx).coerceIn(rect.left + minSize, 1f)
            val newBottom = (rect.bottom + dy).coerceIn(rect.top + minSize, 1f)
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
