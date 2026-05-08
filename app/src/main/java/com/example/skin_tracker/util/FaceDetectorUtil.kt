package com.example.skin_tracker.util

import android.graphics.Bitmap

/**
 * Lightweight face detection using the built-in [android.media.FaceDetector].
 * Zero dependencies — the algorithm ships with Android since API 1.
 * Sufficient for a simple "face vs body" category decision.
 */
object FaceDetectorUtil {

    /**
     * Returns true if at least one face is found in the given bitmap.
     *
     * The detector requires RGB_565 format, so we convert if needed.
     * We also downscale large bitmaps to keep detection fast.
     */
    fun hasFace(bitmap: Bitmap): Boolean {
        // Downscale to speed up detection (max 400px on the longest side)
        val scaled = downscale(bitmap, maxDimension = 400)

        // FaceDetector requires RGB_565
        val rgb565 = if (scaled.config == Bitmap.Config.RGB_565) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.RGB_565, false) ?: run {
                if (scaled !== bitmap) scaled.recycle()
                return false
            }
        }

        // Clean up intermediate scaled bitmap if it's not the same as rgb565
        if (scaled !== bitmap && scaled !== rgb565) scaled.recycle()

        val detector = android.media.FaceDetector(rgb565.width, rgb565.height, 1)
        val faces = arrayOfNulls<android.media.FaceDetector.Face>(1)
        val count = detector.findFaces(rgb565, faces)

        if (rgb565 !== bitmap) rgb565.recycle()

        return count > 0
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
