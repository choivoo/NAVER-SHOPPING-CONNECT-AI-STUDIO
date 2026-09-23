@echo off
REM Builds the debug APK. Output: app\build\outputs\apk\debug\app-debug.apk
cd /d "%~dp0"
call gradlew.bat :app:assembleDebug %*
if errorlevel 1 exit /b 1
echo Debug APK: %cd%\app\build\outputs\apk\debug\app-debug.apk
