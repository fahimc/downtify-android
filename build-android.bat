@echo off
setlocal
cd /d "%~dp0"
call npm --prefix web\frontend ci || exit /b 1
call npm --prefix web\frontend run build || exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\stage-web.ps1 || exit /b 1
call gradlew.bat --no-daemon assembleDebug assembleRelease || exit /b 1
echo.
echo APKs:
echo   app\build\outputs\apk\debug\app-debug.apk
echo   app\build\outputs\apk\release\app-release.apk
endlocal
