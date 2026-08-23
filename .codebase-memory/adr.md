# ADR: Compare → PhotoDetail transition strategy

## Status
Superseded (2026-08-23, second revision) — plain cross-fade replaces the shared-element morph

## Context
The Compare ↔ PhotoDetail transition was originally choppy for three reasons: (1) PhotoDetailScreen rendered its image only after an async Room query, so content popped in late; (2) the detail pager started at page 0 and visibly slid to the tapped photo; (3) corner-radius mismatch caused a pop at the end of the shared-element morph.

A first fix seeded the detail state instantly via a handoff cache and refined the shared-element morph. On-device testing showed the bounds morph still resized the image in jarring, uneven steps (ContentScale.Fit re-layout inside sharedBounds across very different aspect ratios). The user preferred a simple fade over an imperfect morph.

## Decision (current)
- **No shared-element morph.** `SharedTransitionLayout`, `photoSharedElementModifier()`, `sharedCornerProgress()` and the transition-scope parameters were removed from SkinTrackerApp, CompareScreen and PhotoDetailScreen. PhotoSharedElement.kt now only provides `SharedPhotoCorner` (12dp card corner) and `rememberPhotoImageRequest` (pinned Coil memory-cache key + screen-size decode for instant cache hits across screens).
- **Even 300 ms cross-fade** (`CrossFadeIn`/`CrossFadeOut`, tween + FastOutSlowInEasing) for both Compare and PhotoDetail destinations on enter/exit/pop, so one screen dissolves into the other with matched timing.
- **Kept from the first fix** (these make the fade clean):
  - `AppContainer.photoHandoffCache` (LruCache<Long, Photo>, capacity 8), populated by CompareViewModel on every shown pair; PhotoDetailViewModel seeds its StateFlow synchronously with the tapped photo so the detail screen is fully rendered on frame 1 — no pop-in after the Room query.
  - Detail pager snap-syncs (`scrollToPage`, not `animateScrollToPage`) when the seeded single-photo list is replaced by the full category list — invisible because the visible photo is unchanged.
  - The "N of M" page counter fades in gently via AnimatedVisibility.

## Consequences
- Transition is a clean, symmetric fade in both directions with zero image resizing.
- Gallery/Chart → Detail still works without seeding (initialPhoto is optional) and benefits from the snap-sync fix.
- If a shared-element morph is ever revisited, the handoff-cache seeding is the required foundation; the morph itself would need `sharedElement` (geometric scale) rather than `sharedBounds` (re-layout) to avoid the stepped resizing.