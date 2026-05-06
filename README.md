# Skin Tracker

A personal psoriasis tracking app for Android. Take daily photos of your face or body, then compare them head-to-head to build an Elo rating over time. Interactive charts show your skin's progress.

## Features

- **📷 In-app Camera** — Take a quick selfie each morning with front/back camera toggle
- **🖼️ Gallery Import** — Import existing photos from your phone's gallery (uses EXIF date)
- **⚖️ Compare Mode** — Two random photos shown side-by-side; tap the one where your skin looks better
- **📊 Interactive Charts** — Zoomable, pannable Elo rating chart with time-range filters (1W/1M/3M/6M/1Y/All), daily average overlay, and tap-to-view photo details
- **🏷️ Categories** — Face and Body tracked separately
- **📈 Elo Rating** — Each photo starts at 1500; comparisons update ratings using K=32 Elo system
- **🔍 Photo Detail** — Full-screen view with swipe between same-day photos, rating stats, and delete

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

1. **Capture or Import** — Add photos via the camera or gallery import. Each photo is tagged as Face or Body.
2. **Compare** — The app shows you two random photos from the same category. Tap the one where your psoriasis looks better. The Elo rating updates for both photos.
3. **Track** — The chart screen shows your Elo rating over time. Per-photo data points are connected by a line; a dashed daily-average overlay appears when a day has multiple photos.
4. **Pair Selection** — Random within category, biased toward photos with fewer comparisons so new uploads catch up faster.

## Project Structure

```
com.example.skin_tracker/
├── MainActivity.kt
├── SkinTrackerApp.kt              (Application class)
├── di/AppContainer.kt             (manual DI)
├── data/
│   ├── db/                        (Room: AppDatabase, DAOs)
│   ├── entity/                    (PhotoEntity, ComparisonEntity)
│   ├── repo/                      (PhotoRepository, ComparisonRepository)
│   └── storage/PhotoFileStore.kt  (file I/O, downscaling)
├── domain/
│   ├── model/                     (Category, Photo)
│   └── rating/                    (Elo, PairPicker)
├── ui/
│   ├── Navigation.kt
│   ├── SkinTrackerApp.kt          (NavHost + bottom bar)
│   ├── chart/                     (ChartScreen, ChartViewModel)
│   ├── compare/                   (CompareScreen, CompareViewModel)
│   ├── capture/                   (CaptureScreen, CaptureViewModel)
│   ├── gallery/                   (GalleryScreen, GalleryViewModel)
│   ├── detail/                    (PhotoDetailScreen, PhotoDetailViewModel)
│   └── theme/                     (Color, Theme, Type)
└── util/ExifDateReader.kt
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
