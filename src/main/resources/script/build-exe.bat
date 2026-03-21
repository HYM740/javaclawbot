@echo off
REM ============================================
REM JavaClawBot Windows EXE 打包脚本
REM 需要在 Windows 上运行，需要 JDK 17+
REM ============================================

setlocal enabledelayedexpansion

set APP_NAME=javaclawbot
set APP_VERSION=1.0.0
set VENDOR=JavaClawBot
set JAR_FILE=.\javaclawbot.jar
set OUTPUT_DIR=.\dist
set JRE_DIR=.\jre

echo ============================================
echo   JavaClawBot Windows EXE 打包工具
echo ============================================
echo.

REM 检查 JDK
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [错误] 未找到 Java，请安装 JDK 17+
    pause
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%v
    goto :got_version
)
:got_version
echo [信息] Java 版本: %JAVA_VERSION%

REM 检查 jpackage
where jpackage >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [错误] 未找到 jpackage，请确保使用 JDK 而非 JRE
    pause
    exit /b 1
)

REM 检查 JAR 文件
if not exist "%JAR_FILE%" (
    echo [错误] 未找到 JAR 文件: %JAR_FILE%
    pause
    exit /b 1
)
echo [信息] JAR 文件: %JAR_FILE%

REM 创建输出目录
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"

echo.
echo [步骤 1/2] 创建精简 JRE...

REM 获取 Java 模块列表
for /f "delims=" %%i in ('jdeps --print-module-deps --ignore-missing-deps "%JAR_FILE%" 2^>nul') do set MODULES=%%i

REM 添加必要的模块
set MODULES=%MODULES%,java.desktop,java.logging,java.sql,java.naming,java.management,java.security.jgss,java.security.sasl,jdk.crypto.ec,jdk.unsupported

echo [信息] 模块: %MODULES%

REM 创建精简 JRE
if exist "%JRE_DIR%" rmdir /s /q "%JRE_DIR%"
jlink --add-modules %MODULES% --output "%JRE_DIR%" --strip-debug --compress 2 --no-header-files --no-man-pages

if %ERRORLEVEL% neq 0 (
    echo [错误] 创建 JRE 失败
    pause
    exit /b 1
)

echo [完成] JRE 创建成功

echo.
echo [步骤 2/2] 打包 EXE...

jpackage ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%" ^
  --description "JavaClawBot - AI Assistant" ^
  --input "..\target" ^
  --main-jar "javaclawbot.jar" ^
  --main-class "gui.JavaClawBotGUI" ^
  --runtime-image "%JRE_DIR%" ^
  --dest "%OUTPUT_DIR%" ^
  --type exe ^
  --win-console ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut

if %ERRORLEVEL% neq 0 (
    echo [错误] 打包失败
    pause
    exit /b 1
)

echo.
echo ============================================
echo   打包完成！
echo ============================================
echo.
echo 输出目录: %OUTPUT_DIR%
echo.

REM 显示生成的文件
dir "%OUTPUT_DIR%\*.exe" 2>nul

echo.
pause