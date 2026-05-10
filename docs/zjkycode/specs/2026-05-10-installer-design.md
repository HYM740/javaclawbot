# NexusAI 一键安装设计规范

> 日期: 2026-05-10
> 版本: 1.0
> 状态: 设计阶段

## 1. 概述

为 NexusAI（原 JavaClawBot）提供一键安装功能，面向**普通 GUI 用户**，支持 **Windows（主力）+ macOS + Linux（辅助）** 三平台。

项目运行时依赖：JDK 17（硬依赖）、Git（硬依赖）、Python（硬依赖）、Node.js（硬依赖）。

## 2. 方案选择

**方案二：Inno Setup 专业安装包（Windows）+ Shell 脚本（macOS/Linux）**

| 平台 | 安装方式 | 用户体验 |
|------|---------|---------|
| Windows | Inno Setup 向导 (.exe) | 原生安装向导，双击即用 |
| macOS | Shell 脚本 (.sh) | 终端运行 |
| Linux | Shell 脚本 (.sh) | 终端运行 |

Windows 是目标用户的绝对主力平台，macOS/Linux 为辅助。

## 3. Windows 安装器设计

### 3.1 安装向导流程

```
用户双击 NexusAI-Setup-2.2.8.exe (~10MB 安装器本身)
        │
        ▼
┌─────────────────────────────────────────────┐
│  步骤1: 欢迎页                               │
│  "本向导将安装 NexusAI 及所需运行环境"        │
├─────────────────────────────────────────────┤
│  步骤2: 环境检测 + 组件选择                   │
│                                              │
│  ☑ JDK 17 运行时    ✅ 已安装 (跳过)         │
│  ☑ Git 版本管理     ❌ 未安装 (需下载)        │
│  ☑ Python 脚本引擎  ✅ 已安装 (跳过)          │
│  ☑ Node.js 引擎     ❌ 未安装 (需下载)        │
│                                              │
│  * 已安装的组件自动灰化，不可选择              │
│  * 未安装的组件默认勾选，可选择跳过            │
├─────────────────────────────────────────────┤
│  步骤3: 安装位置                             │
│  默认: C:\Program Files\NexusAI              │
├─────────────────────────────────────────────┤
│  步骤4: 下载进度（仅未安装组件）              │
│  ⬇ jdk-17-jre-x64.zip      [████████] ✓    │
│  ⬇ Git-Portable-64-bit.7z  [████░░░░] 45%  │
│  ⏳ python-3.12-embed.zip  [等待中...]      │
│  ⏳ node-v22.12-win-x64.zip [等待中...]      │
│                                              │
│  总进度: ████████░░░░░░░░░░ 25% (45/180MB)  │
├─────────────────────────────────────────────┤
│  步骤5: 解压 & 配置 PATH                     │
│  解压 runtime 依赖 → 注册 PATH → 创建快捷方式 │
├─────────────────────────────────────────────┤
│  步骤6: 完成                                 │
│  ☑ 启动 NexusAI                             │
│  ☑ 创建桌面快捷方式                          │
└─────────────────────────────────────────────┘
```

### 3.2 环境检测逻辑

安装向导在显示组件页面**之前**执行检测，确定每项的安装状态：

| 依赖 | Windows 检测方法 | 检测命令/路径 |
|------|-----------------|--------------|
| JDK 17 | 命令行检测 + 注册表 | `java --version 2>&1 \| find "17"` 或 `HKLM\SOFTWARE\JavaSoft\JDK\17` |
| Git | 命令行检测 + 注册表 | `git --version` 或 `HKLM\SOFTWARE\Git\CurrentVersion` |
| Python | 命令行检测 + 注册表 | `python --version` 或 `HKCU\SOFTWARE\Python\PythonCore\3.12` |
| Node.js | 命令行检测 + 注册表 | `node --version` 或 `HKLM\SOFTWARE\Node.js` |

**检测策略**：命令行为主（检测 PATH 中可用），注册表为辅（检测已安装但 PATH 未生效）。

**已安装处理**：
- 组件复选框灰化禁用，显示 "✅ 已安装"
- 不触发下载，跳过该组件

### 3.3 安装目录结构

```
C:\Program Files\NexusAI\           (或用户选择的自定义路径)
├── java17\                          # JDK 17 裁剪运行时
│   └── bin\java.exe
├── git\                             # Git Portable 解压
│   └── bin\git.exe
├── python\                          # Python Embeddable 解压
│   └── python.exe
├── node\                            # Node.js Portable 解压
│   └── node.exe
├── NexusAI.jar                      # 主程序 fat jar
├── NexusAI.exe                      # 启动器 (jpackage 生成，可选)
└── unins000.exe                     # 卸载程序 (Inno Setup 自动生成)
```

### 3.4 PATH 配置

写入**用户级**环境变量，无需管理员权限：

```
注册表路径: HKCU\Environment\Path

追加内容:
  {安装目录}\java17\bin
  {安装目录}\git\bin
  {安装目录}\python
  {安装目录}\node
```

Inno Setup 通过注册表操作实现，需要处理 PATH 去重（避免重复安装时追加多次）。

### 3.5 下载源配置

所有运行时依赖从内网服务器下载：

```
基础 URL: http://192.168.20.125:9100/releases/2.2.8/windows/

文件列表:
  jdk-17-jre-x64.zip           # JDK 17 裁剪运行时 (~40MB)
  Git-Portable-64-bit.7z       # Git Portable (~55MB)
  python-3.12.4-embed-amd64.zip  # Python Embedded (~8MB)
  node-v22.12.0-win-x64.zip    # Node.js Portable (~28MB)
  NexusAI.jar                  # 主程序 (~40MB)
```

### 3.6 下载插件

使用 **IDP (Inno Download Plugin)**：
- 多文件并发下载
- 带总体进度条 + 单文件进度条
- HTTP 断点续传
- 下载失败自动重试 3 次
- 支持 Hash 校验 (SHA256)

### 3.7 卸载

Inno Setup 自动生成的卸载程序 `unins000.exe`：
- 删除所有安装文件（runtime 目录 + JAR）
- 清理 PATH 环境变量（仅删除自己添加的部分）
- 删除开始菜单快捷方式
- 可选：保留用户数据（配置、会话历史）

## 4. 下载方案（运行时依赖获取）

### 4.1 依赖清单

需要从互联网下载以下 portable/embed 版本：

| 依赖 | 版本 | 下载源 | 文件名 | 大小 |
|------|------|--------|--------|------|
| Git Portable | 2.47.0 | github.com/git-for-windows/git | PortableGit-2.47.0-64-bit.7z.exe | ~55MB |
| Python Embedded | 3.12.4 | python.org | python-3.12.4-embed-amd64.zip | ~8MB |
| Node.js | 22.12.0 LTS | nodejs.org | node-v22.12.0-win-x64.zip | ~28MB |
| JDK 17 (jlink 裁剪) | 17.0.x | adoptium.net | 本地 jlink 生成 | ~40MB |

### 4.2 上传目标

上传至 MinIO (S3 兼容存储)，供内网服务器分发：

```
MinIO 配置:
  Endpoint: http://192.168.20.125:9000
  Bucket: agent
  AccessKey: XSnCKcUT4Z5M2BbYIRQP
  PathStyle: true
```

内网 HTTP 访问路径: `http://192.168.20.125:9100`（通过 netHost 代理 `http://101.68.93.109:9102`）

## 5. 项目更名

javaclawbot → **NexusAI**，全项目统一更名。

### 5.1 更名范围

| 文件/配置 | 旧值 | 新值 |
|----------|------|------|
| `pom.xml` artifactId | javaclawbot | nexusai |
| `pom.xml` finalName | javaclawbot | NexusAI |
| `CHANGELOG.md` 标题 | JavaClawBot | NexusAI |
| `script/build-exe.bat` | JavaClawBot / javaclawbot | NexusAI / NexusAI |
| `script/run.bat` | javaclawbot | NexusAI |
| `build-exe.bat` APP_NAME | JavaClawBot | NexusAI |
| `build-exe.bat` VENDOR | JavaClawBot | NexusAI |
| `build-exe.bat` MAIN_JAR_NAME | javaclawbot.jar | NexusAI.jar |
| `build-exe.bat` BASE_URL | D:\open_code\pkg_exe | 保持或更新 |
| 安装器文件名 | JavaClawBot-Setup-*.exe | NexusAI-Setup-*.exe |
| 安装目录 | C:\Program Files\JavaClawBot | C:\Program Files\NexusAI |

### 5.2 不更名的内容

- Java 包名 (`package com.zjky.ai.xxx`)：更名工作量巨大且影响 Git 历史，暂不修改
- 主类名、函数名
- `.idea/` IDE 配置
- Git 仓库远程地址

## 6. 实施范围

### 6.1 需要新建的文件

| 文件 | 说明 |
|------|------|
| `installer/windows/NexusAI.iss` | Inno Setup 安装脚本 |
| `installer/windows/idp.iss` | IDP 下载插件（内嵌） |
| `installer/macos/install.sh` | macOS 安装脚本 |
| `installer/linux/install.sh` | Linux 安装脚本 |

### 6.2 需要修改的文件

| 文件 | 修改内容 |
|------|---------|
| `pom.xml` | artifactId, finalName 更名 |
| `CHANGELOG.md` | 标题更名，新增 2.2.8 条目 |
| `script/build-exe.bat` | 全量名称替换 |
| `script/run.bat` | 路径名称替换 |

## 7. 不在此范围内

- 跨平台自动构建流水线
- 代码签名（Authenticode / Apple notarization）
- mac 下 .app bundle 的完整打包
- Linux 下 .deb/.rpm 包
- 自动更新（版本检测 + 增量升级）
