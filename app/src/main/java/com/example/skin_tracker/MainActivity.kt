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
    //
    // IMPORTANT: onStop() also fires when one of our own launched activities comes to the
    // front (e.g. the Storage Access Framework folder picker used for backups). In that case
    // we must NOT finish the task, otherwise the app is destroyed while the picker is open and
    // the user experiences it as an instant crash on return. [expectingActivityResult] guards
    // against this.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !expectingActivityResult) {
            finishAndRemoveTask()
        }
    }

    companion object {
        /**
         * Set to true immediately before launching one of our own activities that returns a
         * result (SAF pickers, etc.) so [onStop] does not tear down the task while it is on
         * screen. The launcher's result callback resets it to false.
         */
        @Volatile
        var expectingActivityResult: Boolean = false
    }
}
