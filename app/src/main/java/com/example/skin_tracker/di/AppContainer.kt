package com.example.skin_tracker.di

import android.content.Context
import com.example.skin_tracker.data.db.AppDatabase
import com.example.skin_tracker.data.debug.DebugNoteRepository
import com.example.skin_tracker.data.debug.DebugPreferences
import com.example.skin_tracker.data.ipc.HabitIntegrationRepository
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore
import com.example.skin_tracker.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(val context: Context) {

    private val database by lazy { AppDatabase.getInstance(context) }

    val photoFileStore by lazy { PhotoFileStore(context) }

    val photoRepository by lazy { PhotoRepository(database.photoDao()) }

    val comparisonRepository by lazy { ComparisonRepository(database.comparisonDao()) }

    private val habitPrefs by lazy {
        context.getSharedPreferences("habit_integration_prefs", Context.MODE_PRIVATE)
    }

    val habitIntegrationRepository by lazy {
        HabitIntegrationRepository(context, habitPrefs)
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
