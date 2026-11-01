# Changelog

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
- Bundled Zenless Zone Zero fan art is not MIT-licensed; replace it before redistribution
