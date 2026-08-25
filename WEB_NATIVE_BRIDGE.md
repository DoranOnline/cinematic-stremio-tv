# NUVYRO Web/Native Bridge

## Compatibility policy

The Android package and the legacy JavaScript object name are update-compatibility surfaces.
`CinematicAndroid` remains available until a versioned NUVYRO bridge has shipped and survived
at least one upgrade cycle. Internal legacy naming must not appear in product UI.

## Current V1 contract

### Web to Android

| Method | Arguments | Result | Purpose |
| --- | --- | --- | --- |
| `openNativePlayer` | `streamUrl, videoId, title` | boolean | Open a validated HTTP(S) stream in native playback. |
| `isStreamingServerReady` | none | boolean | Report embedded Stremio server readiness. |
| `getStreamingServerUrl` | none | string | Return embedded server base URL or an empty string. |

### Android to Web

| Event | Payload | Purpose |
| --- | --- | --- |
| `nuvyro-playback-ended` | none | Legacy signal used to select and open the next episode. |

### Focus return

The clicked stream receives `data-cinematic-return-focus`. MainActivity searches for that
element when playback closes. This is temporary compatibility behavior, not the V3 model.

## Target V3 contract

V3 uses JSON envelopes:

```json
{
  "version": 3,
  "command": "OPEN_PLAYER",
  "requestId": "uuid",
  "session": {
    "contentId": "...",
    "videoId": "...",
    "type": "series",
    "title": "...",
    "season": 1,
    "episode": 2,
    "positionMs": 0,
    "sources": [],
    "nextEpisode": null,
    "previousEpisode": null
  }
}
```

Commands: `OPEN_PLAYER`, `SWITCH_SOURCE`, `SWITCH_EPISODE`, `CLOSE_PLAYER`,
`REQUEST_PLAYER_STATE`, `WEB_BACK_RESULT`.

Events: `PLAYER_READY`, `PLAYER_STATE_CHANGED`, `PLAYER_ERROR`, `PLAYER_CLOSED`,
`PLAYBACK_PROGRESS`, `PLAYBACK_ENDED`, `SOURCE_CHANGED`, `EPISODE_CHANGED`,
`NEXT_EPISODE_REQUEST`, `RETURN_FOCUS`, `NATIVE_BACK_REQUEST`.

Every event includes `version`, `event`, `sessionId`, `requestId` where applicable, and a
typed payload. Unknown fields are ignored. Unsupported major versions fail explicitly and
leave the V1 adapter available.

## Security rules

- Accept only allow-listed commands and HTTP(S) playback URLs.
- Never pass passwords, Stremio auth tokens, signing data or raw private headers.
- Validate payload size and required fields before launching native components.
- Do not expose general Android intents, filesystem APIs or arbitrary Java reflection.
- Redact stream query secrets from logs and analytics.

