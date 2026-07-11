package com.example.skin_tracker.data.backup

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists backup settings: the user-chosen backup folder URI (from the SAF folder picker),
 * whether automatic backup is enabled, and the timestamp of the last successful backup.
 *
 * The chosen folder should live OUTSIDE the app sandbox (e.g. a cloud-synced folder, the
 * Documents folder, or an SD card) so that the backup survives a complete uninstall of the app.
 */
class BackupPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** User-chosen backup directory tree URI (from SAF folder picker). Empty = not configured. */
    var backupDirUri: String
        get() = prefs.getString(KEY_BACKUP_DIR_URI, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_BACKUP_DIR_URI, value).apply()
            refresh()
        }

    /** Whether automatic backups run after data changes / on app start. Defaults to true. */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()
            refresh()
        }

    /** Epoch millis of the last successful backup, or 0 if never. */
    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_BACKUP_AT, value).apply()
            refresh()
        }

    /** Number of photos included in the last successful backup. */
    var lastBackupPhotoCount: Int
        get() = prefs.getInt(KEY_LAST_BACKUP_PHOTOS, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_BACKUP_PHOTOS, value).apply()
            refresh()
        }

    val isConfigured: Boolean get() = backupDirUri.isNotBlank()

    // ── Snapshot for reactive UI ────────────────────────────────────────────

    private val _snapshot = MutableStateFlow(buildSnapshot())
    val snapshot: StateFlow<BackupPrefsSnapshot> = _snapshot.asStateFlow()

    fun refresh() {
        _snapshot.value = buildSnapshot()
    }

    private fun buildSnapshot() = BackupPrefsSnapshot(
        backupDirUri = backupDirUri,
        autoBackupEnabled = autoBackupEnabled,
        lastBackupAt = lastBackupAt,
        lastBackupPhotoCount = lastBackupPhotoCount
    )

    companion object {
        private const val PREFS_NAME = "skin_tracker_backup_prefs"
        private const val KEY_BACKUP_DIR_URI = "backup_dir_uri"
        private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val KEY_LAST_BACKUP_PHOTOS = "last_backup_photo_count"
    }
}

data class BackupPrefsSnapshot(
    val backupDirUri: String = "",
    val autoBackupEnabled: Boolean = true,
    val lastBackupAt: Long = 0L,
    val lastBackupPhotoCount: Int = 0
)
