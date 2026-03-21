#!/usr/bin/env pwsh
# ============================================
# JavaClawBot Windows EXE 打包脚本 (PowerShell)
# 需要在 Windows 上运行，需要 JDK 17+
# ============================================

param(
    [string]$AppName = "javaclawbot",
    [string]$Version = "1.0.0",
    [string]$Vendor = "JavaClawBot",
    [switch]$Portable,  # 生成便携版（免安装）
    [switch]$Help
)

if ($Help) {
    Write-Host @"
JavaClawBot Windows EXE 打包工具

用法: .\build-exe.ps1 [-AppName name] [-Version version] [-Portable]

参数:
  -AppName    应用名称 (默认: javaclawbot)
  -Version    版本号 (默认: 1.0.0)
  -Portable   生成便携版 EXE（免安装）
  -Help       显示帮助信息

示例:
  .\build-exe.ps1                    # 生成安装版 EXE
  .\build-exe.ps1 -Portable          # 生成便携版 EXE
  .\build-exe.ps1 -Version 2.0.0     # 指定版本号
"@
    exit 0
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  JavaClawBot Windows EXE 打包工具" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Java
$javaVersion = java -version 2>&1 | Select-Object -First 1
if (-not $javaVersion) {
    Write-Host "[错误] 未找到 Java，请安装 JDK 17+" -ForegroundColor Red
    exit 1
}
Write-Host "[信息] $javaVersion" -ForegroundColor Green

# 检查 jpackage
$jpackage = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackage) {
    Write-Host "[错误] 未找到 jpackage，请确保使用 JDK 而非 JRE" -ForegroundColor Red
    exit 1
}

# 检查 JAR 文件
$jarFile = "..\target\javaclawbot.jar"
if (-not (Test-Path $jarFile)) {
    Write-Host "[错误] 未找到 JAR 文件: $jarFile" -ForegroundColor Red
    exit 1
}
$jarSize = (Get-Item $jarFile).Length / 1MB
Write-Host "[信息] JAR 文件: $jarFile ($([math]::Round($jarSize, 2)) MB)" -ForegroundColor Green

# 创建输出目录
$outputDir = ".\dist"
if (Test-Path $outputDir) {
    Remove-Item -Recurse -Force $outputDir
}
New-Item -ItemType Directory -Path $outputDir | Out-Null

Write-Host ""
Write-Host "[步骤 1/2] 创建精简 JRE..." -ForegroundColor Yellow

# 获取模块依赖
$modules = jdeps --print-module-deps --ignore-missing-deps $jarFile 2>$null
if (-not $modules) {
    $modules = "java.base"
}

# 添加必要的模块
$extraModules = @(
    "java.desktop",
    "java.logging", 
    "java.sql",
    "java.naming",
    "java.management",
    "java.security.jgss",
    "java.security.sasl",
    "jdk.crypto.ec",
    "jdk.unsupported",
    "jdk.httpserver"
)
$allModules = ($modules -split ",") + $extraModules | Select-Object -Unique | Where-Object { $_ }
$modulesStr = $allModules -join ","

Write-Host "[信息] 模块: $modulesStr" -ForegroundColor Gray

# 创建精简 JRE
$jreDir = ".\jre"
if (Test-Path $jreDir) {
    Remove-Item -Recurse -Force $jreDir
}

jlink --add-modules $modulesStr --output $jreDir --strip-debug --compress 2 --no-header-files --no-man-pages

if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 创建 JRE 失败" -ForegroundColor Red
    exit 1
}

$jreSize = (Get-ChildItem -Recurse $jreDir | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "[完成] JRE 创建成功 ($([math]::Round($jreSize, 2)) MB)" -ForegroundColor Green

Write-Host ""
Write-Host "[步骤 2/2] 打包 EXE..." -ForegroundColor Yellow

# 确定打包类型
if ($Portable) {
    $packageType = "app-image"
    Write-Host "[信息] 生成便携版 EXE" -ForegroundColor Gray
} else {
    $packageType = "exe"
    Write-Host "[信息] 生成安装版 EXE" -ForegroundColor Gray
}

# 构建参数
$arguments = @(
    "--name", $AppName,
    "--app-version", $Version,
    "--vendor", $Vendor,
    "--description", "JavaClawBot - AI Assistant",
    "--input", "..\target",
    "--main-jar", "javaclawbot.jar",
    "--main-class", "gui.JavaClawBotGUI",
    "--runtime-image", $jreDir,
    "--dest", $outputDir,
    "--type", $packageType,
    "--win-console"
)

# 安装版额外选项
if (-not $Portable) {
    $arguments += @(
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut"
    )
}

# 执行打包
& jpackage $arguments

if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 打包失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  打包完成！" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "输出目录: $outputDir" -ForegroundColor Cyan
Write-Host ""

# 显示生成的文件
Get-ChildItem -Path $outputDir -Recurse | Where-Object { $_.Extension -in @(".exe", ".msi") } | ForEach-Object {
    $size = $_.Length / 1MB
    Write-Host "  $($_.Name) ($([math]::Round($size, 2)) MB)" -ForegroundColor White
}

Write-Host ""