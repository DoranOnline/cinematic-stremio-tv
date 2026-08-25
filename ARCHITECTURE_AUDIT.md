# NUVYRO V3 Architecture Audit

Date: 2026-08-26

## 1. Current system map

NUVYRO is currently two applications shipped as one APK:

```text
Stremio Core Web (account, catalogs, library, add-ons, metadata)
  -> React Stremio Web fork (NUVYRO CSS, details, episodes, ranked sources)
  -> Capacitor assets bundled into the APK
  -> MainActivity / Android WebView
  -> CinematicAndroid JavaScript interface
  -> NativePlayerActivity
       -> embedded Stremio streaming server for local/proxied streams
       -> Media3/ExoPlayer first
       -> LibVLC fallback
  -> activity result / DOM CustomEvent back to React
```

The package identity is `il.cinematic.stremio`. The release workflow restores a stable
keystore from GitHub Actions secrets, builds the web bundle, syncs it through Capacitor,
runs JVM tests, and publishes a debug APK signed with that stable update key.

### Playback data flow

1. Stremio Core returns stream models and deep links.
2. `StreamsList` parses and ranks the models.
3. `Stream.js` extracts `deepLinks.externalPlayer.streaming`.
4. The WebView invokes `CinematicAndroid.openNativePlayer(url, videoId, title)`.
5. `MainActivity` validates the URL scheme and launches `NativePlayerActivity`.
6. The activity tries Media3, then currently falls back to VLC on an error or an
   eight-second first-frame timer.
7. Progress is stored in Android `SharedPreferences`, keyed by video ID.
8. On natural completion, the activity result causes MainActivity to dispatch
   `nuvyro-playback-ended` into the WebView.
9. `MetaDetails` advances to the next item in the metadata video array and asks the
   source list to auto-open its first ranked source.

### Navigation data flow

Web routes are React Router cached views. Spatial movement is split between Stremio's
gamepad navigation hooks, a global `window.navigate` function, Android key interception,
and CSS focus selectors. Android consumes directional keys and injects calls to
`window.navigate`. Back is handled separately in native code and currently forces most
non-home hash routes to `#/`, bypassing the React Router history.

### Web/native communication flow

The current bridge exposes three Java methods under the legacy compatibility name
`CinematicAndroid`: open player, query embedded-server readiness, and get its URL. Native
to web communication is a single untyped DOM event. Focus return uses a mutable DOM data
attribute. This works, but it is implicit and cannot carry a complete playback session.

## 2. Top 15 problems

### BLOCKER

1. **Player god-class:** `NativePlayerActivity` owns rendering, engine lifecycle, tracks,
   preferences, controls, errors, fallback and remote input. Changes have a high regression
   radius.
2. **Blind eight-second fallback:** slow source startup is treated as engine failure. It can
   start two expensive playback engines and makes torrent/proxy startup unpredictable.
3. **Broken native Back model:** non-home WebView routes are forced directly to Home instead
   of reversing the actual user journey.
4. **Insufficient session contract:** native receives only URL, video ID and title, so it
   cannot implement in-player source switching, episode drawers or provider continuity.

### HIGH

5. **Engine behavior is not abstracted:** Media3 and VLC expose different track and error
   behavior directly to the Activity.
6. **Autoplay is best-effort DOM orchestration:** a DOM event plus `sessionStorage` and a
   timed auto-click can lose state on rerender, source delay or season boundaries.
7. **Source switching exits playback:** the Source button closes the activity rather than
   preserving timestamp and offering alternatives in-player.
8. **Error taxonomy is absent:** decoder, HTTP, network, source resolution and torrent-not-
   ready conditions collapse into generic text or a black/loading screen.
9. **Focus has multiple owners:** Android, browser spatial navigation, gamepad hooks and CSS
   can disagree, which explains air-mouse/D-pad ambiguity.
10. **Track support differs by engine:** native menus currently provide practical selection
    only for VLC, while Media3 tells the user to use Stremio source behavior.
11. **Updater validates APK shape, not signer identity:** a parseable package is accepted;
    the downloaded certificate/package identity is not explicitly compared before install.

### MEDIUM

12. **UI tokens are partial:** the large `cinematic-tv.less` overlay centralizes some values
    but still relies on upstream class-name substring selectors and raw values.
13. **Resume is device-local only:** progress is robust locally but is not represented in a
    shared playback session or synchronized back to the Stremio model.
14. **Performance is unmeasured:** there are no first-render, source-resolution, first-frame,
    dropped-frame or source-switch metrics.

### LOW

15. **Legacy internal naming leaks into maintenance:** compatibility names are intentionally
    preserved, but they need documentation so future cleanup does not break updates.

## 3. V3 target architecture

```text
Web application
  NuvyroShell
  NavigationCoordinator + FocusMemory
  Home / Details / Episodes / Sources
  SourceParser + SourceRanker
  PlaybackBridgeClient
          |
          | versioned JSON commands and events
          v
Android application
  MainActivity / WebViewHost
  WebNativeBridge
  PlaybackSessionRepository
  NativePlayerActivity (view and lifecycle only)
  PlayerController + PlayerStateMachine
  PlayerControlsController
  ResumeManager / TrackController / AutoplayController
  PlaybackEngine
      Media3Engine
      VlcEngine
  EmbeddedStreamingServer
  AppUpdateManager
```

`PlaybackSession` is the single source of truth. `PlaybackEngine` hides engine-specific
mechanics. `PlayerController` owns state transitions and fallback policy. The Activity
renders state and forwards lifecycle/input. The web UI owns catalog navigation and sends a
complete, versioned session to native.

## 4. Migration plan

1. Add contracts and value objects with compatibility adapters. Keep the current three-
   argument bridge method operational.
2. Introduce a state machine and engine interface behind the existing Activity without
   changing its UI.
3. Extract Media3 and VLC implementations one at a time and validate each against legal test
   streams.
4. Replace timer fallback with evidence-based error categories and a user-visible slow-source
   state.
5. Expand the web/native session payload to include structured sources and episode context.
6. Move source switching, tracks, resume and autoplay onto the controller/session model.
7. Add deterministic native-to-web Back handling and focus restoration before visual changes.
8. Rebuild the five core screens on design-system components, one screen per verified change.
9. Harden updater and WebView, profile performance, then run the physical-device matrix.

Every step must retain the legacy bridge until the new contract is proven in a released build.
Package ID, signing configuration, Stremio account/core transport and add-on semantics remain.

## 5. File-by-file plan

### Remain

- `stremio-web/src/core/**`: Stremio account, catalog and add-on backend.
- `EmbeddedStreamingServer.java` and bundled native libraries: retain behind a new service
  interface; do not rewrite without upstream compatibility tests.
- `AppUpdateManager.java`: retain identity and release source, then harden validation.
- `PlaybackLinkPolicy.java`, `RemoteKeyGate.java`, `VersionComparator.java`: small testable
  utilities.
- Package ID, manifest launcher identity and stable signing workflow.

### Refactor

- `MainActivity.java`: become WebView host, Back coordinator and bridge registration only.
- `NativePlayerActivity.java`: become a lifecycle/view adapter.
- `MetaDetails.js`, `StreamsList.js`, `Stream.js`: emit structured sessions rather than DOM
  timing flags.
- `Routes.tsx` and gamepad navigation hooks: use a screen-level navigation contract and focus
  memory.
- `cinematic-tv.less`: migrate from upstream substring overrides to NUVYRO components/tokens.
- `AppUpdateManager.java`: add package/signer comparison and playback-safe prompting.

### Split

- Player engines, controller, controls, tracks, subtitles, resume, autoplay, errors and
  telemetry out of `NativePlayerActivity`.
- Source parsing/ranking/preferences out of route components.
- TV navigation/focus restoration out of individual screen effects.

### Replace

- Eight-second fallback timer with typed failure policy.
- `window.location.hash='#/'` Back shortcut with explicit React history/bridge acknowledgement.
- `nuvyro-playback-ended` plus `sessionStorage` auto-click with session-based events.
- Player Source button that calls `finish()` with an in-player source drawer.

### Create

- Android: `PlaybackSession`, `PlayerState`, `PlayerStateMachine`, `PlaybackEngine`,
  `Media3Engine`, `VlcEngine`, `PlayerController`, `PlayerError`, `ResumeManager`,
  `TrackController`, `AutoplayController`, `WebNativeBridgeContract`.
- Web: `design/tokens`, `PlaybackBridgeClient`, `NavigationCoordinator`, `FocusMemory`,
  `SourceParser`, `SourceRanker`, and NUVYRO-owned core-screen components.
- Documentation: this audit and `WEB_NATIVE_BRIDGE.md`.

## 6. Player plan

Both engines implement one contract: prepare, play, pause, seek, position, duration, buffer,
speed, audio tracks, subtitle tracks, selection, subtitle disable, state observation and
release. Engine errors are mapped to NUVYRO categories. A controller attempts Media3 first,
switches only on evidence of incompatibility/failure, and retains the previous working source
during source changes. UI observes `PlayerState`; it never inspects engine instances.

## 7. Navigation plan

Each screen declares initial focus, directional neighbors, row boundaries, modal behavior and
Back destination. `FocusMemory` stores a stable semantic key (catalog, row, item, episode or
source), not a transient DOM node. Android sends remote input only when native playback owns
the screen; otherwise the Web navigation coordinator owns it. Back closes the deepest layer
first: menu, controls, player, sources, episode/details, origin card, Home, exit confirmation.

## 8. Design system plan

Initial tokens:

- Background: `#090A0F`; elevated surface: `rgba(25, 24, 34, .88)`.
- Primary accent: `#F02D62`; secondary accent: `#7A5CFF`.
- Text: `#F6F1EA`; secondary text: `#B9B4BE`.
- Focus glow: `rgba(240, 45, 98, .48)` with a 1.05 default card scale.
- Spacing: 4, 8, 12, 16, 24, 32, 48, 64.
- Radius: 8, 12, 18, 26, pill.
- Motion: 120ms immediate, 160ms focus, 240ms panel, with reduced-motion mode.
- TV type minimums: caption 18sp-equivalent, body 22, rail 26, H1 40, display 56+.

Components: AppShell, TopNav, Hero, Rail, MediaCard, FocusRing, DetailsHero, EpisodeCard,
SourceCard, StatusPanel, PlayerOverlay, ControlButton, Timeline, Drawer and Dialog. Components
consume tokens; screens do not invent visual constants.

## 9. Test plan

- JVM: state transitions, sessions, resume thresholds, error mapping, URL policy, versions and
  update identity checks.
- Jest: source parsing/ranking, next-episode selection, bridge payloads and focus keys.
- Web integration: Home -> Details -> Episode -> Source and exact Back reversal.
- Android instrumentation: bridge validation, lifecycle persistence and remote keys.
- Emulator journey: install/upgrade without clearing data; D-pad-only journey; legal MP4/HLS
  playback; pause/seek/resume; error and slow-source UI; screenshot and layout capture.
- Physical devices: projector/HOT box, low-cost TV box and Galaxy Tab A9 remain required human
  validation zones. They must never be claimed from emulator evidence.
- Release: clean CI build, signer fingerprint comparison, APK checksum, install-over-previous,
  cold launch and update check.

## 10. First implementation milestone

The smallest safe milestone is **contracts before behavior**:

1. Add a tested immutable `PlaybackSession` model.
2. Add explicit `PlayerState` values.
3. Centralize bridge names, methods and events in `WebNativeBridgeContract`.
4. Document the versioned bridge payload and compatibility policy.
5. Compile and test without changing current playback/focus behavior.

This creates seams for the player split while keeping the released 2.2.2 journey intact.

