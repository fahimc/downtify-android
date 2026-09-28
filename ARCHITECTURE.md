# Architecture

## Downtify API integration

The initial handshake calls `GET /api/server/info`, validates `product == Downtify` and rejects unknown newer API versions. Pairing uses `POST /api/auth/pair` with the server's one-time pairing code, device model and `platform: android`. Authenticated requests use the returned bearer device token. That is the server-supported device credential, so the app never asks for the account password.

For older Downtify 3.1.x servers, `/api/server/info` does not exist and the single-page web app returns HTML for that path. The app probes `GET /api/version`; if the server is older than 3.2.0, it loads the server's own root page in the WebView so the existing home UI and same-origin sign-in work. Native pairing/playback/download features remain available only on servers exposing the 3.2 mobile contract.

The Vue API adapter uses the user-configured server origin, and its Axios requests add the native bearer token. The upstream mobile API provides durable track IDs, incremental sync, playlist and likes endpoints, range streaming, covers and WebSocket events. Since the upstream `/tracks` rows use filenames, the bridge resolves a row's ID through `/api/v1/library?since=0` and caches the filename-to-ID mapping for later playback and downloads.

## Web/native bridge

`window.DowntifyNative` exposes server URL/token access, track play/pause/seek, download/status calls and album/playlist download entry points. The upstream player delegates track transport to Media3 when the bridge exists. `web/frontend/src/config.js` and `src/model/api.js` are the only general API adapter changes; `src/model/player.js` hands off playback. The complete Vue source and npm lockfile are retained in `web/frontend`.

## Playback

`PlaybackService` is an AndroidX Media3 `MediaSessionService` with ExoPlayer. Its session provides Android media notification, lock-screen, headset and Bluetooth transport controls. The player requests audio focus and pauses for noisy output changes. `PlaybackController` supplies the paired bearer token to the HTTP data source. Native play checks the server-scoped Room row first and opens its app-private file when present; otherwise it streams the server track endpoint. Playback continues when the activity is not visible.

## Downloads and offline database

WorkManager downloads into `files/music/<server_id>/` and stores metadata in Room. A `.part` file is retained across interruption and resumed with `Range: bytes=<offset>-`; unsuccessful network attempts retry with backoff. Device token and server settings use AndroidX encrypted shared preferences backed by Android Keystore. Room's composite `(serverId, trackId)` key keeps independent servers' libraries separate. The row includes title, artist, album, artwork URL, duration, file path, status, downloaded time and playback position. Artwork is not yet persisted locally, and playback position is reserved in the schema but not yet updated during playback.

## Online/offline switching

The bundled web UI is loaded from APK assets and can render while offline. Android's Offline action browses retained local metadata, and local files remain in app storage when the server is unreachable or its token is revoked. A remote play request prefers a matching local track. Changing to a different server identity scopes future lookup and downloads to the new server ID.

## Current limits

This first implementation is a usable foundation, not yet the complete acceptance target. Track and collection offline actions use the mobile IDs resolved from `/api/v1/library`; album and playlist downloads queue their supplied tracks individually. Incremental library sync, cached artwork, download progress surfaces, playback-position reporting, and a full native Offline page remain follow-up work. Release signing uses the debug key until a private production signing key is configured.
