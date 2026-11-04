# Reddit Thread Draft

## Title

Rokid NewPipe: a NewPipe fork rebuilt for Rokid glasses, now with R08 ring support

## Body

I just published a Rokid-focused NewPipe build:

https://github.com/Anezium/Rokid-NewPipe/releases/tag/rokid-v0.28.8-20260628

This is a fork of NewPipe adapted for Rokid glasses. The goal is not to make a
generic phone app smaller; it is to make the core video workflow usable directly
on a `480x640` no-touch HUD.

What changed:

- Home, search, video detail, and player screens are adjusted for the glasses.
- Navigation uses the real glasses model: swipe forward/back to move focus,
  tap/select to activate, Back to exit.
- Search opens into a Rokid-friendly on-screen keyboard.
- Player controls are reachable from a compact action rail: pause/play, skip,
  quality, speed, subtitles, close, and exit.
- The UI uses black surfaces, high-contrast text, and green outline focus so it
  stays readable without blocking too much of the AR view.
- Swipe debounce handles the Rokid quirk where one physical swipe can arrive as
  paired Android key events.

R08 Access Bridge was also updated for this app:

https://github.com/Anezium/R08-Access-Bridge/releases/latest

Use R08 Access Bridge `v1.4.7` or newer. When Rokid NewPipe is active, the bridge
forwards ring swipes/taps into NewPipe's own one-axis navigator:

- Ring swipe forward/back moves focus.
- Single tap activates the focused item.
- Double tap goes Back.
- Stable mode is recommended.

Install order:

1. Install Rokid NewPipe on the glasses.
2. Install/update R08 Access Bridge to `v1.4.7`.
3. Enable the R08 Access Bridge Accessibility Service.
4. Pair the R08 ring in Stable mode.
5. Open Rokid NewPipe.

This is not an upstream NewPipe release and it uses its own package name:
`com.anezium.rokid.newpipe`.

The APK is signed as a separate community build, so it will not update over the
official NewPipe app.

Screenshots in the README were captured from real Rokid glasses:

https://github.com/Anezium/Rokid-NewPipe#screenshots-from-the-glasses
