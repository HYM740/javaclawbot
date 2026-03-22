@echo off
setlocal enabledelayedexpansion

REM ============================================
REM JavaClawBot Windows EXE Packaging Script
REM Debug Version - Path Checking Enabled
REM ============================================

echo.
echo ============================================
echo   JavaClawBot Windows EXE Packaging Tool
echo   Debug Version
echo ============================================
echo.

REM ===== Basic paths =====
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "JAVA_BIN=%JAVA_HOME%\bin"

set "JAVA_CMD=%JAVA_BIN%\java.exe"
set "JPACKAGE_CMD=%JAVA_BIN%\jpackage.exe"
set "JDEPS_CMD=%JAVA_BIN%\jdeps.exe"
set "JLINK_CMD=%JAVA_BIN%\jlink.exe"

set "APP_NAME=javaclawbot"
set "APP_VERSION=1.0.0"
set "VENDOR=JavaClawBot"

set "JAR_FILE=D:\opencode_code\pkg_exe\javaclawbot.jar"
set "OUTPUT_DIR=D:\opencode_code\pkg_exe\dist"
set "JRE_DIR=D:\opencode_code\pkg_exe\jre"
set "TEMP_PACKAGE_DIR=D:\opencode_code\pkg_exe\temp_package"

echo [DEBUG] Current working directory: %cd%
echo [DEBUG] JAVA_HOME        = %JAVA_HOME%
echo [DEBUG] JAVA_BIN         = %JAVA_BIN%
echo [DEBUG] JAVA_CMD         = %JAVA_CMD%
echo [DEBUG] JPACKAGE_CMD     = %JPACKAGE_CMD%
echo [DEBUG] JDEPS_CMD        = %JDEPS_CMD%
echo [DEBUG] JLINK_CMD        = %JLINK_CMD%
echo [DEBUG] JAR_FILE         = %JAR_FILE%
echo [DEBUG] OUTPUT_DIR       = %OUTPUT_DIR%
echo [DEBUG] JRE_DIR          = %JRE_DIR%
echo [DEBUG] TEMP_PACKAGE_DIR = %TEMP_PACKAGE_DIR%
echo.

echo [CHECK] Does JAVA_HOME exist?
if exist "%JAVA_HOME%" (
    echo [OK] Exists: %JAVA_HOME%
) else (
    echo [ERROR] Not found: %JAVA_HOME%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does JAVA_BIN exist?
if exist "%JAVA_BIN%" (
    echo [OK] Exists: %JAVA_BIN%
) else (
    echo [ERROR] Not found: %JAVA_BIN%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does java.exe exist?
if exist "%JAVA_CMD%" (
    echo [OK] Exists: %JAVA_CMD%
) else (
    echo [ERROR] Not found: %JAVA_CMD%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does jpackage.exe exist?
if exist "%JPACKAGE_CMD%" (
    echo [OK] Exists: %JPACKAGE_CMD%
) else (
    echo [ERROR] Not found: %JPACKAGE_CMD%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does jdeps.exe exist?
if exist "%JDEPS_CMD%" (
    echo [OK] Exists: %JDEPS_CMD%
) else (
    echo [ERROR] Not found: %JDEPS_CMD%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does jlink.exe exist?
if exist "%JLINK_CMD%" (
    echo [OK] Exists: %JLINK_CMD%
) else (
    echo [ERROR] Not found: %JLINK_CMD%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does JAR file exist?
if exist "%JAR_FILE%" (
    echo [OK] Exists: %JAR_FILE%
) else (
    echo [ERROR] Not found: %JAR_FILE%
    pause
    exit /b 1
)
echo.

echo [CHECK] Does JAR directory exist?
for %%i in ("%JAR_FILE%") do set "JAR_DIR=%%~dpi"
echo [DEBUG] JAR_DIR = !JAR_DIR!
if exist "!JAR_DIR!" (
    echo [OK] Exists: !JAR_DIR!
) else (
    echo [ERROR] Not found: !JAR_DIR!
    pause
    exit /b 1
)
echo.

echo [CHECK] Does parent directory of OUTPUT_DIR exist?
for %%i in ("%OUTPUT_DIR%") do set "OUTPUT_PARENT=%%~dpi"
echo [DEBUG] OUTPUT_PARENT = !OUTPUT_PARENT!
if exist "!OUTPUT_PARENT!" (
    echo [OK] Exists: !OUTPUT_PARENT!
) else (
    echo [ERROR] Not found: !OUTPUT_PARENT!
    pause
    exit /b 1
)
echo.

echo ============================================
echo Java version
echo ============================================
"%JAVA_CMD%" -version 2>&1
echo.

echo ============================================
echo jpackage version
echo ============================================
"%JPACKAGE_CMD%" --version 2>&1
echo.

echo ============================================
echo jdeps version
echo ============================================
"%JDEPS_CMD%" --version 2>&1
echo.

echo ============================================
echo jlink version
echo ============================================
"%JLINK_CMD%" --version 2>&1
echo.

echo [DEBUG] Listing JDK bin directory:
dir "%JAVA_BIN%"
echo.

echo [DEBUG] Listing package directory:
dir "D:\opencode_code\pkg_exe"
echo.

REM ===== Prepare output directory =====
echo [STEP] Preparing output directory...
if exist "%OUTPUT_DIR%" (
    echo [DEBUG] Removing old directory: %OUTPUT_DIR%
    rmdir /s /q "%OUTPUT_DIR%"
)
mkdir "%OUTPUT_DIR%"
if errorlevel 1 (
    echo [ERROR] Failed to create output directory: %OUTPUT_DIR%
    pause
    exit /b 1
)
echo [OK] Output directory is ready: %OUTPUT_DIR%
echo.

REM ===== Detect modules =====
echo [STEP] Detecting required modules with jdeps...
set "MODULES="
for /f "delims=" %%i in ('"%JDEPS_CMD%" --print-module-deps --ignore-missing-deps "%JAR_FILE%" 2^>nul') do set "MODULES=%%i"

echo [DEBUG] jdeps returned MODULES = %MODULES%
if "%MODULES%"=="" (
    echo [WARN] jdeps returned no modules, using default set
    set "MODULES=java.base,java.desktop,java.logging,java.sql,java.naming,java.management,java.security.jgss,java.security.sasl,jdk.crypto.ec,jdk.unsupported"
)
echo [INFO] Final modules: %MODULES%
echo.

REM ===== Create runtime image =====
echo [STEP] Creating custom runtime image...
if exist "%JRE_DIR%" (
    echo [DEBUG] Removing old runtime image: %JRE_DIR%
    rmdir /s /q "%JRE_DIR%"
)

echo [DEBUG] Command to execute:
echo "%JLINK_CMD%" --add-modules %MODULES% --output "%JRE_DIR%" --strip-debug --compress 2 --no-header-files --no-man-pages
echo.

"%JLINK_CMD%" ^
  --add-modules %MODULES% ^
  --output "%JRE_DIR%" ^
  --strip-debug ^
  --compress 2 ^
  --no-header-files ^
  --no-man-pages

if errorlevel 1 (
    echo [ERROR] jlink failed to create runtime image
    echo [DEBUG] Target runtime directory: %JRE_DIR%
    pause
    exit /b 1
)

if exist "%JRE_DIR%\bin\java.exe" (
    echo [OK] Runtime image created successfully: %JRE_DIR%\bin\java.exe
) else (
    echo [ERROR] java.exe not found after runtime creation: %JRE_DIR%\bin\java.exe
    pause
    exit /b 1
)
echo.

REM ===== Prepare temp package dir =====
echo [STEP] Preparing temporary package directory...
if exist "%TEMP_PACKAGE_DIR%" (
    echo [DEBUG] Removing old temp directory: %TEMP_PACKAGE_DIR%
    rmdir /s /q "%TEMP_PACKAGE_DIR%"
)
mkdir "%TEMP_PACKAGE_DIR%"
if errorlevel 1 (
    echo [ERROR] Failed to create temp directory: %TEMP_PACKAGE_DIR%
    pause
    exit /b 1
)
echo [OK] Temp directory created: %TEMP_PACKAGE_DIR%
echo.

REM ===== Copy JAR =====
echo [STEP] Copying JAR into temp directory...
copy "%JAR_FILE%" "%TEMP_PACKAGE_DIR%\"
if errorlevel 1 (
    echo [ERROR] Failed to copy JAR
    echo [DEBUG] Source: %JAR_FILE%
    echo [DEBUG] Target: %TEMP_PACKAGE_DIR%\
    pause
    exit /b 1
)
echo [OK] JAR copied successfully
echo.

echo [DEBUG] Listing temp package directory:
dir "%TEMP_PACKAGE_DIR%"
echo.

REM ===== Run jpackage =====
echo [STEP] Running jpackage...
echo [DEBUG] Command to execute:
echo "%JPACKAGE_CMD%" --type exe --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --description "JavaClawBot - AI Assistant" --input "%TEMP_PACKAGE_DIR%" --main-jar "javaclawbot.jar" --main-class "gui.JavaClawBotGUI" --runtime-image "%JRE_DIR%" --dest "%OUTPUT_DIR%" --win-console --win-dir-chooser --win-menu --win-shortcut --verbose
echo.

"%JPACKAGE_CMD%" ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%" ^
  --description "JavaClawBot - AI Assistant" ^
  --input "%TEMP_PACKAGE_DIR%" ^
  --main-jar "javaclawbot.jar" ^
  --main-class "gui.JavaClawBotGUI" ^
  --runtime-image "%JRE_DIR%" ^
  --dest "%OUTPUT_DIR%" ^
  --win-console ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --verbose

set "JPACKAGE_ERROR=%ERRORLEVEL%"
echo.
echo [DEBUG] jpackage exit code: %JPACKAGE_ERROR%
echo.

REM ===== Cleanup =====
if exist "%TEMP_PACKAGE_DIR%" (
    echo [DEBUG] Cleaning temp directory: %TEMP_PACKAGE_DIR%
    rmdir /s /q "%TEMP_PACKAGE_DIR%"
)
echo.

if not "%JPACKAGE_ERROR%"=="0" (
    echo [ERROR] Packaging failed, exit code: %JPACKAGE_ERROR%
    pause
    exit /b 1
)

echo ============================================
echo Packaging completed
echo ============================================
echo Output directory: %OUTPUT_DIR%
echo.

dir "%OUTPUT_DIR%"
echo.
pause