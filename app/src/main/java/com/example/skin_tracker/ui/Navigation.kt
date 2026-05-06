package com.example.skin_tracker.ui

sealed class Screen(val route: String) {
    data object Chart : Screen("chart")
    data object Compare : Screen("compare")
    data object Capture : Screen("capture")
    data object Gallery : Screen("gallery")
    data object History : Screen("history")
    data object PhotoDetail : Screen("photo/{photoId}") {
        fun createRoute(photoId: Long) = "photo/$photoId"
    }
    data object EditPhoto : Screen("edit/{photoId}") {
        fun createRoute(photoId: Long) = "edit/$photoId"
    }
}
