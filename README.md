# Skin Tracker

A personal psoriasis tracking app for Android. Take daily photos of your face or body, then compare them head-to-head to build an Elo rating over time. Interactive charts show your skin's progress.

## Features

- **📷 In-app Camera** — Take a quick selfie each morning with front/back camera toggle
- **🖼️ Gallery Import** — Import existing photos from your phone's gallery (uses EXIF date)
- **🤖 Auto Face/Body Detection** — Automatically tags photos as Face or Body using on-device face detection (Android built-in FaceDetector)
- **⚖️ Compare Mode** — Two random photos shown side-by-side; tap the one where your skin looks better
- **📊 Interactive Charts** — Zoomable, pannable Elo rating chart with time-range filters (1W/1M/3M/6M/1Y/All), period step arrows (←/→) to navigate back/forward by the selected time unit, daily average overlay, and tap-to-view photo details
- **🏷️ Categories** — Face and Body tracked separately
- **📈 Elo Rating** — Each photo starts at 1500; comparisons update ratings using K=32 Elo system
- **🔍 Photo Detail** — Full-screen view with swipe between same-day photos, rating stats, and delete
- **🔗 Tail App Integration** — Automatically increment a habit in the Tail habits app whenever a new photo is added (captured or imported)
- **🐛 Debug Bubble** — Floating draggable bubble overlay for logging bugs, features, and notes per-screen; saves to `debug_skin_tracker.json` for LLM-assisted debugging
- **💾 Uninstall-Proof Backup** — Automatic incremental backup of every image plus a full metadata manifest to a user-chosen folder (via Storage Access Framework), so data survives a complete app uninstall. One-tap Restore rebuilds the database and images on a fresh install.

## Tech Stack

| Layer | Tech |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| Camera | CameraX |
| Gallery | Android Photo Picker |
| Database | Room (SQLite) |
| Image Loading | Coil |
| Charts | MPAndroidChart |
| EXIF | androidx.exifinterface |
| Architecture | MVVM + manual DI (AppContainer) |
| Min SDK | 26 (Android 8.0) |

## Building

```bash
./gradlew assembleDebug
```

## Installing

```bash
./gradlew installDebug
```

## How It Works

1. **Capture or Import** — Add photos via the camera or gallery import. Photos are automatically tagged as Face or Body using on-device face detection; you can still override with the toggle.
2. **Compare** — The app shows you two random photos from the same category. Tap the one where your psoriasis looks better. The Elo rating updates for both photos.
3. **Track** — The chart screen shows your Elo rating over time. Per-photo data points are connected by a line; a dashed daily-average overlay appears when a day has multiple photos.
4. **Pair Selection** — Random within category, biased toward photos with fewer comparisons so new uploads catch up faster.

## Backup & Restore

*(Added 2026-07-10)* Skin Tracker keeps all images in app-private storage (`filesDir/photos/`) and metadata in the Room database, both of which are erased on uninstall. To make the data survive a full app deletion, an uninstall-proof backup system writes to a folder **outside** the app sandbox using the Storage Access Framework (SAF).

- **Set it up once** — In **Settings → Backup & Restore**, choose a backup folder (ideally a cloud-synced folder such as Google Drive / Nextcloud, or an SD card). The app takes a persistable URI permission so it keeps write access across reboots.
- **Automatic** — Backups run on every app start and are debounced after each data change (photo add, edit/crop, delete, category change, comparison). Image copies are **incremental** (only new files by filename); the small `backup.json` manifest is always rewritten in full.
- **Layout** — `SkinTrackerBackup/backup.json` + `SkinTrackerBackup/images/*.jpg` inside the chosen folder.
- **Restore** — After a reinstall, pick the same folder and tap **Restore**. Images are copied back into app-private storage, the URIs are re-rooted, and the Room tables are repopulated (primary keys preserved via `REPLACE` upserts).

## Project Structure

```
com.example.skin_tracker/
├── MainActivity.kt
├── SkinTrackerApp.kt              (Application class)
├── di/AppContainer.kt             (manual DI)
├── data/
│   ├── db/                        (Room: AppDatabase, DAOs)
│   ├── entity/                    (PhotoEntity, ComparisonEntity)
│   ├── debug/                     (DebugPreferences, DebugNoteRepository, ScreenContextMapper)
│   ├── ipc/                       (HabitIntegrationRepository — Tail IPC)
│   ├── repo/                      (PhotoRepository, ComparisonRepository)
│   └── storage/PhotoFileStore.kt  (file I/O, downscaling)
├── domain/
│   ├── model/                     (Category, Photo, HabitEntry, DebugNote, NoteType)
│   └── rating/                    (Elo, PairPicker)
├── ui/
│   ├── Navigation.kt
│   ├── SkinTrackerApp.kt          (NavHost + bottom bar + debug bubble)
│   ├── chart/                     (ChartScreen, ChartViewModel, TimeRange)
│   ├── components/                (SelectableChip — shared chip component)
│   ├── compare/                   (CompareScreen, CompareViewModel)
│   ├── capture/                   (CaptureScreen, CaptureViewModel)
│   ├── debug/                     (DebugBubbleOverlay, DebugNoteDialog)
│   ├── gallery/                   (GalleryScreen, GalleryViewModel)
│   ├── detail/                    (PhotoDetailScreen, PhotoDetailViewModel)
│   ├── settings/                  (SettingsScreen, SettingsViewModel)
│   └── theme/                     (Color, Theme, Type)
└── util/
    ├── ExifDateReader.kt
    └── FaceDetectorUtil.kt   (on-device face detection)
```

## Changelog

### 2026-05-06 — v1.0 Initial Implementation
- Full app scaffold with bottom navigation (Chart, Compare, Capture, Gallery)
- CameraX integration with front/back toggle and category selection
- Gallery import via Photo Picker with EXIF date extraction
- Elo rating system (K=32, initial 1500) with weighted pair selection
- MPAndroidChart with zoom/pan, time-range chips, daily average overlay
- Photo detail screen with swipe pager and delete
- Room database with Photo and Comparison entities
- App-private file storage with auto-downscaling to 2048px

### 2026-05-08 — Auto Face/Body Detection
- Added `FaceDetectorUtil` using Android's built-in `android.media.FaceDetector` (zero dependencies)
- Camera capture auto-detects face vs body and pre-selects the category
- Gallery import auto-detects per photo (removed the category selection dialog)
- Users can still manually override the category via the toggle

### 2026-05-09 — Tail App Integration
- Added Settings screen accessible from the top app bar gear icon
- Integrated with the Tail habits app via ContentProvider (fetch habits) and explicit broadcast (increment habit)
- Single "Photo Added" slot: when a photo is captured or imported, the selected Tail habit is incremented
- `HabitIntegrationRepository` handles all IPC (ContentProvider query + permission-guarded broadcast)
- Added `com.example.tail.permission.TAIL_INTEGRATION` permission to manifest
- Persisted habit selection in dedicated SharedPreferences file

### 2026-05-13 — Debug Bubble Overlay
- Added floating draggable debug bubble overlay visible on all screens when enabled
- Three-tab dialog: Note (compose), Queue (batch submit), Saved (persisted notes)
- Notes tagged with Bug/Feature/Note type and current screen context (source file + functions)
- Submit appends to `debug_skin_tracker.json` (accumulates across submissions, unlike wags which replaces)
- Settings screen includes Debug Mode toggle and optional SAF directory picker for JSON output
- `DebugPreferences` persists debug mode, directory URI, saved notes, and queued notes
- `ScreenContextMapper` maps skin_tracker routes to source file context for LLM debugging

### 2026-05-27 — Chart Screen & Selection UX Improvements
- Replaced `LazyRow` time-range chips with a compact `Row(SpaceEvenly)` layout — all 6 periods (1W/1M/3M/6M/1Y/All) now fit on screen without horizontal scrolling
- Added period step arrows (← / →) below the time-range row: stepping back/forward navigates by the selected time unit (e.g. 1M steps by month, 1W by week)
- Forward arrow disabled when at the current (most recent) period; period label shown between arrows
- Created shared `SelectableChip` composable: selected chips use `primary` fill + `onPrimary` text + elevation shadow for a clear "glow" effect; unselected chips use `surfaceVariant`
- Replaced all `FilledTonalButton` category tabs (Face/Body) on Chart, Compare, and Capture screens with `SelectableChip` for consistent, visually distinct selection state
- `ChartViewModel` now tracks `periodOffset` (Int) and produces a human-readable `periodLabel` (e.g. "May 2026", "Apr 28 – May 4")
