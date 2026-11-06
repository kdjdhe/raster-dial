# Changelog

## [0.1.14] - 2026-11-03

### Added

- 设为光栅壁纸 now verifies the attempt: if the system picker never opens (HyperOS
  without the 动态壁纸 permission silently drops the intent) a dialog explains the
  permission and offers 去授权 (MIUI permission editor, falling back to app details);
  after returning from the picker the app checks the active wallpaper component and
  reports success or the same hint.
- Wallpaper card now shows the overwrite warning under 设为光栅壁纸: applying the live
  wallpaper replaces the existing one, restoring the system default in Settings almost
  certainly loses the original wallpaper, and lock-screen support is device-dependent
  (README).
- Main screen cards (光栅组 / 效果 / 预览 / 壁纸) can be reordered by long-press drag with
  spring make-way and a haptic tick per crossing. The order is persisted. The 光栅组 card
  only needs a long press because the whole card is already interactive. The swap
  threshold uses the dragged card's leading edge (half of the next/previous card), so a
  tall card grabbed near its top only needs a short drag to push its neighbour down.
- Segmented controls can be dragged: long-press the selected segment (约 220ms), the
  thumb scales up slightly and follows the finger 1:1; crossing a neighbour's midpoint
  switches 过渡线 / 百叶窗 / 类OriginOS or the axis option live, with a haptic tick per
  crossing, then springs to the slot on release. A quick horizontal swipe also enters
  drag mode, so the control stays responsive; vertical movement still scrolls.
- 百叶窗 / 类OriginOS axis options gain 自动: the dominant tilt axis is chosen per gesture,
  with 1.15x hysteresis so diagonal movement does not flip back and forth. Existing
  axis_h settings migrate (0 横向 / 1 竖向).
- Settings → 外观 gains a 卡片圆角 slider (0–36dp, default 22) that applies to cards,
  grouped settings rows, dialogs and picker cards. The Settings cards re-round live
  while the slider is dragged; Main rebuilds on return without replaying entrance.

### Changed

- Sensitivity slider haptic feedback now scales with the value: amplitude 20–220 and
  6–12ms, so low sensitivity ticks are softer and high sensitivity ticks stronger.
  Adds the VIBRATE permission (normal, no prompt).
- versionCode 15 / versionName 0.1.14.

## [0.1.13] - 2026-11-03

### Fixed

- Layout now respects the system font size; labels no longer clip when the system
  font is enlarged (checked at 1.5x and 2.0x on the emulator):
  - Group picker cards size to their content, then equalize to the tallest card in
    the grid. Names, meta lines and the 编辑 chip stay visible, and drag sorting still
    has a uniform pitch because every card is the same height.
  - Card names allow 3 lines at >=1.2x and 4 lines at >=1.6x, ending with an ellipsis
    instead of hard clipping.
  - Segmented controls use a minimum row height instead of a fixed 34dp, so 过渡线 /
    百叶窗 / 类OriginOS and the axis labels never clip.
  - Decorative glyphs (‹ × ⚙ ✓ › ＋) keep a fixed dp size so they cannot overflow
    their circular buttons; the full-screen preview clock is dp-sized and its hint
    pill is width-capped so it wraps.
  - At font scale >=1.5 the picker search box takes a full row with 导入 / 分享 below
    it, so the hint and query are never squeezed by the buttons.
- versionCode 14 / versionName 0.1.13.

## [0.1.12] - 2026-11-03

### Changed

- Main screen preview: removed the fixed-phase shortcuts (第1张 / 1→2 50% / 第2张 /
  2→3 50% / 第3张 / 斜向 45°); only 全屏预览（传感器） remains.
- Custom group editor now has its own 全屏预览（传感器） button, previewing the group
  being edited without switching the current group.
- Slot ordering is now long-press drag with spring make-way, replacing the ↑/↓
  buttons: 360ms hold lifts the card (one continuous gesture), neighbours spring to
  their new slot, 槽位 N labels renumber live with the order, and the order is saved
  when the card is dropped.
- Dialogs and text fields now use one shared design (`Ui.dialog` / `Ui.textField`):
  the new-group naming sheet, delete-group confirm, restore-wallpaper confirm,
  update prompt and unknown-sources prompt all render as the app's layered dark card
  instead of the system AlertDialog.
- versionCode 13 / versionName 0.1.12.

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
- Sensitivity is now a continuous slider (灵敏度 0–100%, live label) instead of the
  three presets 高/中/低. Mapping: 0–100% <-> half-range angle 0.60–0.15 rad
  (about 34° down to 8.6°); the previous presets (0.24 / 0.35 / 0.52 rad) are all
  reachable and the default lands at 55% (~20°). Haptic tick per 1% step.
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
