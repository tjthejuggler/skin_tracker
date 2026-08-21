# ADR: Day Notes feature (2026-08-21)

## Context
Users want to annotate arbitrary days (today or past) with short notes and see them as small markers on the Elo chart, with per-chart targeting (Both / Face / Body). Notes must survive the uninstall-proof backup/restore cycle.

## Decision
1. **Storage**: New Room table `notes` (DB v1→v2, `MIGRATION_1_2`). Rows keyed by `epochDay` (`LocalDate.toEpochDay`) instead of epoch-millis — DST-safe, timezone-independent day identity. Multiple notes per day allowed (index on epochDay, not unique).
2. **Targeting**: `NoteTarget` enum (BOTH default, FACE, BODY) stored as a string column; `appliesTo(Category)` decides chart visibility. Chart markers are amber dots (`#FFD54F`) drawn as a transparent-line LineDataSet just below the data (minY − 12% span), with `axisLeft.axisMinimum` temporarily lowered to keep them visible and reset when no notes apply.
3. **UI**: `DayNoteDialog` (ui/notes) — Material3 DatePicker constrained to today/past, per-day note list with edit/delete, target chips. Entry point is a sticky-note IconButton in the chart period-nav row (tinted primary when the current chart has notes). Notes flow through ChartViewModel (state combine now includes NoteRepository.getAll()).
4. **Backup**: `backup.json` manifest v2 adds a `notes` array; restore parses it optionally (v1 manifests restore with zero notes). `RestoreResult.Success` gained `noteCount` with a default value so existing callers compile unchanged.
5. **Habit picker fix** (same session): Tail's HabitsContentProvider now filters blank and `app_link:` pseudo-habit names at the source; skin_tracker's `fetchHabits()` also filters/dedupes defensively, and the Settings habit picker gained a search field with a height-capped scrollable list.

## Consequences
- DB migration is additive; no data loss risk.
- Old backups (manifest v1) restore cleanly without notes.
- Chart marker Y position is derived from visible photo min/max, so marks stay below the curve across zoom/ranges.
