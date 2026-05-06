package com.example.skin_tracker.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class PhotoFileStore(private val context: Context) {

    private val photosDir = File(context.filesDir, "photos").also { it.mkdirs() }

    companion object {
        private const val MAX_DIMENSION = 2048
        private const val JPEG_QUALITY = 85
    }

    /**
     * Save a bitmap from the camera to app-private storage.
     * @return absolute file path
     */
    fun saveBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val scaled = downscale(rotated)
        val filename = "${System.currentTimeMillis()}.jpg"
        val file = File(photosDir, filename)

        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        return file.absolutePath
    }

    /**
     * Copy a content URI (from gallery picker) to app-private storage.
     * @return absolute file path
     */
    fun copyFromUri(uri: Uri): String {
        val filename = "${System.currentTimeMillis()}.jpg"
        val file = File(photosDir, filename)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        // Downscale the saved file
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file.absolutePath
        val scaled = downscale(bitmap)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        return file.absolutePath
    }

    /**
     * Crop an image file to the specified region and overwrite it.
     * @param path absolute file path
     * @param left crop left in pixels
     * @param top crop top in pixels
     * @param right crop right in pixels
     * @param bottom crop bottom in pixels
     */
    fun crop(path: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val bitmap = BitmapFactory.decodeFile(path) ?: return false
        val w = (right - left).coerceIn(1, bitmap.width - left)
        val h = (bottom - top).coerceIn(1, bitmap.height - top)
        val cropped = Bitmap.createBitmap(bitmap, left.coerceAtLeast(0), top.coerceAtLeast(0), w, h)
        FileOutputStream(path).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return true
    }

    /**
     * Delete the file at the given path.
     */
    fun delete(path: String): Boolean = File(path).delete()

    private fun downscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return bitmap

        val scale = minOf(
            MAX_DIMENSION.toFloat() / width,
            MAX_DIMENSION.toFloat() / height
        )
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
