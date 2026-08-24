# Cinematic for Stremio

Android TV and tablet packaging for the custom Stremio Web interface.

Version 1.3 embeds the MIT-licensed Stremio Stream Server and starts playback
with AndroidX Media3. If a TV decoder or format fails, it automatically retries
with LibVLC compatibility playback. Loading and failures are shown on screen,
with an optional external-player escape hatch instead of a silent black screen.
See `THIRD_PARTY_NOTICES.md` for attribution.

The TV player includes a cinematic full-screen overlay with large D-pad and
touch targets, play/pause, 10-second seek, timeline, app brightness controls,
control locking, combined audio/subtitle selection, playback speed, source
switching, and local resume progress keyed by movie or episode.

Version 1.3.1 validates completed update downloads before launching Android's
installer, preventing partial files from producing package parsing errors.

Version 1.4 introduces a cohesive premium visual system across TV and tablet:
a brighter blue-black canvas, warmer typography, polished glass surfaces,
clearer focus states, larger remote/touch targets, and redesigned discovery,
details, streams, settings, add-ons, search, dialogs, and feedback states.

## Build

1. Build `../stremio-web` with `corepack pnpm run build`.
2. Run `npm install` in this directory.
3. Run `npm run build:debug`.

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
