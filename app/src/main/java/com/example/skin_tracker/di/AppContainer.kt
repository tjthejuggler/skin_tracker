package com.example.skin_tracker.di

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.example.skin_tracker.data.backup.BackupManager
import com.example.skin_tracker.data.backup.BackupPreferences
import com.example.skin_tracker.data.db.AppDatabase
import com.example.skin_tracker.data.debug.DebugNoteRepository
import com.example.skin_tracker.data.debug.DebugPreferences
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.NoteRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(val context: Context) {

    private val database by lazy { AppDatabase.getInstance(context) }

    val photoFileStore by lazy { PhotoFileStore(context) }

    /**
     * In-memory LRU cache for normalised face bitmaps used by the chart
     * animation. Keyed by photo file path. Sized to ~1/8 of max heap.
     */
    val faceBitmapCache: LruCache<String, Bitmap> by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = maxMemoryKb / 8
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024)
        }
    }

    val photoRepository by lazy { PhotoRepository(database.photoDao()) }

    val comparisonRepository by lazy { ComparisonRepository(database.comparisonDao()) }

    val noteRepository by lazy { NoteRepository(database.noteDao()) }

    val backupPreferences by lazy { BackupPreferences(context) }

    val backupManager by lazy {
        BackupManager(context, photoRepository, comparisonRepository, noteRepository, backupPreferences)
    }

    private val habitPrefs by lazy {
        context.getSharedPreferences("habit_integration_prefs", Context.MODE_PRIVATE)
    }

    val habitIntegrationRepository by lazy {
        HabitIntegrationRepository(context, habitPrefs, photoRepository)
    }

    val debugPreferences by lazy { DebugPreferences(context) }

    val debugNoteRepository by lazy { DebugNoteRepository(context, debugPreferences) }
    /** Shared category selection synced between Compare and Chart screens. */
    private val _sharedCategory = MutableStateFlow(Category.FACE)
    val sharedCategory: StateFlow<Category> = _sharedCategory.asStateFlow()

    fun setSharedCategory(category: Category) {
        _sharedCategory.value = category
    }
}
