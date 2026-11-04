# Rokid NewPipe 0.28.8 - 2026-06-28

Rokid NewPipe is a glasses-focused fork of NewPipe for Rokid devices. This build
keeps the NewPipe streaming core and reshapes the main experience around a
`480x640`, no-touch HUD.

## Highlights

- New green launcher icon and adaptive icon background.
- Fresh screenshots captured from real Rokid glasses.
- Rokid-first home, search, video detail, and player controls.
- One-axis navigation: swipe moves focus, tap activates, Back exits.
- Directional debounce for physical Rokid swipe events that arrive as paired
  key events.
- Black, high-contrast HUD surfaces with outline focus for AR readability.
- README updated with R08 Access Bridge setup notes.

## R08 Access Bridge

R08 Access Bridge `v1.4.7` now includes Rokid NewPipe compatibility. When this
app is active, the bridge sends R08 ring swipes and taps into NewPipe's own
single-axis navigator.

Get it here:
https://github.com/Anezium/R08-Access-Bridge/releases/latest

## Install

1. Install `Rokid-NewPipe-v0.28.8-rokid-20260628.apk` on the glasses.
2. Install or update R08 Access Bridge to `v1.4.7` if you want R08 ring control.
3. Enable the R08 Access Bridge Accessibility Service on the glasses.
4. Pair the R08 ring in Stable mode.
5. Launch Rokid NewPipe.

## Notes

This is not the upstream NewPipe distribution. It is a focused Rokid glasses
build with package name `com.anezium.rokid.newpipe`.

The APK attached to this release is signed with the standard Android debug
certificate for installability. It will not update over the official NewPipe app
and should be treated as a separate community build.
