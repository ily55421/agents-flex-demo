@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "JDK_HOME=%PROJECT_DIR%build-tools\jdk-17"
set "ANDROID_SDK_ROOT=%PROJECT_DIR%build-tools\android-sdk"
set "GRADLE_USER_HOME=%PROJECT_DIR%build-tools\gradle-home"
set "PYTHON_HOME=%PROJECT_DIR%build-tools\python-3.11"

set "JAVA_HOME=%JDK_HOME%"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%"

:: Clear all Python env vars that could interfere
set PYTHONHOME=
set PYTHONPATH=
set PYTHONEXECUTABLE=
set PYTHONSTARTUP=
set PYTHONCASEOK=
set PYTHONDONTWRITEBYTECODE=
set PIP_REQUIRE_VIRTUALENV=
set CONDA_PREFIX=
set CONDA_DEFAULT_ENV=
set VIRTUAL_ENV=
set PYTHONNOUSERSITE=1

:: PATH with our Python first, then JDK, then minimal system
set "PATH=%PYTHON_HOME%;%PYTHON_HOME%\Scripts;%JDK_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem"

if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"

cd /d "%PROJECT_DIR%"

echo ========================================
echo   DocBlog Android APK Builder
echo ========================================
echo.
echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_HOME: %ANDROID_HOME%
echo PYTHON_HOME: %PYTHON_HOME%
echo.

:: Check if our Python exists, if not download it
if not exist "%PYTHON_HOME%\python.exe" (
    echo Downloading Python 3.11 for Windows (for Chaquopy pip)...
    if not exist "%PROJECT_DIR%build-tools" mkdir "%PROJECT_DIR%build-tools"
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://www.python.org/ftp/python/3.11.8/python-3.11.8-embed-amd64.zip' -OutFile '%PROJECT_DIR%build-tools\python-3.11.zip'"
    echo Extracting Python...
    powershell -Command "Expand-Archive -Path '%PROJECT_DIR%build-tools\python-3.11.zip' -DestinationPath '%PYTHON_HOME%' -Force"
    del "%PROJECT_DIR%build-tools\python-3.11.zip"
    echo Python 3.11 ready
)

echo Using Python:
%PYTHON_HOME%\python.exe --version
echo.

echo Starting Gradle build...
call gradlew.bat clean assembleDebug --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   BUILD SUCCESSFUL!
    echo ========================================
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo APK size: %%~zA bytes
        echo APK: %PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk
    )
) else (
    echo.
    echo ========================================
    echo   BUILD FAILED
    echo ========================================
)

endlocal
