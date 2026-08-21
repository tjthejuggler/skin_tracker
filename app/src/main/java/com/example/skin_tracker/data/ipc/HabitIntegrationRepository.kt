package com.example.skin_tracker.data.ipc

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.skin_tracker.data.repo.PhotoRepository
import com.example.skin_tracker.domain.model.Category
import com.example.skin_tracker.domain.model.HabitEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Handles all IPC communication with the Tail habit-tracking app.
 *
 * Skin Tracker has three slots:
 *  - [Slot.PHOTO_ADDED] — fires when a new photo is added (captured or imported).
 *    The user picks which Tail habit to increment from the Settings screen.
 *  - [Slot.FACE_SCORE] / [Slot.BODY_SCORE] — the daily average skin score (Elo
 *    rating) for that category. Because past scores keep shifting as new
 *    comparisons are made, the *whole* history is re-sent (as a diff against
 *    what Tail already has) rather than just the latest day. This sync runs at
 *    most once per day, piggybacked on the photo-added signal, or on demand
 *    from Settings ("Send Scores Now").
 *
 * The broadcasts are:
 *  - **Explicit** (package + action set) — required for reliable delivery on API 26+
 *  - **Permission-guarded** via [PERMISSION_TAIL] — only the Tail app can receive them
 */
class HabitIntegrationRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val photoRepository: PhotoRepository
) {

    // ── Activity slot ────────────────────────────────────────────────────────

    enum class Slot(
        val idKey: String,
        val nameKey: String,
        val label: String,
        /** Category whose daily average score feeds this slot (null = not a score slot). */
        val category: Category? = null
    ) {
        PHOTO_ADDED(
            idKey   = "habit_id_photo_added",
            nameKey = "habit_name_photo_added",
            label   = "Photo Added"
        ),
        FACE_SCORE(
            idKey   = "habit_id_face_score",
            nameKey = "habit_name_face_score",
            label   = "Face Skin Score",
            category = Category.FACE
        ),
        BODY_SCORE(
            idKey   = "habit_id_body_score",
            nameKey = "habit_name_body_score",
            label   = "Body Skin Score",
            category = Category.BODY
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

    // ── Broadcast triggers ────────────────────────────────────────────────────

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

    /**
     * The "usage signal": tells Tail a photo was added, and — at most once per
     * day — also pushes the current score history to the FACE/BODY score slots.
     */
    suspend fun notifyPhotoAdded() {
        sendHabitIncrement(Slot.PHOTO_ADDED)
        syncScoreHistory()
    }

    // ── Score history sync ────────────────────────────────────────────────────

    /**
     * Result of a score-history sync, for surfacing in the UI.
     *
     * @param datesSent  Total number of date entries actually broadcast to Tail
     *                   (0 = everything was already up to date).
     * @param throttled  True when the sync was skipped because it already ran today.
     * @param slotsSynced Names/labels of the score slots that had a habit configured.
     */
    data class ScoreSyncResult(
        val datesSent: Int,
        val throttled: Boolean,
        val slotsSynced: List<String>
    )

    /**
     * Pushes the daily average skin-score history for the FACE_SCORE and
     * BODY_SCORE slots to the Tail app.
     *
     * Past scores drift as new comparisons are made, so the *full* history is
     * the source of truth — but for efficiency only the dates whose values
     * changed since the last successful send are actually broadcast. Tail's
     * SET-semantics receiver makes this idempotent.
     *
     * Runs at most once per calendar day unless [force] is true (the Settings
     * "Send Scores Now" button).
     */
    suspend fun syncScoreHistory(force: Boolean = false): ScoreSyncResult =
        withContext(Dispatchers.IO) {
            val scoreSlots = Slot.entries.filter { it.category != null }

            // Daily throttle — the history only needs pushing once a day.
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (!force && prefs.getString(KEY_LAST_SYNC_DAY, "") == today) {
                Log.d(TAG, "syncScoreHistory: already synced today, skipping")
                return@withContext ScoreSyncResult(0, throttled = true, slotsSynced = emptyList())
            }

            var datesSent = 0
            val synced = mutableListOf<String>()

            for (slot in scoreSlots) {
                val habitId = getHabitId(slot)
                if (habitId.isBlank()) continue

                val desired = computeDailyScores(slot.category!!)
                val lastSent = loadLastSent(slot)

                // Efficient delta: only dates that are new or whose value
                // changed, plus days that vanished (photos deleted) — those
                // are sent as 0, which Tail interprets as "clear this date".
                val changed = desired.filter { (date, value) -> lastSent[date] != value }
                val removed = (lastSent.keys - desired.keys).associateWith { 0 }
                val toSend = changed + removed

                if (toSend.isNotEmpty()) {
                    val json = JSONObject().apply {
                        toSend.forEach { (date, value) -> put(date, value) }
                    }
                    if (sendHabitValues(habitId, json.toString())) {
                        lastSent.putAll(changed)
                        removed.keys.forEach { lastSent.remove(it) }
                        saveLastSent(slot, lastSent)
                        datesSent += changed.size
                        Log.i(TAG, "syncScoreHistory(${slot.name}): sent ${changed.size} dates " +
                                "(${removed.size} cleared) to '$habitId'")
                    }
                } else {
                    Log.d(TAG, "syncScoreHistory(${slot.name}): no changes, nothing sent")
                }
                synced += slot.label
            }

            prefs.edit().putString(KEY_LAST_SYNC_DAY, today).apply()

            ScoreSyncResult(datesSent = datesSent, throttled = false, slotsSynced = synced)
        }

    /**
     * Computes the daily average Elo rating for [category], keyed by ISO date
     * string ("yyyy-MM-dd") in the device's local timezone. Values are rounded
     * to positive Ints as required by Tail's value-set protocol.
     */
    private suspend fun computeDailyScores(category: Category): Map<String, Int> {
        val photos = photoRepository.getByCategorySync(category)
        val zone = ZoneId.systemDefault()
        return photos
            .groupBy { photo ->
                java.time.Instant.ofEpochMilli(photo.capturedAt).atZone(zone).toLocalDate()
            }
            .map { (date, dayPhotos) ->
                date.format(DateTimeFormatter.ISO_LOCAL_DATE) to
                        dayPhotos.map { it.rating }.average().toInt().coerceAtLeast(1)
            }
            .toMap()
    }

    private fun lastSentKey(slot: Slot) = "last_sent_scores_${slot.name.lowercase()}"

    private fun loadLastSent(slot: Slot): MutableMap<String, Int> {
        val json = prefs.getString(lastSentKey(slot), null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            for (key in obj.keys()) map[key] = obj.getInt(key)
            map
        } catch (e: Exception) {
            Log.w(TAG, "loadLastSent(${slot.name}): corrupt JSON, starting fresh — ${e.message}")
            mutableMapOf()
        }
    }

    private fun saveLastSent(slot: Slot, values: Map<String, Int>) {
        val json = JSONObject().apply { values.forEach { (k, v) -> put(k, v) } }
        prefs.edit().putString(lastSentKey(slot), json.toString()).apply()
    }

    /**
     * Sends an explicit, permission-guarded broadcast to the Tail app requesting
     * that the habit's stored value for each provided date be **set** (replaced)
     * with the given value. Returns true if the broadcast was dispatched.
     */
    private fun sendHabitValues(habitId: String, valuesJson: String): Boolean {
        return try {
            val intent = Intent(ACTION_SET_VALUES).apply {
                `package` = HABIT_APP_PACKAGE
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_VALUES_JSON, valuesJson)
            }
            context.sendBroadcast(intent, PERMISSION_TAIL)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "sendHabitValues: SecurityException — Tail app likely not installed. ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "sendHabitValues: unexpected error — ${e.message}")
            false
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

        /** Tail's SET-semantics receiver: replaces the stored value per date. */
        const val ACTION_SET_VALUES = "com.example.tail.ACTION_SET_HABIT_VALUES"
        const val EXTRA_VALUES_JSON = "EXTRA_VALUES_JSON"

        /** ISO date (yyyy-MM-dd) of the last score-history sync, for the daily throttle. */
        private const val KEY_LAST_SYNC_DAY = "last_score_sync_day"

        const val PERMISSION_TAIL = "com.example.tail.permission.TAIL_INTEGRATION"
    }
}
