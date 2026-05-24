package com.example.skin_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.skin_tracker.ui.SkinTrackerApp
import com.example.skin_tracker.ui.theme.Skin_trackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Skin_trackerTheme {
                SkinTrackerApp()
            }
        }
    }

    // Fully close the app when the user minimizes it (presses Home or switches away).
    // This prevents the app from running in the background.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            finishAndRemoveTask()
        }
    }
}
