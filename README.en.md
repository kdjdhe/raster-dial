**English** | [简体中文](README.md)

![License](https://img.shields.io/github/license/kdjdhe/raster-dial?style=flat-square)
![Release](https://img.shields.io/github/v/release/kdjdhe/raster-dial?style=flat-square)

# Raster Dial

A lenticular live wallpaper for Android 9+: tilt the phone and the image transitions between 2–3 layers along a single soft gradient boundary. 5 built-in demo groups, multiple named custom groups, tablet/landscape support, and debouncing.

<p align="center">
  <img src="docs/screenshots/home.webp" width="260" alt="Raster Dial wallpaper on the home screen">
</p>

## Features

- **Gradient line (default)**: one ~18% screen-width soft gradient band between A and B, not a stripe or a hard cut.
- **2D direction**: left/right, up/down, and diagonal tilts all work; the boundary follows the tilt direction.
- **Cycling**: `phase` is unbounded; 2–3 images cycle as `1→2→3→1`.
- **Debouncing**: One Euro Filter + soft dead zone + direction dead zone + a 3 cycles/s phase limit; the half-range angle is multiplied by 1.2 on tablets/landscape.
- **Three transition styles**: gradient line / Venetian blinds (百叶窗: six strips with synchronized boundaries, strips rotate with the axis) / OriginOS-like (类OriginOS: one wide soft gradient sweeping across; instantaneous tilt-to-scene mapping with fixed 100ms committed switches, no intermediate states).
- **Configurable axis**: gradient line 全向 / 仅横向 / 仅竖向; blinds and OriginOS-like 横向 / 竖向.
- **Eye-care strength**: 0–30% persistent luminance attenuation (leftmost = off) that reduces switch flashing; haptic tick per step.
- **5 built-in demo groups + custom**: Yixuan, Pyrois, Miyabi, Vivian, Ye Shunguang.
- **Multiple custom groups**: create, name/rename, delete; 1–6 images per group with pick, rotate 90°, long-press drag reorder, in-editor full-screen preview, and delete.
- **Tablets/foldables**: the runtime surface aspect ratio selects portrait/landscape assets; aspect-fill cropping; display-rotation compensation.
- **Wallpaper only**: no lock-screen takeover, no flashing, no boot service.
- **In-app updates**: Chengdu primary → Gitee secondary → GitHub backup; SHA-256 verification; system installer.
- **6 themes**: Obsidian / Glacier / Moss / Violet / Crimson / Gold; one-tap switch, remembered.
- **System font scaling**: labels stay fully visible from 0.85x to 2.0x; cards, segmented controls and dialogs grow with the text, decorative glyphs keep a fixed dp size.
- **Long-press drag reordering**: lift a card in the picker, 1:1 tracking, spring make-way, per-slot haptic.
- **Search / export & import**: search groups in the picker; bundle a custom group to share, import others'.

### Effect Details

- 2–3 images per group: Pyrois has 2; the other 4 have 3; 5 groups / 14 images in total.
- Images are ranked by colorfulness (1 = most colorful; 2/3 decrease). Scores are in `app/src/main/assets/groups/groups.json` under `scores`.
- Portrait assets are the source images rotated 90° clockwise; landscape assets use the original orientation.
- Half-range angle (tilt needed for one transition): the 0–100% sensitivity slider maps to 34° → 8.6°, default 55% ≈ 20°. A full A→B→A cycle is 2 × the half-range.
- Pose uses the axis-angle `(x, y)` of the relative quaternion, not Euler angles; face-down or near-horizontal poses do not strobe from roll/pitch flips.
- Debounce: soft dead zone 0.015 rad (~0.86°), direction dead zone 0.02 rad (~1.15°), phase limit 3 cycles/s.
- 3-image groups: 0→1 first→second, 1→2 second→third, 2→3 third→first; 2-image groups: 0→1 first→second, 1→2 second→first.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Configuration and Usage](#configuration-and-usage)
- [Common Commands](#common-commands)
- [Testing](#testing)
- [Custom Groups](#custom-groups)
- [Assets and License](#assets-and-license)
- [Tested Lock-Screen Support](#tested-lock-screen-support)
- [Known Limitations and Roadmap](#known-limitations-and-roadmap)
- [License](#license)

## Quick Start

### Prerequisites

- JDK 17 or newer (`sourceCompatibility = VERSION_17` in `app/build.gradle.kts`; AGP 9.2.1)
- Android SDK Platform 36 (`compileSdk = 36`), with `ANDROID_HOME` pointing at the SDK root
- An Android 9+ (API 28) device with USB debugging enabled
- _(Optional)_ Python 3.10+, Pillow, and NumPy — only for regenerating the built-in assets

### Build and Install

```bash
git clone https://github.com/kdjdhe/raster-dial.git
cd raster-dial

# Command-line builds need local.properties; Android Studio creates it automatically.
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Raster Dial → choose a group → **Set as live wallpaper** → confirm in the system live-wallpaper preview.

Or download the [latest release APK](https://github.com/kdjdhe/raster-dial/releases/latest) (debug-signed; sideload only).

## Project Structure

| Path | Purpose |
|---|---|
| `app/src/main/java/com/raster/dial/` | Java sources: wallpaper service, GL preview, sensors/debouncing, group picker, custom groups |
| `app/src/main/AndroidManifest.xml` | App, preview, wallpaper service, and debug receiver declarations |
| `app/src/main/assets/groups/` | Portrait assets 1080×2400 (5 groups; source images rotated 90°) |
| `app/src/main/assets/groups_land/` | Landscape assets 1920×1080 (source images unrotated) |
| `app/src/main/assets/groups/groups.json` | Group catalog, names, colorfulness scores |
| `app/src/main/res/` | Icons, strings, wallpaper metadata |
| `tools/build_groups.py` | Builds portrait assets and `groups.json` |
| `tools/build_landscape.py` | Builds landscape assets |
| `tools/publish_update.py` | Builds `update.json` from an APK |
| `tools/sync_chengdu.sh` | Uploads `update.json` + APK to the Chengdu server |
| `tools/sync_gitee.sh` | Pushes `main` and uploads the APK to a Gitee Release (domestic mirror) |
| `update.json` | App update manifest (versionCode, SHA-256, Chengdu/Gitee/GitHub mirrors) |
| `docs/UPDATE_SOURCE.md` | Chengdu + Gitee/GitHub update source deployment and release guide |
| `docs/SIGNING.md` | debug/release signing, keystore, and update migration |
| `keystore.properties.example` | Release signing example (the real file is gitignored) |
| `docs/COMPATIBILITY.md` | Compatibility analysis for other Android devices and tablets |
| `docs/screenshots/home.webp` `docs/screenshots/landscape.webp` | README screenshots (home / landscape) |

## Configuration and Usage

| Control | Purpose |
|---|---|
| Group | 5 built-in demo groups + multiple custom groups; picker cards; "Chinese name · English" or the custom name |
| Sensitivity | 0–100% continuous slider (live label), half-range 34° → 8.6°, default 55% ≈ 20° |
| Wallpaper debug line | Bottom-left: group, portrait/landscape, rotation, `phase`, segment, segment progress, direction, frame time |
| Invert sweep | Swap the side the gradient sweeps from |
| Select group | Opens the picker; tap a card; custom cards can be edited / named / deleted |
| Manage custom groups | Create groups; 1–6 images each; pick / rotate / drag-reorder / delete / full-screen preview |
| Set as live wallpaper | Opens the system live-wallpaper picker |
| Clear wallpaper | Restores the system default static wallpaper |
| Check for updates | Reads manifests in Chengdu → Gitee → GitHub order; downloads and verifies SHA-256 |

Debug broadcast (the `-n` component is required; implicit broadcasts are not delivered on this EMUI build):

```bash
# Switch group
adb shell am broadcast -n com.raster.dial/.WallDebugReceiver \
  -a com.raster.dial.WALL_DEBUG --es group Miyabi

# Pin phase/direction (45° diagonal at 50%)
adb shell am broadcast -n com.raster.dial/.WallDebugReceiver \
  -a com.raster.dial.WALL_DEBUG --ef progress 0.5 --ef dirX 0.707 --ef dirY 0.707

# Clear pinned values and return to the sensors
adb shell am broadcast -n com.raster.dial/.WallDebugReceiver \
  -a com.raster.dial.WALL_DEBUG --ez clear true
```

> The debug broadcast writes `wall_progress` into SharedPreferences; the wallpaper stays at the pinned value until it is cleared. The in-app "Clear debug override" action is equivalent.

### Tablet and Landscape

The code does not hard-code resolutions. It checks the surface aspect ratio and the current display rotation at runtime:

1. `onSurfaceChanged(width, height)` sets `landscape = width > height`;
2. portrait loads `app/src/main/assets/groups/`; landscape loads `app/src/main/assets/groups_land/`;
3. drawing uses aspect-fill center cropping, never stretching;
4. sensor direction is rotated by `Display.getRotation()` into the current screen coordinates;
5. the half-range angle is multiplied by 1.2 on tablets/landscape.

The same code covers 20:9 phones, 19.5:9, 21:9, 16:10 tablets, and folded/unfolded foldables without a per-resolution asset set. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

![Landscape preview](docs/screenshots/landscape.webp)

## Common Commands

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew test                   # unit-test task (no test sources currently)
./gradlew lint                   # Android Lint
./gradlew assembleRelease        # needs keystore.properties; outputs app-release.apk
python tools/build_groups.py     # rebuild portrait assets + groups.json
python tools/build_landscape.py  # rebuild landscape assets
python tools/publish_update.py --chengdu-base https://updates.example.com/raster-dial
CHENGDU_HOST=user@your-server CHENGDU_KEY=~/.ssh/your-deploy-key tools/sync_chengdu.sh work/raster-dial-0.1.8.apk
```

Asset generation reads the repository's `art` directory by default; you can also point `RASTER_SRC` at your source-art directory:

```bash
RASTER_SRC=/path/to/art python tools/build_groups.py
RASTER_SRC=/path/to/art python tools/build_landscape.py
```

The `sources` field in `groups.json` records the original file for each slot (1/2/3).

## Testing

No test files are present in the repository; `./gradlew test` runs but skips. Build checks:

```bash
./gradlew assembleDebug
./gradlew test
gradle test             # equivalent when Gradle 9.7.1 is installed
./gradlew lint
```

Manual acceptance on a device:

1. Install and set the wallpaper.
2. Tilt left/right, up/down, and diagonally; confirm there is only one gradient boundary.
3. Keep tilting; confirm the `1→2→3→1` cycle.
4. Hold still for 30 seconds; confirm there is no visible jitter.
5. Rotate to landscape or force a tablet size; confirm landscape assets and direction.
6. Create multiple custom groups; name/rename, pick, rotate, reorder, delete, and switch.
7. Switch groups; confirm the wallpaper updates within about 0.5 seconds.

## Custom Groups

- Multiple custom groups, each with a user name; create, rename, delete;
- Picker cards with thumbnails, a current marker, and built-in/custom badges; tap to switch; custom cards have "Edit";
- Editor: name field + save; **全屏预览（传感器）** previews the group being edited without switching the current group; 1–6 images per group (a new group may start empty; at least 1 to "Use this group");
- Each slot: pick (system file picker), rotate 90°, long-press drag reorder (槽位 N renumbers live with position), delete;
- The old single custom group migrates to a group named 自定义; a fresh install seeds one "自定义" group with 2 Pyrois images;
- Files live in the app's private `files` directory under `custom`: `groups.json` plus each image's `m_*.webp` (master) and `s_*.webp` (rotated display);
- Imports are EXIF-corrected; the master's longest edge is 3000 px and the display's longest edge is 1600 px.

## Assets and License

- **Code**: MIT; see [LICENSE](LICENSE).
- **Bundled images**: `app/src/main/assets/groups/` and `app/src/main/assets/groups_land/` come from the official miHoYo / 米游社 (Miyoushe) Zenless Zone Zero "绳网情报站" wiki (https://baike.mihoyo.com/zzz/wiki/channel/map/2/43); copyright miHoYo / HoYoverse. They are **not covered by the MIT License**; the public repository is a personal demonstration only and grants no redistribution or commercial rights. The app icon, thumbnail, and README screenshots also contain official artwork. If you redistribute or use the project commercially, remove/replace these images or obtain permission; see [NOTICE](NOTICE).
- Regenerate assets: put the originals in the `art` directory or set `RASTER_SRC`, then run `tools/build_groups.py` and `tools/build_landscape.py`.

## Tested Lock-Screen Support

The app does not implement a lock screen itself. Whether the system live-wallpaper preview offers "set as lock screen / set for both" depends on each vendor's wallpaper manager and keyguard; AOSP provides no unified guarantee. Observed on real devices (2026-11-03):

| Device | OS | Android | Settable as lock screen | Notes |
|---|---|---|---|---|
| Xiaomi 15 Pro | HyperOS 3 (OS3.0.309.0.WOBCNXM.C11) | 16 | ✅ | Preview offers wallpaper / lock screen / both |
| vivo X200s | OriginOS 6 | 16 | ✅ | Same as above |
| NZONE | EMUI 11 | 10 | ❌ | Wallpaper only; live wallpaper cannot be set on the lock screen |
| HUAWEI P30 Pro | HarmonyOS 4.0.0.112 | 10 | ❌ | Wallpaper only; live wallpaper cannot be set on the lock screen |
| Honor Pad 9 Pro | MagicOS 8 | 14 | ❌ | Same as above |
| HONOR 300 Pro | MagicOS 10.0 (10.0.0.175 C00E170R201P1) | 16 | ❌ | Wallpaper only; live wallpaper cannot be set on the lock screen |

Pattern: HyperOS / OriginOS integrate live wallpapers into the keyguard and expose the lock-screen entry; EMUI / HarmonyOS / MagicOS lock-screen wallpapers go through their own static pipelines.

> **Please help test on your device**: install the app, try setting the live wallpaper (and the
> lock screen where the system offers it), and submit device model / OS and version / Android
> version / whether the lock screen is settable / observed behavior via
> [GitHub Issues](https://github.com/kdjdhe/raster-dial/issues). Results will be added to the table.

## Known Limitations and Roadmap

- **Home-screen live wallpaper by design**; whether it can also be set as the lock-screen wallpaper depends on the vendor's keyguard implementation — see [Tested Lock-Screen Support](#tested-lock-screen-support).
- The wallpaper uses a Canvas `saveLayer` + `LinearGradient` renderer; tablets/4K panels could use EGL + GLES2 to reduce GPU work.
- While visible, it redraws every 16 ms; event-driven + dirty rendering is planned to approach zero redraws when idle.
- Memory headroom: downscale for the target screen, RGB_565, and a 1600 px display edge for custom images.
- A real tablet/foldable smoke test is on the roadmap.
- The official artwork carries the IP risk described above.
- The update APK must be signed with the same key as the installed app; current builds are debug-signed, and the Chengdu server must mirror the exact APK. Signing steps: [docs/SIGNING.md](docs/SIGNING.md).

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Documentation Maintenance

Update this README when:

- a built-in group is added/removed, or `groups.json` fields change;
- the package name, `minSdk`/`targetSdk`, or version changes;
- the debug broadcast action or component changes;
- the asset scripts or the `RASTER_SRC` convention change;
- permissions, the wallpaper service, or an app entry point is added/removed.

## License

MIT License; see [LICENSE](LICENSE). The bundled official artwork is not covered by MIT; see [NOTICE](NOTICE).
