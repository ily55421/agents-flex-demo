@echo off
setlocal enabledelayedexpansion

set "PROJECT_DIR=d:\阅读\总结文档\web-app\android"
set "JDK_HOME=%PROJECT_DIR%\build-tools\jdk-17"
set "ANDROID_SDK_ROOT=%PROJECT_DIR%\build-tools\android-sdk"
set "GRADLE_HOME=%PROJECT_DIR%\build-tools\gradle-home"
set "PYTHON_HOME=%PROJECT_DIR%\build-tools\python-3.11"

cd /d "%PROJECT_DIR%"

echo ========================================
echo   Isolated Android Build Environment
echo ========================================
echo.

set "JAVA_HOME=%JDK_HOME%"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"

set "PYTHONHOME="
set "PYTHONPATH="
set "PYTHONEXECUTABLE="
set "CONDA_PREFIX="
set "VIRTUAL_ENV="
set "PIP_REQUIRE_VIRTUALENV="

set "PATH=%PYTHON_HOME%;%PYTHON_HOME%\Scripts;%JDK_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%SystemRoot%\system32;%SystemRoot%"

echo Python version:
python --version
echo.
where python
echo.

echo Stopping Gradle daemons...
call gradlew.bat --stop 2>nul
ping 127.0.0.1 -n 3 >nul

echo Cleaning build...
if exist "app\build" rmdir /s /q "app\build"

echo.
echo ========================================
echo   Starting APK Build...
echo ========================================
echo.

call gradlew.bat assembleDebug --no-daemon --console=plain
set BUILD_RESULT=%ERRORLEVEL%

echo.
if %BUILD_RESULT% EQU 0 (
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do set APK_SIZE=%%~zA
        set /a APK_SIZE_MB=!APK_SIZE! / 1048576
        echo ========================================
        echo   BUILD SUCCESSFUL!
        echo ========================================
        echo APK: %PROJECT_DIR%\app\build\outputs\apk\debug\app-debug.apk
        echo Size: approximately !APK_SIZE_MB! MB
    ) else (
        echo ========================================
        echo   BUILD REPORTED SUCCESS BUT APK NOT FOUND
        echo ========================================
        set BUILD_RESULT=1
    )
) else (
    echo ========================================
    echo   BUILD FAILED (exit code: %BUILD_RESULT%)
    echo ========================================
)

endlocal
exit /b %BUILD_RESULT%
