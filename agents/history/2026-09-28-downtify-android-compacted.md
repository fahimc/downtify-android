# Downtify Android implementation handoff

## Purpose and source

Standalone Android client repository at `C:/Projects/downtify-android`. Upstream Downtify was inspected at revision `d0eec48` (`henriquesebastiao/downtify`, GPL-3.0). It is a Vue 3/Vite frontend with a FastAPI server. Its mobile contract supports server discovery/info, device pairing, bearer tokens, track-ID library reads, range stream/download, and WebSockets.

## Completed

- Added native Android app scaffolding with Kotlin, Media3 playback service and queue, Room metadata, WorkManager track downloads, Keystore-backed encrypted preferences, offline actions in the existing UI, and generated web asset bundle served with WebViewAssetLoader.
- Added setup/pairing using `/api/server/info` and `/api/auth/pair`; track IDs are resolved from `/api/v1/library` because upstream web rows use filenames.
- Added Offline browser/settings, Wi-Fi-only preference, quality choice, storage clear action, build docs/script, and GPL-3 license.
- Frontend `npm ci` and production build succeeded. `assembleDebug assembleRelease` succeeded on 2026-09-28 using JDK 17, Android SDK 35, Gradle 8.9. APKs: debug 9,371,921 bytes and release 7,402,295 bytes. `aapt dump badging` confirmed package `com.downtify.android`, min SDK 26, target SDK 35; APK listing confirmed bundled `assets/index.html` and JS.

## Current constraints / next work

- No device or emulator playback validation was performed; verification covers frontend production build and Gradle APK assembly only.
- Library incremental sync, artwork download/cache, saved playback-position updates, persisted progress/status and retry presentation, foreground long-running download behavior, and fuller Offline browsing need implementation.
- Rebuild embedded web assets after frontend edits: `npm --prefix web/frontend ci`, `npm --prefix web/frontend run build`, `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stage-web.ps1`; then `gradlew.bat --no-daemon assembleDebug assembleRelease`.
- Release signing uses the debug signing config and must be changed before publishing.

## Follow-up: connection response diagnostics (2026-09-28)

The user-provided screenshot showed `JSONObject` receiving a body starting with `<!doctype`, proving the server-info request returned HTML and the parser leaked its exception. Updated setup to check HTTP status/content type, report likely root-path/proxy interception causes, and retain the URL/pair code after a failed attempt. Version is now 0.1.1.

Verification: `assembleDebug assembleRelease` succeeds. Installed debug APK 0.1.1 on connected Moto G31 (Android 12) through Appium. Entered `https://example.com`; UI showed a useful HTTP 404/HTML diagnostic and preserved the URL. Deleted Appium session and force-stopped app afterward. User's actual Downtify URL is not present in the screenshot, so live server compatibility remains unverified pending the server root URL.

## Follow-up: Downtify 3.1.x home-page compatibility (2026-09-28)

The user's public URL `https://downtify1.m8e.co.uk` was inspected read-only: `/api/health` and `/api/version` return 3.1.0 JSON; `/api/server/info` returns the SPA HTML (200), because the mobile API contract is absent on this version. Added a version probe and a web-only compatibility mode that opens the server origin in WebView, preserving same-origin UI/auth. Native device pairing and download/playback bridge are enabled only on servers with the 3.2+ mobile API. Bundled UI is used as offline fallback if the legacy server cannot be reached.

Verification: frontend production build and `assembleDebug assembleRelease` succeeded. Explicitly installed v0.1.2 on Moto G31 (Android 12), entered the user's URL, and confirmed the real Downtify home page loaded (Good afternoon screen). Earlier test attempts accidentally retained v0.1.1 due Appium `noReset`; the successful check used `adb install -r` and verified package versionCode 3/versionName 0.1.2. Appium session deleted and app force-stopped after capture. No login or credentials were entered.

## Follow-up: full-screen UI and Downtify 3.1 offline download (2026-09-28)

Removed the permanent Android header and injected **Offline music** and **Android server settings** into Downtify's existing More sheet. Added a WebView download listener for same-server `/downloads/` and `/media/` links. It forwards the active session cookies and user agent to WorkManager, supports Range resume, extracts embedded audio metadata, and records the completed file in Room. Version is now 0.1.3.

Verification on the connected Moto G31 against `https://downtify1.m8e.co.uk`: the remote UI filled the screen without the wrapper header; the two Android actions appeared in More; selecting **Save to this device** for “Thrift Shop” completed a WorkManager job and saved a 9,521,432-byte private audio file; **Offline music** showed the title and artist; selecting it started the Media3 session with Android playback state `PLAYING`. Wi-Fi and mobile data were disabled for six seconds and playback advanced to 15 seconds without an error, then both network radios were restored. Debug and release APK assembly succeeded before this device test.
