# Build

Requirements: Windows, JDK 17, Android SDK platform 35 and build tools 35.0.0, Node.js 20 or newer, and npm. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the SDK directory if it is not in the default location. The checked in Gradle wrapper downloads Gradle on first use.

From the repository root:

```bat
npm --prefix web\frontend ci
npm --prefix web\frontend run build
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stage-web.ps1
gradlew.bat --no-daemon assembleDebug assembleRelease
```

The two APKs are written to:

```text
app\build\outputs\apk\debug\app-debug.apk
app\build\outputs\apk\release\app-release.apk
```

Or run `build-android.bat`. It installs the locked frontend dependencies, builds and stages the bundled UI, then assembles both APKs. The first run needs network access for npm packages and Gradle dependencies. Debug and release variants use the local Android debug signing key for convenient evaluation; replace the release signing configuration before distributing a production build.
