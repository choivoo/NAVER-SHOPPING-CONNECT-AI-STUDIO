@echo off
REM Builds release APK + AAB (R8 minified). Signed only if keystore.properties exists.
cd /d "%~dp0"
call gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease %*
if errorlevel 1 exit /b 1
dir app\build\outputs\apk\release app\build\outputs\bundle\release
if not exist keystore.properties echo NOTE: keystore.properties not found - release APK is unsigned. See SETUP_GUIDE.md.
