@echo off
echo Building APK for WiredLess Controller Bridge...
echo.

REM Check if we're in the right directory
if not exist "gradlew.bat" (
    echo Error: gradlew.bat not found!
    echo Please run this script from the android_app directory.
    pause
    exit /b 1
)

REM Build the debug APK
echo Building debug APK...
call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo.
    echo Error: APK build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo APK build completed successfully!
echo.
echo The APK can be found at:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on a connected device:
echo   adb install app\build\outputs\apk\debug\app-debug.apk
echo.
pause