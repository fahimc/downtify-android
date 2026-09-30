# Architecture

## Downtify API integration

The initial handshake calls `GET /api/server/info`, validates `product == Downtify` and rejects unknown newer API versions. Pairing uses `POST /api/auth/pair` with the server's one-time pairing code, device model and `platform: android`. Authenticated requests use the returned bearer device token. That is the server-supported device credential, so the app never asks for the account password.

For older Downtify 3.1.x servers, `/api/server/info` does not exist and the single-page web app returns HTML for that path. The app probes `GET /api/version`; if the server is older than 3.2.0, it loads the server's own root page in the WebView so the existing home UI and same-origin sign-in work. The WebView download listener captures Downtify's existing `/downloads/` and `/media/` links, forwards the current session cookies to WorkManager, and adds completed tracks to the same Room database used by the 3.2 mobile API. Native device pairing and direct web-player handoff require the 3.2 mobile contract.

The Vue API adapter uses the user-configured server origin, and its Axios requests add the native bearer token. The upstream mobile API provides durable track IDs, incremental sync, playlist and likes endpoints, range streaming, covers and WebSocket events. Since the upstream `/tracks` rows use filenames, the bridge resolves a row's ID through `/api/v1/library?since=0` and caches the filename-to-ID mapping for later playback and downloads.

## Web/native bridge

`window.DowntifyNative` exposes server URL/token access, track play/pause/seek, download/status calls and album/playlist download entry points. It also opens Android's offline library and server settings. These two actions are inserted into Downtify's existing **More** sheet at runtime, which avoids a separate native header. The upstream player delegates track transport to Media3 when the 3.2 bridge exists. `web/frontend/src/config.js` and `src/model/api.js` are the only general API adapter changes; `src/model/player.js` hands off playback. The complete Vue source and npm lockfile are retained in `web/frontend`.

## Playback

`PlaybackService` is an AndroidX Media3 `MediaSessionService` with ExoPlayer. Its session provides Android media notification, lock-screen, headset and Bluetooth transport controls. The player requests audio focus and pauses for noisy output changes. `PlaybackController` supplies the paired bearer token to the HTTP data source. Native play checks the server-scoped Room row first and opens its app-private file when present; otherwise it streams the server track endpoint. Playback continues when the activity is not visible.

## Downloads and offline database

WorkManager downloads into `files/music/<server_id>/` and stores metadata in Room. A `.part` file is retained across interruption and resumed with `Range: bytes=<offset>-`; unsuccessful network attempts retry with backoff. Compatibility downloads forward only the cookies and user agent needed for the selected server link, then extract title, artist, album and duration from the downloaded audio. Device token and server settings use AndroidX encrypted shared preferences backed by Android Keystore. Room's composite `(serverId, trackId)` key keeps independent servers' libraries separate. The row includes title, artist, album, artwork URL, duration, file path, status, downloaded time and playback position. Artwork is not yet persisted locally, and playback position is reserved in the schema but not yet updated during playback.

## Online/offline switching

Startup displays a native local downloads screen before any network request. With no active internet-capable network it stays on that screen; otherwise an eight-second server reachability check precedes loading the web interface. Main-frame network/HTTP failures and a twelve-second page-load timeout return to the local screen. This fallback does not depend on the bundled Vue app bootstrapping successfully or on server API calls. Compatibility servers use their remote interface while connected; **More → Offline music** remains available for locally saved tracks. Local files remain in app storage when the server is unreachable or its token is revoked. Selecting an offline-library row always opens its private local file through Media3 using the row's server identity. A 3.2 remote play request prefers a matching local track. Changing to a different server identity scopes future lookup and downloads to the new server ID.

## Current limits

This first implementation is a usable foundation, not yet the complete acceptance target. Track and collection offline actions use the mobile IDs resolved from `/api/v1/library`; album and playlist downloads queue their supplied tracks individually. Incremental library sync, cached artwork, download progress surfaces, playback-position reporting, and a full native Offline page remain follow-up work. Release signing uses the debug key until a private production signing key is configured.
