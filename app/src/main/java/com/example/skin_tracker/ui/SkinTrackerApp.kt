package com.example.skin_tracker.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.skin_tracker.SkinTrackerApp
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.ui.capture.CaptureScreen
import com.example.skin_tracker.ui.chart.ChartScreen
import com.example.skin_tracker.ui.compare.CompareScreen
import com.example.skin_tracker.ui.debug.DebugBubbleOverlay
import com.example.skin_tracker.ui.detail.PhotoDetailScreen
import com.example.skin_tracker.ui.edit.EditPhotoScreen
import com.example.skin_tracker.ui.gallery.GalleryScreen
import com.example.skin_tracker.ui.history.HistoryScreen
import com.example.skin_tracker.ui.settings.SettingsScreen
import java.util.Calendar

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SkinTrackerApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as SkinTrackerApp).container

    // Determine start destination: go to Capture if no photos captured today
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        // Use Calendar to advance by 1 day (DST-safe, handles 23/25-hour days)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis

        val todayCount = container.photoRepository.countToday(startOfDay, endOfDay)
        startDestination = if (todayCount == 0) Screen.Capture.route else Screen.Chart.route
    }

    // Wait until we know the start destination
    if (startDestination == null) return

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    // Play button trigger — incremented each time the user taps play in the TopAppBar.
    // ChartScreen observes this via LaunchedEffect.
    var playTrigger by remember { mutableIntStateOf(0) }
    val sharedCategory by container.sharedCategory.collectAsState()
    val showPlayButton = currentRoute == Screen.Chart.route && sharedCategory == Category.FACE

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Skin Tracker") },
                    actions = {
                        if (showPlayButton) {
                            IconButton(onClick = { playTrigger++ }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play face animation"
                                )
                            }
                        }
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            },
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
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = startDestination!!,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Chart.route) {
                        ChartScreen(
                            onPhotoClick = { photoId ->
                                navController.navigate(Screen.PhotoDetail.createRoute(photoId))
                            },
                            playTrigger = playTrigger
                        )
                    }
                    composable(
                        route = Screen.Compare.route,
                        enterTransition = { fadeIn(tween(350, easing = FastOutSlowInEasing)) },
                        exitTransition = { fadeOut(tween(200, easing = LinearEasing)) },
                        popEnterTransition = { fadeIn(tween(350, easing = FastOutSlowInEasing)) },
                        popExitTransition = { fadeOut(tween(200, easing = LinearEasing)) }
                    ) {
                        CompareScreen(
                            onHistoryClick = {
                                navController.navigate(Screen.History.route)
                            },
                            onPhotoDetail = { photoId ->
                                navController.navigate(Screen.PhotoDetail.createRoute(photoId))
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this
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
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.PhotoDetail.route,
                        arguments = listOf(navArgument("photoId") { type = NavType.LongType }),
                        enterTransition = { fadeIn(tween(350, easing = FastOutSlowInEasing)) },
                        exitTransition = { fadeOut(tween(200, easing = LinearEasing)) },
                        popEnterTransition = { fadeIn(tween(350, easing = FastOutSlowInEasing)) },
                        popExitTransition = { fadeOut(tween(200, easing = LinearEasing)) }
                    ) { backStackEntry ->
                        val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
                        PhotoDetailScreen(
                            photoId = photoId,
                            onBack = { navController.popBackStack() },
                            onEditPhoto = { editId ->
                                navController.navigate(Screen.EditPhoto.createRoute(editId))
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this
                        )
                    }
                    composable(
                        route = Screen.EditPhoto.route,
                        arguments = listOf(navArgument("photoId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
                        EditPhotoScreen(
                            photoId = photoId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Debug bubble overlay — sits on top of everything
        DebugBubbleOverlay(
            currentRoute = currentRoute,
            debugPrefs = container.debugPreferences,
            debugNoteRepo = container.debugNoteRepository
        )
    }
}
