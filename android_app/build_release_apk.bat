@echo off
echo Building Release APK for WiredLess Controller Bridge...
echo.

REM Check if we're in the right directory
if not exist "gradlew.bat" (
    echo Error: gradlew.bat not found!
    echo Please run this script from the android_app directory.
    pause
    exit /b 1
)

REM Check if keystore exists
if not exist "my-release-key.jks" (
    echo Warning: Release keystore not found!
    echo You need to create a signing key first.
    echo.
    echo To generate a key, run:
    echo   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -storepass android -keypass android -validity 10000 -alias android
    echo.
    echo Or place your existing keystore file in this directory.
    pause
    exit /b 1
)

REM Build the release APK
echo Building release APK...
call gradlew.bat assembleRelease

if %errorlevel% neq 0 (
    echo.
    echo Error: Release APK build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo Release APK build completed successfully!
echo.
echo The APK can be found at:
echo   app\build\outputs\apk\release\app-release.apk
echo.
pause