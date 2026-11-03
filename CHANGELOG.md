# Changelog

## [0.1.11] - 2026-11-03

### Changed

- Update sources are now explicitly tiered: **Chengdu primary → Gitee secondary →
  GitHub backup**, for both the manifest and the APK.
  - `BuildConfig.UPDATE_MANIFEST_SECONDARY` points at
    `https://gitee.com/kdjdhe/raster-dial/raw/main/update.json`.
  - The checker walks the manifest list in order and stops as soon as it has a
    manifest newer than the installed build; when nothing is newer it still probes
    the mirrors, so a stale primary can no longer report "up to date" while a newer
    release exists elsewhere.
- versionCode 12 / versionName 0.1.11.

## [0.1.10] - 2026-11-02

### Fixed

- In-app update stability:
  - A global download guard prevents concurrent downloads (auto + manual checks could
    previously start two threads writing the same file, corrupting it and failing
    verification); only one download dialog is kept.
  - The progress dialog now shows real phases: 下载中 x% / 校验中… / opening the
    installer, instead of a frozen "准备下载…".
  - Each source is tried twice; HTTPS timeouts raised to 10s/30s; failures report the
    reason (network / checksum) instead of a generic message.
  - Dismissal is cleaned up when the activity is finishing/destroyed, so a leaked
    dialog can no longer sit on top of a later check.

### Changed

- versionCode 11 / versionName 0.1.10.

## [0.1.9] - 2026-11-02

### Added

- Settings screen (⚙ in the main header), styled like a system "About/Settings"
  page: back pill, large title, centered about block, section labels and grouped
  cards with hairline rows.
- Moved into Settings: theme picker, update check (current version + explanation row),
  clear debug override, restore default wallpaper.
- Main screen now keeps only daily actions (group, effect, preview, wallpaper, note).

### Changed

- versionCode 10 / versionName 0.1.9.

## [0.1.8] - 2026-11-02

### Added

- Two new transition styles alongside the gradient line:
  - 百叶窗 (venetian blinds): six strips (vertical for horizontal sweeps, horizontal for
    vertical), each running its own synchronized boundary, with thin dividers.
  - 类OriginOS: one wide, soft gradient line sweeping across the screen.
  - Both are driven by the instantaneous tilt mapped to scene index (one scene per
    0.7x the half-range angle, hysteresis), so holding a tilt settles on a stable scene;
    every switch commits in a fixed 100ms run with a per-slot tick — no intermediate states.
- Configurable axis: gradient line = 全向 / 仅横向 / 仅竖向 (signed single-axis follow);
  blinds and 类OriginOS = 横向 / 竖向.
- Eye-care strength slider (persistent luminance attenuation, 0% = off) that reduces
  scene-switch flashing without adding temporal flicker; ticks haptics per 1% step.
- Main screen rebuilds in place on theme switch (no entrance replay, keeps scroll position).

### Changed

- Axis labels unified to 全向 / 仅横向 / 仅竖向.
- versionCode 9 / versionName 0.1.8.

## [0.1.7] - 2026-11-02

### Changed

- In-app update primary source switched to the Chengdu server:
  `https://update.litanghub.top:8443/raster-dial/update.json` (direct TLS on a
  non-standard port, Let's Encrypt certificate, DNS-only A record). GitHub raw stays
  as the fallback. Verified end-to-end from a real device.
- versionCode 8 / versionName 0.1.7.

## [0.1.6] - 2026-11-02

### Fixed

- Group switching was dead in the picker: the drag gesture consumed ACTION_DOWN, so the
  card never entered the pressed state and the framework never dispatched the click.
  Tap-to-switch now calls performClick() explicitly on the tap branch.
- Share mode: the selection ring/badge now follow the share selection instead of the
  current group.

## [0.1.5] - 2026-11-01

### Added

- Apple-HIG style UI redesign: type scale, layered surfaces, spring motion, press-down
  feedback, and haptics throughout
- Six themes (曜石 / 冰川 / 森绿 / 霞紫 / 绯红 / 鎏金) with instant switching, persisted
- Group picker: search, long-press drag reordering (spring make-way, per-slot tick haptic)
- Custom group export/import for sharing (zip bundle via share sheet / file picker)
- Multi-group share: select several custom groups in the picker and pack them into one zip
- Tablet / wide-screen adaptation: unlocked orientations, width-capped centered content,
  3-column grid on wide screens

### Changed

- Main screen: hero current-group card, segmented sensitivity control, settings-style list rows
- Fullscreen preview: light clock typography, capsule hint, circular close button

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
