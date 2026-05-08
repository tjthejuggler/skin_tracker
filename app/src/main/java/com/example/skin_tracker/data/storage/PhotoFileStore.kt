package com.example.skin_tracker.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

class PhotoFileStore(private val context: Context) {

    private val photosDir = File(context.filesDir, "photos").also { it.mkdirs() }

    companion object {
        private const val MAX_DIMENSION = 2048
        private const val JPEG_QUALITY = 85
    }

    /**
     * Load a bitmap from an absolute file path.
     * Returns null if the file doesn't exist or can't be decoded.
     */
    fun loadBitmap(absolutePath: String): Bitmap? {
        val file = File(absolutePath)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(absolutePath)
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
     * Normalises EXIF orientation so the stored file always has rotation=0.
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

        // Read EXIF orientation before decoding
        val exifRotation = getExifRotation(file.absolutePath)

        // Decode, apply EXIF rotation, and downscale
        var bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file.absolutePath
        if (exifRotation != 0) {
            val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val scaled = downscale(bitmap)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        return file.absolutePath
    }

    /**
     * Crop an image file to the specified region and overwrite it.
     *
     * IMPORTANT: [left], [top], [right], [bottom] are in ORIGINAL (un-rotated) bitmap pixel
     * coordinates. The crop is applied first, then [rotationDegrees] (multiple of 90) is
     * applied to the cropped result. This ordering avoids any rotation-direction ambiguity
     * when computing pixel coordinates upstream.
     *
     * Disables BitmapFactory density scaling so bitmap.width/height match the file on disk.
     */
    fun crop(
        path: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        rotationDegrees: Int = 0
    ): Boolean {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return false

        // 1. Crop in original bitmap coordinates
        val safeLeft = left.coerceIn(0, bitmap.width - 1)
        val safeTop = top.coerceIn(0, bitmap.height - 1)
        val w = (right - safeLeft).coerceIn(1, bitmap.width - safeLeft)
        val h = (bottom - safeTop).coerceIn(1, bitmap.height - safeTop)
        var cropped = Bitmap.createBitmap(bitmap, safeLeft, safeTop, w, h)

        // 2. Apply rotation AFTER cropping
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            cropped = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        }

        FileOutputStream(path).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return true
    }

    /**
     * Rotate an image file by [degrees] (multiples of 90) and overwrite it.
     */
    fun rotate(path: String, degrees: Int): Boolean {
        if (degrees % 90 != 0) return false
        val bitmap = BitmapFactory.decodeFile(path) ?: return false
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        FileOutputStream(path).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return true
    }

    /**
     * Delete the file at the given path.
     */
    fun delete(path: String): Boolean = File(path).delete()

    /**
     * Read the EXIF rotation angle from a JPEG file.
     * Returns 0, 90, 180, or 270.
     */
    fun getExifRotation(path: String): Int {
        return try {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

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
