package com.example.skin_tracker.data.debug

import com.example.skin_tracker.ui.Screen

/**
 * Maps a navigation route to human-readable context that helps a programmer LLM
 * find the right source files quickly. Each entry provides the screen label,
 * the primary source file, and key functions/composables in that file.
 */
object ScreenContextMapper {

    data class ScreenContext(
        val label: String,
        val sourceFile: String,
        val sourceFunctions: String
    )

    private val routeMap: Map<String, ScreenContext> = mapOf(
        Screen.Chart.route to ScreenContext(
            "Chart", "ui/chart/ChartScreen.kt", "ChartScreen, ChartViewModel"
        ),
        Screen.Compare.route to ScreenContext(
            "Compare", "ui/compare/CompareScreen.kt", "CompareScreen, CompareViewModel"
        ),
        Screen.Capture.route to ScreenContext(
            "Capture", "ui/capture/CaptureScreen.kt", "CaptureScreen, CaptureViewModel"
        ),
        Screen.Gallery.route to ScreenContext(
            "Gallery", "ui/gallery/GalleryScreen.kt", "GalleryScreen, GalleryViewModel"
        ),
        Screen.History.route to ScreenContext(
            "History", "ui/history/HistoryScreen.kt", "HistoryScreen, HistoryViewModel"
        ),
        Screen.Settings.route to ScreenContext(
            "Settings", "ui/settings/SettingsScreen.kt", "SettingsScreen, SettingsViewModel"
        )
    )

    private val routePrefixMatch: Map<String, ScreenContext> = mapOf(
        "photo" to ScreenContext(
            "Photo Detail", "ui/detail/PhotoDetailScreen.kt", "PhotoDetailScreen, PhotoDetailViewModel"
        ),
        "edit" to ScreenContext(
            "Edit Photo", "ui/edit/EditPhotoScreen.kt", "EditPhotoScreen, EditPhotoViewModel"
        )
    )

    /**
     * Resolves a navigation route string to a [ScreenContext].
     * Tries exact match first, then prefix match (stripping path parameters).
     */
    fun resolve(route: String?): ScreenContext {
        if (route == null) return ScreenContext("Unknown", "MainActivity.kt", "MainActivity")

        // Exact match
        routeMap[route]?.let { return it }

        // Prefix match — find the longest matching prefix
        val matchedKey = routePrefixMatch.keys
            .filter { route.startsWith(it) }
            .maxByOrNull { it.length }

        return matchedKey?.let { routePrefixMatch[it] }
            ?: ScreenContext(route, "Unknown", "Unknown")
    }
}
