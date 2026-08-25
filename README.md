# Cinematic TV 2.0

A watch-first Android TV and tablet experience: cinematic branding, a native
player, large remote controls, saved progress, language controls, automatic
updates, high-contrast focus and source-first movie/episode screens.

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

## What v1.1 improves

- Touching the video on tablets opens the full player overlay.
- D-pad timeline changes now perform a real seek instead of snapping back.
- The focused TV button stays selected after an action.
- First-run Hebrew/English choice is saved for each device.
- Preferred language is also applied automatically to matching VLC subtitle and audio tracks.
- Player actions scroll horizontally on smaller tablet screens.

## What v1.2 stabilizes

- Back from playback reliably returns to the same source screen and restores focus.
- Back on the home screen asks for confirmation instead of closing by accident.
- Subtitles are disabled by default in both Media3 and LibVLC and remain opt-in.
- Focus is unmistakable on projectors: white border, red highlight and strong glow.
- Native player buttons use the same high-contrast focused state.
- Native-only Stremio desktop/service banners are hidden.
- Playback links are handed only to an installed Stremio package.
- Back and profile navigation are remote-friendly.

## What v1.3 redesigns

- Netflix-inspired cinematic overlay without copying Netflix branding.
- Large centered play/pause and 10-second seek controls.
- Clean top title bar, close action, timeline and separated secondary controls.
- Combined audio and subtitle menu, playback speed and source switching.
- App brightness controls and accidental-touch control locking.
- Loading messages no longer overlap the primary playback actions.

Version 1.3.1 verifies that an update download completed and contains a valid
Android package before opening the installer, preventing partial-download parse errors.

## What v1.4 polishes

- A brighter blue-black canvas that stays cinematic without crushing detail.
- Warm, highly readable typography and clearer hierarchy throughout the app.
- Consistent glass surfaces for settings, search, add-ons, dialogs and streams.
- Strong projector-friendly focus states and larger remote/touch targets.
- Responsive TV and tablet layouts with calmer spacing and cleaner cards.
- A documented visual system in `stremio-web/DESIGN.md` for future screens.

This project does not provide, host, index, or sell media. It is an independent UI shell and is not affiliated with Stremio, Netflix, or HOT.

## Build

1. In `stremio-web`, install with pnpm and run `pnpm build`.
2. In `stremio-tv`, run `npm ci` and `npm run sync`.
3. In `stremio-tv/android`, run `./gradlew assembleDebug`.

Licensed under GPL-2.0, following the upstream Stremio Web license.

## What v2.3 improves

- Slow sources are no longer treated as player failure after eight seconds.
- Media3 switches to VLC automatically only for evidenced decoder/container incompatibility.
- Slow and failed sources expose clear wait, compatibility and source-recovery actions.
- Android TV Back now follows the real React navigation history instead of jumping to Home.
- Update APKs must match both the NUVYRO package and the currently installed signing identity.
- NUVYRO now uses its own electric-crimson/violet visual identity instead of Netflix red.
