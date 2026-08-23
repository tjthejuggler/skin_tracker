# ADR: Per-comparison decision time tracking & dedicated Stats screen

## Status
Accepted (2026-08-23, revised twice after user feedback)

## Context
The user wants to know time spent comparing: a time-per-comparison rate per category (face and body differ), calculated totals next to counts, and stats living on their own screen rather than inside Settings.

## Decision
- **Data**: `durationMs: Long = 0` on `ComparisonEntity` (Room v2 → v3: `ALTER TABLE comparisons ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0`). DAO aggregates: `totalDuration`/`totalDurationByCategory` (`SUM(durationMs)`) and `trackedCount`/`trackedCountByCategory` (`COUNT(*) WHERE durationMs > 0`), exposed via `ComparisonRepository`. BackupManager manifest writes `durationMs`, reads with `optLong(..., 0)`.
- **Timing capture**: `CompareViewModel.pairShownAt` stamps when a pair is presented (loadPair, undo restore); `pickWinner` captures elapsed ms synchronously, capped at 10 min (`MAX_DECISION_MS`). Skips contribute nothing.
- **Rate**: time per comparison = totalDuration / trackedCount per category (legacy zero-duration rows excluded from the denominator).
- **Display (StatsScreen, own route `stats` + BarChart icon in the global TopAppBar next to Settings)**: Comparisons section row order is Total comparisons → Time per comparison → Face comparisons → Time per face comparison → Body comparisons → Time per body comparison, then This week / This month / Avg-active-day. Parenthetical times are CALCULATED as count × that row's applicable rate (overall rate for week/month/avg-day rows; category rate for face/body rows) — NOT the raw recorded sum — so legacy untracked comparisons are extrapolated. Rate rows and parentheticals appear only once a rate exists.
- **Settings slimmed**: AppStats/loadStats/streak helpers moved from SettingsViewModel to new `ui/stats/StatsViewModel`; SettingsViewModel dropped its photo/comparison repository dependencies.

## Consequences
- Calculated parenthetical (count × rate) can differ from the raw recorded sum by design; the raw sums remain available in AppStats if needed later.
- Stats screen reloads on open (init), so post-restore refresh is automatic; SettingsScreen no longer triggers stat reloads.
- Comparisons made before this feature (and History flips) have no duration; rates only reflect tracked comparisons.