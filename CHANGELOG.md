# Changelog

## [0.1.4] - 2026-11-01

### Fixed

- Tablet tilt direction: the sensor-axis mapping assumed a portrait-natural display
  (`getRotation()` is relative to the natural orientation, which is landscape on most
  tablets), so directions were 90° off on tablets. The rotation is now converted to a
  portrait-relative index from the current real size + rotation; phone behavior is
  unchanged.

## [0.1.3] - 2026-11-01

### Added

- Multiple custom groups: create, name/rename, delete; 0–6 slots per group, at least 1 to select
- Group picker with thumbnail cards, current marker, built-in/custom badges, and edit/new actions
- One-time migration of the old single custom group to a group named 自定义
- Release signing configuration (`keystore.properties`, `keystore.properties.example`, `docs/SIGNING.md`)
- `tools/publish_update.py --asset-name` for release-signed APK filenames

### Changed

- Main screen group card opens the picker; the spinner is removed
- versionCode 4 / versionName 0.1.3

## [0.1.2] - 2026-11-01

### Added

- In-app update check: Chengdu primary + GitHub fallback, versionCode comparison, SHA-256 verification, system installer
- `update.json`, `tools/publish_update.py`, `tools/sync_chengdu.sh`, `docs/UPDATE_SOURCE.md`

### Changed

- versionCode 3 / versionName 0.1.2

## [0.1.1] - 2026-11-01

### Changed

- Limit the built-in demo to five groups: Yixuan, Pyrois, Miyabi, Vivian, Ye Shunguang (14 images)
- Attribute the artwork to the official miHoYo / 米游社 (Miyoushe) "绳网情报站" wiki

## [0.1.0] - 2026-11-01

### Added

- 14 built-in groups / 41 images, ranked by colorfulness (1 = most colorful) with Chinese names
- Single soft gradient boundary; 2D tilt direction; unbounded `1→2→3→1` cycling
- One Euro Filter + soft dead zone + direction dead zone + phase rate limit
- Tablet/landscape assets, aspect-fill cropping, display-rotation compensation, 1.2x range
- Custom group: 1-6 slots, image picker, 90-degree rotation, reorder, delete
- `com.raster.dial` package, app icon, card UI, full-screen preview
- Debug broadcast, on-wallpaper debug line, group switching

### Known limitations

- Home-screen live wallpaper only; OEM lock screens are a separate system and are not taken over
- Canvas `saveLayer` renderer; a GLES renderer is planned for tablets and 4K panels
- Continuous 60 fps redraw while visible; event-driven dirty rendering is planned
- Bundled Zenless Zone Zero official artwork from the official "绳网情报站" wiki is not MIT-licensed; remove or replace it before redistribution
