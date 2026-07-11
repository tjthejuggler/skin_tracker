package com.example.skin_tracker.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.skin_tracker.data.entity.ComparisonEntity
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "BackupManager"

/**
 * Automatic, uninstall-proof backup system.
 *
 * ## Why this design
 * All app data normally lives in app-private storage:
 *  - images:   [Context.getFilesDir]/photos/ (one .jpg per photo)
 *  - metadata: the Room database "skin_tracker_db" (photos + comparisons tables)
 *
 * ALL of that is wiped when the app is uninstalled. To survive a complete deletion of the app,
 * the backup must be written to a location OUTSIDE the app sandbox. We use the Storage Access
 * Framework (SAF): the user picks a folder once (ideally a cloud-synced folder such as Google
 * Drive / Nextcloud, or an SD card). We hold a persistable URI permission to it, and mirror the
 * data there.
 *
 * ## Backup layout (inside the chosen folder)
 * ```
 * SkinTrackerBackup
 *   backup.json          full metadata manifest (photos + comparisons)
 *   images.zip           compressed archive containing all images
 * ```
 *
 * ## Backup behavior
 * [backup] creates a new zip file containing all images each time (replacing any existing zip).
 * The `backup.json` manifest is always rewritten in full (it is tiny).
 *
 * ## Restore
 * [restore] reads `backup.json`, copies every referenced image back into the app's photos dir,
 * rewrites the URI to point at the restored local file, and repopulates the Room tables. This lets
 * a freshly reinstalled app fully recover months of images and rating data.
 */
class BackupManager(
    private val context: Context,
    private val photoRepository: PhotoRepository,
    private val comparisonRepository: ComparisonRepository,
    private val backupPrefs: BackupPreferences
) {
    /** Serializes backup/restore so overlapping auto-triggers can't corrupt the folder. */
    private val mutex = Mutex()

    private val photosDir: File by lazy { File(context.filesDir, "photos").also { it.mkdirs() } }

    sealed class Result {
        data class Success(val photoCount: Int, val imagesCopied: Int) : Result()
        data class Skipped(val reason: String) : Result()
        data class Failure(val message: String) : Result()
    }

    sealed class RestoreResult {
        data class Success(val photoCount: Int, val imagesRestored: Int, val comparisonCount: Int) : RestoreResult()
        data class Failure(val message: String) : RestoreResult()
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    /**
     * Run an incremental backup to the configured SAF folder.
     * Safe to call frequently; returns [Result.Skipped] when auto-backup is disabled or
     * no folder is configured (unless [force] is set, which ignores the auto-backup toggle).
     */
    suspend fun backup(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dirUriString = backupPrefs.backupDirUri
            if (dirUriString.isBlank()) return@withLock Result.Skipped("No backup folder configured")
            if (!force && !backupPrefs.autoBackupEnabled) return@withLock Result.Skipped("Auto-backup disabled")

            try {
                val root = resolveBackupRoot(dirUriString)
                    ?: return@withLock Result.Failure("Cannot access backup folder (permission lost?)")

                val photos = photoRepository.getAllSync()
                val comparisons = comparisonRepository.getAllSync()

                // Create a zip file containing all images
                val zipFile = root.findFile(IMAGES_ZIP)?.takeIf { it.isFile }
                if (zipFile != null) {
                    zipFile.delete()
                }
                val newZipFile = root.createFile("application/zip", IMAGES_ZIP)
                    ?: return@withLock Result.Failure("Cannot create images zip file")

                var imagesAdded = 0
                context.contentResolver.openOutputStream(newZipFile.uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        for (photo in photos) {
                            val srcFile = File(photo.uri)
                            if (!srcFile.exists()) continue
                            val entryName = srcFile.name
                            try {
                                FileInputStream(srcFile).use { fis ->
                                    val entry = ZipEntry(entryName)
                                    zipOut.putNextEntry(entry)
                                    fis.copyTo(zipOut)
                                    zipOut.closeEntry()
                                    imagesAdded++
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to add ${srcFile.name} to zip", e)
                            }
                        }
                    }
                } ?: return@withLock Result.Failure("Cannot write to zip file")

                // Always rewrite the (small) manifest.
                val manifest = buildManifest(photos, comparisons)
                writeManifest(root, manifest)

                backupPrefs.lastBackupAt = System.currentTimeMillis()
                backupPrefs.lastBackupPhotoCount = photos.size

                Log.i(TAG, "Backup complete: ${photos.size} photos, $imagesAdded images in zip")
                Result.Success(photoCount = photos.size, imagesCopied = imagesAdded)
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                Result.Failure(e.message ?: "Unknown error")
            }
        }
    }

    // ── Restore ─────────────────────────────────────────────────────────────

    /**
     * Restore from the configured SAF folder. This REPLACES all current photos and comparisons
     * with the contents of the backup. Images are copied back into app-private storage.
     */
    suspend fun restore(): RestoreResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dirUriString = backupPrefs.backupDirUri
            if (dirUriString.isBlank()) return@withLock RestoreResult.Failure("No backup folder configured")

            try {
                val root = resolveBackupRoot(dirUriString)
                    ?: return@withLock RestoreResult.Failure("Cannot access backup folder")

                val manifestFile = root.findFile(MANIFEST_NAME)
                    ?: return@withLock RestoreResult.Failure("No backup.json found in folder")
                val json = context.contentResolver.openInputStream(manifestFile.uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: return@withLock RestoreResult.Failure("Cannot read backup.json")

                val (photos, comparisons) = parseManifest(json)

                val zipFile = root.findFile(IMAGES_ZIP)?.takeIf { it.isFile }
                    ?: return@withLock RestoreResult.Failure("No images.zip found in folder")

                // Extract images from zip file and copy them back into app-private storage.
                var restoredImages = 0
                val restoredFiles = mutableMapOf<String, File>()
                context.contentResolver.openInputStream(zipFile.uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val destFile = File(photosDir, entry.name)
                                try {
                                    FileOutputStream(destFile).use { fos ->
                                        zipIn.copyTo(fos)
                                    }
                                    restoredFiles[entry.name] = destFile
                                    restoredImages++
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to extract ${entry.name}", e)
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                } ?: return@withLock RestoreResult.Failure("Cannot read zip file")

                // Remap the photo URIs to the local files.
                val remapped = photos.map { photo ->
                    val fileName = File(photo.uri).name
                    val localFile = restoredFiles[fileName]
                    if (localFile != null) {
                        photo.copy(uri = localFile.absolutePath)
                    } else {
                        Log.w(TAG, "Image not found in zip: $fileName")
                        photo.copy(uri = "") // Mark as missing
                    }
                }

                // Rewrite the database.
                comparisonRepository.clearAll()
                photoRepository.clearAll()
                for (photo in remapped) {
                    if (photo.uri.isNotEmpty()) {
                        photoRepository.upsertWithId(photo)
                    }
                }
                for (comparison in comparisons) comparisonRepository.upsertWithId(comparison)

                Log.i(TAG, "Restore complete: ${remapped.size} photos, $restoredImages images restored, ${comparisons.size} comparisons")
                RestoreResult.Success(
                    photoCount = remapped.size,
                    imagesRestored = restoredImages,
                    comparisonCount = comparisons.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                RestoreResult.Failure(e.message ?: "Unknown error")
            }
        }
    }

    // ── SAF helpers ───────────────────────────────────────────────────────────

    /** Resolve (or create) the SkinTrackerBackup folder inside the user-chosen tree. */
    private fun resolveBackupRoot(dirUriString: String): DocumentFile? {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(dirUriString)) ?: return null
        if (!tree.exists() || !tree.canWrite()) return null
        val existing = tree.findFile(BACKUP_DIR)?.takeIf { it.isDirectory }
        return existing ?: tree.createDirectory(BACKUP_DIR)
    }


    private fun writeManifest(root: DocumentFile, json: String) {
        root.findFile(MANIFEST_NAME)?.delete()
        val file = root.createFile("application/json", MANIFEST_NAME)
            ?: throw IllegalStateException("Cannot create backup.json")
        context.contentResolver.openOutputStream(file.uri)?.use { os ->
            os.write(json.toByteArray(Charsets.UTF_8))
            os.flush()
        } ?: throw IllegalStateException("Cannot open backup.json for writing")
    }

    // ── Manifest (de)serialization ──────────────────────────────────────────

    private fun buildManifest(photos: List<Photo>, comparisons: List<ComparisonEntity>): String {
        val photosArr = JSONArray()
        photos.forEach { p ->
            photosArr.put(JSONObject().apply {
                put("id", p.id)
                // Store only the file name; on restore it is re-rooted at the local photos dir.
                put("fileName", File(p.uri).name)
                put("category", p.category.name)
                put("capturedAt", p.capturedAt)
                put("importedAt", p.importedAt)
                put("rating", p.rating)
                put("comparisonCount", p.comparisonCount)
                put("wins", p.wins)
                put("losses", p.losses)
                put("deleted", p.deleted)
            })
        }
        val comparisonsArr = JSONArray()
        comparisons.forEach { c ->
            comparisonsArr.put(JSONObject().apply {
                put("id", c.id)
                put("winnerPhotoId", c.winnerPhotoId)
                put("loserPhotoId", c.loserPhotoId)
                put("category", c.category)
                put("comparedAt", c.comparedAt)
                put("winnerRatingBefore", c.winnerRatingBefore)
                put("loserRatingBefore", c.loserRatingBefore)
                put("winnerRatingAfter", c.winnerRatingAfter)
                put("loserRatingAfter", c.loserRatingAfter)
            })
        }
        return JSONObject().apply {
            put("version", MANIFEST_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("photos", photosArr)
            put("comparisons", comparisonsArr)
        }.toString(2)
    }

    private fun parseManifest(json: String): Pair<List<Photo>, List<ComparisonEntity>> {
        val root = JSONObject(json)

        val photos = mutableListOf<Photo>()
        val photosArr = root.optJSONArray("photos") ?: JSONArray()
        for (i in 0 until photosArr.length()) {
            val o = photosArr.getJSONObject(i)
            photos.add(
                Photo(
                    id = o.getLong("id"),
                    // uri is a bare filename here; BackupManager.restore() re-roots it.
                    uri = o.getString("fileName"),
                    category = runCatching { Category.valueOf(o.getString("category")) }
                        .getOrDefault(Category.FACE),
                    capturedAt = o.getLong("capturedAt"),
                    importedAt = o.getLong("importedAt"),
                    rating = o.optDouble("rating", 1500.0),
                    comparisonCount = o.optInt("comparisonCount", 0),
                    wins = o.optInt("wins", 0),
                    losses = o.optInt("losses", 0),
                    deleted = o.optBoolean("deleted", false)
                )
            )
        }

        val comparisons = mutableListOf<ComparisonEntity>()
        val comparisonsArr = root.optJSONArray("comparisons") ?: JSONArray()
        for (i in 0 until comparisonsArr.length()) {
            val o = comparisonsArr.getJSONObject(i)
            comparisons.add(
                ComparisonEntity(
                    id = o.getLong("id"),
                    winnerPhotoId = o.getLong("winnerPhotoId"),
                    loserPhotoId = o.getLong("loserPhotoId"),
                    category = o.getString("category"),
                    comparedAt = o.getLong("comparedAt"),
                    winnerRatingBefore = o.getDouble("winnerRatingBefore"),
                    loserRatingBefore = o.getDouble("loserRatingBefore"),
                    winnerRatingAfter = o.getDouble("winnerRatingAfter"),
                    loserRatingAfter = o.getDouble("loserRatingAfter")
                )
            )
        }
        return photos to comparisons
    }

    companion object {
        private const val BACKUP_DIR = "SkinTrackerBackup"
        private const val IMAGES_ZIP = "images.zip"
        private const val MANIFEST_NAME = "backup.json"
        private const val MANIFEST_VERSION = 1
    }
}
