package com.example.skin_tracker.di

import android.content.Context
import com.example.skin_tracker.data.db.AppDatabase
import com.example.skin_tracker.data.repo.ComparisonRepository
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.data.storage.PhotoFileStore

class AppContainer(val context: Context) {

    private val database by lazy { AppDatabase.getInstance(context) }

    val photoFileStore by lazy { PhotoFileStore(context) }

    val photoRepository by lazy { PhotoRepository(database.photoDao()) }

    val comparisonRepository by lazy { ComparisonRepository(database.comparisonDao()) }
}
