# ADR: Tail score-history sync (Face/Body Skin Score slots)

## Context
Elo ratings of past photos keep drifting as new comparisons are made, so a "send only today's score" integration would leave Tail's copy stale. At the same time, pushing the history on every comparison would spam Tail with redundant broadcasts.

## Decision
- Two new integration slots on `HabitIntegrationRepository.Slot`: `FACE_SCORE` and `BODY_SCORE`, each mapped by the user to a Tail habit and bound to a `Category`.
- Daily score = average Elo rating of that category's photos captured that day (local timezone), rounded to a positive Int (real scores are ~1500, always ≥ 1).
- Transport: Tail's existing `HabitValueSetReceiver` (`com.example.tail.ACTION_SET_HABIT_VALUES`, extras `EXTRA_HABIT_ID` + `EXTRA_VALUES_JSON`), which SETs (replaces) per-date values — idempotent.
- Efficiency: the full history is the source of truth, but only dates whose values differ from the last successfully sent snapshot (persisted per slot in SharedPreferences as JSON) are broadcast; the snapshot is updated after a successful dispatch.
- Deletion handling: days present in the last-sent snapshot but absent from the current history (photos deleted / soft-deleted photos excluded by the DAO) are sent with value **0**. Tail (updated in lockstep) interprets 0 as "clear this date's entry" and removes it from the habits file. WAGS is unaffected (it never sends 0).
- Cadence: sync runs at most once per calendar day (ISO-date throttle key `last_score_sync_day`), piggybacked on the photo-added usage signal via `notifyPhotoAdded()` (capture) and a post-import `syncScoreHistory()` call (gallery import). Never triggered by comparisons.
- Manual override: "Send Scores Now" button in Settings → Tail App Integration calls `syncScoreHistory(force = true)` and surfaces the result as a snackbar.

## Consequences
- Tail's copy converges to the full history at most one day behind, with only changed/cleared dates on the wire; deleted days are fully removed from Tail.
- Tail-side protocol change: `HabitValueSetReceiver` now accepts values ≥ 0 and `HabitsRepository.setHabitValuesForDates` removes entries for values ≤ 0 — requires the updated Tail APK to be installed alongside.
- If a broadcast is dispatched but Tail fails to process it, the local snapshot would wrongly consider it sent; the next day's sync only re-sends once values change again. The "Send Scores Now" button bypasses the daily throttle but not the snapshot.