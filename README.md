# Downtify for Android

An Android companion for a self-hosted Downtify server. The app bundles the upstream Vue web interface and adds native device pairing, Media3 background playback, and private offline downloads. The upstream frontend source lives in [`web/frontend`](web/frontend); a small adapter routes its API requests through the selected server and hands music playback to Android.

Servers on Downtify 3.1.x open their own web interface in compatibility mode. In that mode, the app intercepts the existing **Save to this device** action, downloads the track into protected app storage, and plays saved tracks through Media3. Downtify 3.2.0 or newer additionally supports native device pairing and the full web/native playback bridge.

## Current implementation

- Server URL setup and Downtify API identity check (`/api/server/info`).
- Device pairing at `/api/auth/pair`; the device token is stored with Android encrypted shared preferences.
- Bundled web interface, not an internet browser wrapper.
- Media3 media session service for playback controls, audio focus and background playback.
- Room metadata database and app-private file storage for downloaded tracks.
- WorkManager transfers with HTTP Range resume, retry and Wi-Fi-only option.
- Basic offline downloads browser, quality preference and storage clearing.
- Android actions live in Downtify's existing **More** menu, so the web interface can use the full screen.

To save a track on Downtify 3.1.x, open the track's three-dot menu and tap **Save to this device**. Open **More → Offline music** to browse and play saved tracks. See [BUILD.md](BUILD.md) for build commands and [ARCHITECTURE.md](ARCHITECTURE.md) for integration details and current limits. On Downtify 3.2.0 or newer, pair the phone from Downtify's **Settings → Apps → Pair a phone**, then enter the displayed code in the Android app.

## Upstream

The bundled web source is based on [henriquesebastiao/downtify](https://github.com/henriquesebastiao/downtify) (GPL-3.0). `web/UPSTREAM.md` records the source revision and local adapter changes.
