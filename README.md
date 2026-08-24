# Cinematic Stremio TV

A remote-friendly Android TV and tablet shell for Stremio Web, with a brighter cinematic interface and a built-in compatibility player.

## Install

Download the APK from the latest GitHub Release. Starting with v0.7, the app checks this repository for newer releases and offers to download them inside the app. Android will still show its normal installation confirmation.

## What v1.0 adds

- Native TV playback with automatic Media3 to LibVLC fallback.
- D-pad play/pause, 10-second seek and a focusable timeline.
- Audio tracks, subtitles, playback speed and source switching controls.
- Local resume playback with a clear continue/from-start prompt.
- Back closes the controls first, then returns to the title instead of exiting the app.
- Built-in updater backed by GitHub Releases.
- Native-only Stremio desktop/service banners are hidden.
- Playback links are handed only to an installed Stremio package.
- Back and profile navigation are remote-friendly.

This project does not provide, host, index, or sell media. It is an independent UI shell and is not affiliated with Stremio, Netflix, or HOT.

## Build

1. In `stremio-web`, install with pnpm and run `pnpm build`.
2. In `stremio-tv`, run `npm ci` and `npm run sync`.
3. In `stremio-tv/android`, run `./gradlew assembleDebug`.

Licensed under GPL-2.0, following the upstream Stremio Web license.
