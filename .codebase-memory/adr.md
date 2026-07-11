## Backup System Migration to ZIP Format

**Date:** 2025-01-11
**Status:** Implemented

### Context
The previous backup system exported individual image files to a directory, which caused two issues:
1. Flooded the user's phone gallery with backup images
2. Took up more space due to lack of compression

### Decision
Migrated the backup system to use a single ZIP file (`images.zip`) containing all images instead of individual files in an `images/` directory.

### Changes Made
1. **BackupManager.kt**:
   - Added imports for `java.util.zip.*` (ZipOutputStream, ZipInputStream, ZipEntry, FileInputStream)
   - Modified [`backup()`](app/src/main/java/com/example/skin_tracker/data/backup/BackupManager.kt:84) to create a new `images.zip` file containing all images (replaces any existing zip)
   - Modified [`restore()`](app/src/main/java/com/example/skin_tracker/data/backup/BackupManager.kt:149) to extract images from `images.zip` instead of reading from an `images/` directory
   - Removed helper methods `copyFileToDir()` and `copyDocToFile()` (no longer needed)
   - Updated companion object constant from `IMAGES_DIR = "images"` to `IMAGES_ZIP = "images.zip"`
   - Updated documentation to reflect new backup layout

### New Backup Layout
```
SkinTrackerBackup/
  backup.json          # metadata manifest (photos + comparisons)
  images.zip           # compressed archive containing all images
```

### Benefits
- Images are no longer visible in the phone's gallery (single zip file)
- Reduced storage footprint due to compression
- Simpler backup folder structure (2 files instead of 1 + N images)
- No incremental complexity - full backup each time (manifest is tiny anyway)