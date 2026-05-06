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
}
