# Cinematic for Stremio

Android TV and tablet packaging for the custom Stremio Web interface.

Version 0.8 embeds the MIT-licensed Stremio Stream Server and uses AndroidX
Media3 ExoPlayer for reliable in-app playback on Android TV and tablets. See
`THIRD_PARTY_NOTICES.md` for attribution.

## Build

1. Build `../stremio-web` with `corepack pnpm run build`.
2. Run `npm install` in this directory.
3. Run `npm run build:debug`.

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
