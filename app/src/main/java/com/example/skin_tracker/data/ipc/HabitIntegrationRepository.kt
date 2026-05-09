package com.example.skin_tracker.data.ipc

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.skin_tracker.domain.model.HabitEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles all IPC communication with the Tail habit-tracking app.
 *
 * Skin Tracker has a single slot — [PHOTO_ADDED] — which fires when a new
 * photo is added (captured or imported). The user picks which Tail habit
 * to increment from the Settings screen.
 *
 * The broadcast is:
 *  - **Explicit** (package + action set) — required for reliable delivery on API 26+
 *  - **Permission-guarded** via [PERMISSION_TAIL] — only the Tail app can receive it
 */
class HabitIntegrationRepository(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    // ── Activity slot ────────────────────────────────────────────────────────

    enum class Slot(
        val idKey: String,
        val nameKey: String,
        val label: String
    ) {
        PHOTO_ADDED(
            idKey   = "habit_id_photo_added",
            nameKey = "habit_name_photo_added",
            label   = "Photo Added"
        )
    }

    // ── Content Provider query ────────────────────────────────────────────────

    /**
     * Queries the Tail app's Content Provider and returns a list of [HabitEntry]
     * objects. Returns an empty list if the Tail app is not installed or the
     * provider is unavailable.
     */
    suspend fun fetchHabits(): List<HabitEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<HabitEntry>()
        try {
            context.contentResolver.query(
                /* uri        */ HABITS_CONTENT_URI,
                /* projection */ arrayOf(COL_HABIT_NAME),
                /* selection  */ null,
                /* selArgs    */ null,
                /* sortOrder  */ null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(COL_HABIT_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)
                    results += HabitEntry(
                        habitId   = name,
                        habitName = name
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchHabits: could not query Tail app — ${e.message}")
        }
        results
    }

    // ── Per-slot persistence ──────────────────────────────────────────────────

    fun getHabitId(slot: Slot): String =
        prefs.getString(slot.idKey, "") ?: ""

    fun getHabitName(slot: Slot): String =
        prefs.getString(slot.nameKey, "") ?: ""

    fun setHabit(slot: Slot, entry: HabitEntry) {
        prefs.edit()
            .putString(slot.idKey,   entry.habitId)
            .putString(slot.nameKey, entry.habitName)
            .apply()
    }

    fun clearHabit(slot: Slot) {
        prefs.edit()
            .putString(slot.idKey,   "")
            .putString(slot.nameKey, "")
            .apply()
    }

    // ── Broadcast trigger ─────────────────────────────────────────────────────

    /**
     * Sends an explicit, permission-guarded broadcast to the Tail app requesting
     * that the habit mapped to [slot] be incremented by one.
     *
     * Does nothing if no habit has been selected for [slot].
     */
    fun sendHabitIncrement(slot: Slot) {
        val habitId = getHabitId(slot)
        if (habitId.isBlank()) {
            Log.d(TAG, "sendHabitIncrement(${slot.name}): no habit selected, skipping")
            return
        }

        try {
            val intent = Intent(ACTION_INCREMENT).apply {
                `package` = HABIT_APP_PACKAGE
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_SLOT, slot.name)
            }
            context.sendBroadcast(intent, PERMISSION_TAIL)
            Log.d(TAG, "sendHabitIncrement(${slot.name}): fired for habitId=$habitId")
        } catch (e: SecurityException) {
            Log.w(TAG, "sendHabitIncrement(${slot.name}): SecurityException — " +
                    "Tail app likely not installed. ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "sendHabitIncrement(${slot.name}): unexpected error — ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HabitIntegrationRepo"

        const val HABIT_APP_PACKAGE = "com.example.tail"

        val HABITS_CONTENT_URI: Uri =
            Uri.parse("content://com.example.tail.provider/habits")

        const val COL_HABIT_ID   = "habit_id"
        const val COL_HABIT_NAME = "habit_name"

        const val ACTION_INCREMENT = "com.example.tail.ACTION_INCREMENT_HABIT"
        const val EXTRA_HABIT_ID = "EXTRA_HABIT_ID"
        const val EXTRA_SLOT = "skin_tracker_slot"

        const val PERMISSION_TAIL = "com.example.tail.permission.TAIL_INTEGRATION"
    }
}
