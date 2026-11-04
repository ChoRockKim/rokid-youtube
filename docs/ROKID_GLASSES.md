# RokidPipe

RokidPipe (formerly "Rokid NewPipe") is based on TeamNewPipe/NewPipe and keeps the upstream Java/Kotlin package layout so future merges stay practical. The installed Android package is `com.anezium.rokid.newpipe`; it predates the rename and is kept for compatibility with the released R08 Access Bridge and existing installs.

## Glasses UX Baseline

- Primary target: Rokid glasses at 480 x 640 portrait.
- Rendering: black root, high-contrast text, outline controls, minimal filled surfaces.
- Input: one swipe axis plus tap/back. Directional aliases are debounced so `DPAD_RIGHT` followed by `DPAD_DOWN` counts as one move.
- Search: the Rokid keyboard is a one-axis carousel. Swipe cycles keys, tap inserts/activates, back closes it.
- Player: swipe left/right navigates controls. Seek-by-swipe is disabled so the same gestures can reach every button.
- Player rail: `Play/Pause`, `Full`, quality, speed, subtitles, and close are exposed as focusable actions with readable labels.
- Detail pages: comments are hidden in Rokid mode; use `Full` to keep video controls first.
- Offline recovery: the error panel exposes a focusable Wi-Fi settings button when Android reports no network.

## Local Dev Loop

```powershell
.\scripts\rokid-dev-loop.ps1
```

With a connected device:

```powershell
.\scripts\rokid-dev-loop.ps1 -Install -SmokeKeys
```

With an explicit ADB serial:

```powershell
.\scripts\rokid-dev-loop.ps1 -Serial <serial> -Install -SmokeKeys
```

R08/Rokid regression smoke:

```powershell
.\scripts\rokid-regression.ps1 -GlassesSerial <serial> -RunPlayer
```

The regression script builds/installs by default, captures launch/search/Wi-Fi/player screenshots, dumps the accessibility tree, and writes logs under `qa/artifacts/`.

## GitHub Workflow

`.github/workflows/rokid-glasses.yml` builds the debug APK, runs JVM tests, and uploads the APK artifact. A manual dispatch can run connected Android checks when the runner has a device attached.
