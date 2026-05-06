package com.example.skin_tracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.skin_tracker.ui.chart.ChartScreen
import com.example.skin_tracker.ui.compare.CompareScreen
import com.example.skin_tracker.ui.capture.CaptureScreen
import com.example.skin_tracker.ui.gallery.GalleryScreen
import com.example.skin_tracker.ui.detail.PhotoDetailScreen
import com.example.skin_tracker.ui.history.HistoryScreen

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Chart, "Chart", Icons.AutoMirrored.Filled.ShowChart),
    BottomNavItem(Screen.Compare, "Compare", Icons.Default.Compare),
    BottomNavItem(Screen.Capture, "Capture", Icons.Default.CameraAlt),
    BottomNavItem(Screen.Gallery, "Gallery", Icons.Default.PhotoLibrary)
)

@Composable
fun SkinTrackerApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chart.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chart.route) {
                ChartScreen(
                    onPhotoClick = { photoId ->
                        navController.navigate(Screen.PhotoDetail.createRoute(photoId))
                    }
                )
            }
            composable(Screen.Compare.route) {
                CompareScreen(
                    onHistoryClick = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }
            composable(Screen.Capture.route) {
                CaptureScreen()
            }
            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onPhotoClick = { photoId ->
                        navController.navigate(Screen.PhotoDetail.createRoute(photoId))
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.PhotoDetail.route,
                arguments = listOf(navArgument("photoId") { type = NavType.LongType })
            ) { backStackEntry ->
                val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
                PhotoDetailScreen(
                    photoId = photoId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
