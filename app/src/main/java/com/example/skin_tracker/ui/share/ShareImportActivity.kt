package com.example.skin_tracker.ui.share

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import com.example.skin_tracker.util.ExifDateReader
import com.example.skin_tracker.util.FaceDetectorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible share-target: lets the user share a photo from the camera app (or
 * anywhere else) straight into Skin Tracker.
 *
 * The activity is fully translucent and draws no UI. In the background it runs
 * the exact same pipeline as in-app capture:
 *   1. Copy the shared image into app-private storage (EXIF-rotation normalised)
 *   2. Auto-detect face vs body via [FaceDetectorUtil]
 *   3. Insert the photo with its original capture date ([ExifDateReader])
 *   4. Fire the Tail integration ([com.example.skin_tracker.data.ipc.HabitIntegrationRepository.notifyPhotoAdded])
 *      and trigger the debounced auto-backup
 *   5. Flash a toast telling the user whether it was filed as FACE or BODY
 *
 * The app never visibly opens — the toast is the only feedback.
 */
class ShareImportActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No setContentView — the translucent theme keeps the camera app visible
        // underneath while we process in the background.

        val uri = extractSharedImageUri()
        if (uri == null) {
            Log.w(TAG, "No image in share intent — ignoring")
            finish()
            return
        }

        val app = application as SkinTrackerApp
        val container = app.container

        scope.launch {
            val message = try {
                importPhoto(container, app, uri)
            } catch (e: Exception) {
                Log.e(TAG, "Shared photo import failed", e)
                "Skin Tracker: couldn't import photo"
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun importPhoto(
        container: com.example.skin_tracker.di.AppContainer,
        app: SkinTrackerApp,
        uri: Uri
    ): String {
        // Read the original capture date before the source URI can go stale
        val capturedAt = ExifDateReader.readCapturedAt(this, uri)

        // Copy into app-private storage (also normalises EXIF orientation)
        val path = container.photoFileStore.copyFromUri(uri)

        // Face vs body detection — same rule as in-app capture
        val bitmap = container.photoFileStore.loadBitmap(path)
        val category = if (bitmap != null && FaceDetectorUtil.hasFace(bitmap)) {
            Category.FACE
        } else {
            Category.BODY
        }

        val now = System.currentTimeMillis()
        container.photoRepository.insert(
            Photo(
                uri = path,
                category = category,
                capturedAt = capturedAt,
                importedAt = now
            )
        )

        // Tail integration + auto-backup, identical to in-app capture
        container.habitIntegrationRepository.notifyPhotoAdded()
        app.requestAutoBackup()

        Log.i(TAG, "Imported shared photo as ${category.name} (capturedAt=$capturedAt)")
        return if (category == Category.FACE) {
            "Skin Tracker: saved as FACE photo"
        } else {
            "Skin Tracker: saved as BODY photo"
        }
    }

    private fun extractSharedImageUri(): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null

        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShareImportActivity"
    }
}
