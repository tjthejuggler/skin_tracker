package com.example.skin_tracker.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExifDateReader {

    private val exifDateFormats = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )

    /**
     * Read the 'created' date from a photo URI.
     * Priority: EXIF DateTimeOriginal → EXIF DateTime → file lastModified → now.
     */
    fun readCapturedAt(context: Context, uri: Uri): Long {
        // Try EXIF first
        context.contentResolver.openInputStream(uri)?.use { input ->
            val date = readExifDate(input)
            if (date != null) return date.time
        }

        // Fallback: MediaStore date_taken or date_modified (stored in seconds, convert to ms)
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val dateTakenIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    if (dateTakenIndex >= 0) {
                        val dateTaken = it.getLong(dateTakenIndex)
                        if (dateTaken > 0) return dateTaken // DATE_TAKEN is already in ms
                    }
                    val dateModifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (dateModifiedIndex >= 0) {
                        val dateModified = it.getLong(dateModifiedIndex)
                        if (dateModified > 0) return dateModified * 1000L // DATE_MODIFIED is in seconds
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Final fallback: now
        return System.currentTimeMillis()
    }

    /**
     * Read the 'created' date from a file path.
     */
    fun readCapturedAtFromFile(filePath: String): Long {
        try {
            val exif = ExifInterface(filePath)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (dateStr != null) {
                val date = parseExifDate(dateStr)
                if (date != null) return date.time
            }
        } catch (_: Exception) {
        }

        // Fallback: file lastModified
        val file = java.io.File(filePath)
        return if (file.exists()) file.lastModified() else System.currentTimeMillis()
    }

    private fun readExifDate(input: InputStream): Date? {
        return try {
            val exif = ExifInterface(input)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (dateStr != null) parseExifDate(dateStr) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseExifDate(dateStr: String): Date? {
        for (format in exifDateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                return sdf.parse(dateStr)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
