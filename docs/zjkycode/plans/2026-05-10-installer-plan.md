# NexusAI 一键安装实施计划

> **对于代理工作者：**必需的子技能：使用 zjkycode:subagent-driven-development（推荐）或 zjkycode:executing-plans 来逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：**为 NexusAI 构建 Windows Inno Setup 一键安装器 + macOS/Linux 辅助脚本，包含运行时依赖自动检测与下载

**架构：**Inno Setup 安装向导内嵌 IDP 下载插件，安装前检测 JDK17/Git/Python/Node.js 已有环境，缺失组件从内网 MinIO 自动下载并配置用户级 PATH；macOS/Linux 提供 shell 安装脚本

**技术栈：**Inno Setup 6 + Pascal 脚本 + IDP (Inno Download Plugin) + S3/MinIO + Shell (bash)

---

## 任务 1：下载运行时依赖并上传到 MinIO

**文件：**
- 下载到：`D:/code/ai_project/javaclawbot/installer/runtime/`

- [ ] **步骤 1：下载 Git Portable for Windows**

从 https://github.com/git-for-windows/git/releases/download/v2.47.0.windows.1/PortableGit-2.47.0-64-bit.7z.exe 下载

```bash
curl -L -o 'D:/code/ai_project/javaclawbot/installer/runtime/PortableGit-2.47.0-64-bit.7z.exe' \
  'https://github.com/git-for-windows/git/releases/download/v2.47.0.windows.1/PortableGit-2.47.0-64-bit.7z.exe'
```

- [ ] **步骤 2：下载 Python Embedded for Windows**

从 https://www.python.org/ftp/python/3.12.4/python-3.12.4-embed-amd64.zip 下载

```bash
curl -L -o 'D:/code/ai_project/javaclawbot/installer/runtime/python-3.12.4-embed-amd64.zip' \
  'https://www.python.org/ftp/python/3.12.4/python-3.12.4-embed-amd64.zip'
```

- [ ] **步骤 3：下载 Node.js for Windows**

从 https://nodejs.org/dist/v22.12.0/node-v22.12.0-win-x64.zip 下载

```bash
curl -L -o 'D:/code/ai_project/javaclawbot/installer/runtime/node-v22.12.0-win-x64.zip' \
  'https://nodejs.org/dist/v22.12.0/node-v22.12.0-win-x64.zip'
```

- [ ] **步骤 4：生成本地 JDK 17 裁剪 JRE（jlink）**

使用本地 JDK 17 运行 jlink 生成精简 JRE：

```bash
'C:/Program Files/Java/jdk-17/bin/jlink.exe' \
  --add-modules java.se,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs,jdk.management,jdk.management.agent,jdk.jdwp.agent,jdk.jfr,jdk.httpserver,jdk.attach,jdk.jartool,jdk.naming.dns,jdk.xml.dom \
  --output 'D:/code/ai_project/javaclawbot/installer/runtime/jdk17-jre' \
  --strip-debug --compress 2 --no-header-files --no-man-pages
```

然后将 jdk17-jre 目录打包为 zip：

```bash
cd 'D:/code/ai_project/javaclawbot/installer/runtime' && \
  'C:/Program Files/7-Zip/7z.exe' a jdk-17-jre-x64.zip jdk17-jre/
```

- [ ] **步骤 5：上传所有文件到 MinIO**

使用 MinIO 客户端或 S3 兼容工具上传。MinIO 配置：
- Endpoint: http://192.168.20.125:9000
- Bucket: agent
- AccessKey: XSnCKcUT4Z5M2BbYIRQP
- AccessSecret: UbeHwkVka2CGm0mvp51ADYIKppkr9GS2gadE8EFf
- PathStyle: true

上传路径：`releases/2.2.8/windows/`

```bash
# 使用 AWS CLI with S3 compatible endpoint
export AWS_ACCESS_KEY_ID=XSnCKcUT4Z5M2BbYIRQP
export AWS_SECRET_ACCESS_KEY=UbeHwkVka2CGm0mvp51ADYIKppkr9GS2gadE8EFf

for file in D:/code/ai_project/javaclawbot/installer/runtime/*.{zip,exe}; do
  aws s3 cp "$file" s3://agent/releases/2.2.8/windows/ \
    --endpoint-url http://192.168.20.125:9000 \
    --region us-east-1 \
    --force-path-style
done
```

验证上传：`aws s3 ls s3://agent/releases/2.2.8/windows/ --endpoint-url http://192.168.20.125:9000 --force-path-style`

- [ ] **步骤 6：提交 runtime 记录文件**

创建 `installer/runtime/manifest.txt` 记录文件清单和 SHA256。

---

## 任务 2：项目更名 javaclawbot → NexusAI

**文件：**
- 修改：`D:/code/ai_project/javaclawbot/pom.xml`
- 修改：`D:/code/ai_project/javaclawbot/CHANGELOG.md`
- 修改：`D:/code/ai_project/javaclawbot/script/build-exe.bat`
- 修改：`D:/code/ai_project/javaclawbot/script/run.ps1`

- [ ] **步骤 1：修改 pom.xml**

修改 artifactId 和 finalName：

```xml
<!-- 原 -->
<artifactId>javaclawbot</artifactId>
<!-- 新 -->
<artifactId>nexusai</artifactId>

<!-- 原 -->
<finalName>javaclawbot</finalName>
<!-- 新 -->
<finalName>NexusAI</finalName>
```

- [ ] **步骤 2：修改 CHANGELOG.md**

标题从 `JavaClawBot` → `NexusAI`

```markdown
<!-- 原 -->
All notable changes to JavaClawBot will be documented in this file.
<!-- 新 -->
All notable changes to NexusAI will be documented in this file.
```

- [ ] **步骤 3：修改 build-exe.bat**

全文替换：
- `JavaClawBot` → `NexusAI`
- `javaclawbot` → `NexusAI`（仅文件名相关，不包括路径）
- `APP_NAME=JavaClawBot` → `APP_NAME=NexusAI`
- `VENDOR=JavaClawBot` → `VENDOR=NexusAI`
- `MAIN_JAR_NAME=javaclawbot.jar` → `MAIN_JAR_NAME=NexusAI.jar`

- [ ] **步骤 4：修改 run.ps1**

```powershell
<!-- 原 -->
$APP_NAME = "javaclawbot"
<!-- 新 -->
$APP_NAME = "NexusAI"

<!-- 原 -->
$JAR_FILE = "javaclawbot.jar"
<!-- 新 -->
$JAR_FILE = "NexusAI.jar"

<!-- 原 -->
$APP_HOME = "D:\apps\javaclawbot"
<!-- 新 -->
$APP_HOME = "D:\apps\NexusAI"
```

- [ ] **步骤 5：编译验证**

```bash
cd 'D:/code/ai_project/javaclawbot'
mvn compile -Dmaven.test.skip=true
```

验证编译通过，JAR 名变为 `NexusAI.jar`。

---

## 任务 3：编写 Inno Setup 安装脚本

**文件：**
- 创建：`D:/code/ai_project/javaclawbot/installer/windows/NexusAI.iss`
- 创建：`D:/code/ai_project/javaclawbot/installer/windows/idp.iss`

- [ ] **步骤 1：创建 idp.iss（Inno Download Plugin 内嵌）**

下载 IDP 插件文件 `idp.iss` 放入 installer/windows/ 目录，从官方获取：
https://github.com/DomGries/InnoDependencyInstaller/blob/master/scripts/products/idp.iss

（或手动放置预下载的 idp.iss 文件）

- [ ] **步骤 2：编写 NexusAI.iss 主安装脚本**

```pascal
#define MyAppName "NexusAI"
#define MyAppVersion "2.2.8"
#define MyAppPublisher "NexusAI"
#define MyAppURL "http://nexusai.local"
#define MyAppExeName "NexusAI.exe"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\..\dist
OutputBaseFilename=NexusAI-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#MyAppName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Types]
Name: "full"; Description: "完整安装 (含所有运行时依赖)"
Name: "custom"; Description: "自定义安装"; Flags: iscustom

; ---- 运行时依赖下载配置 ----
#define RuntimeBaseURL "http://192.168.20.125:9100/releases/2.2.8/windows"

[Components]
Name: "jdk"; Description: "JDK 17 运行时"; Types: full custom; \
  Check: not IsJdk17Installed; Flags: disablenouninstallwarning
Name: "git"; Description: "Git 版本管理"; Types: full custom; \
  Check: not IsGitInstalled; Flags: disablenouninstallwarning
Name: "python"; Description: "Python 脚本引擎"; Types: full custom; \
  Check: not IsPythonInstalled; Flags: disablenouninstallwarning
Name: "nodejs"; Description: "Node.js JavaScript 引擎"; Types: full custom; \
  Check: not IsNodeInstalled; Flags: disablenouninstallwarning

[Files]
; 主程序 JAR（从内网下载）
Source: "{tmp}\NexusAI.jar"; DestDir: "{app}"; \
  Flags: external; Check: DownloadNexusAIJar

; 下载的运行时依赖
Source: "{tmp}\jdk-17-jre-x64.zip"; DestDir: "{tmp}"; \
  Flags: external; Components: jdk; Check: DownloadJdk
Source: "{tmp}\PortableGit-2.47.0-64-bit.7z.exe"; DestDir: "{tmp}"; \
  Flags: external; Components: git; Check: DownloadGit
Source: "{tmp}\python-3.12.4-embed-amd64.zip"; DestDir: "{tmp}"; \
  Flags: external; Components: python; Check: DownloadPython
Source: "{tmp}\node-v22.12.0-win-x64.zip"; DestDir: "{tmp}"; \
  Flags: external; Components: nodejs; Check: DownloadNode

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "启动 NexusAI"; \
  Flags: nowait postinstall skipifsilent

[Registry]
; 用户级 PATH 配置（无需管理员权限）
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}\java17\bin;{app}\git\bin;{app}\python;{app}\node"; \
  Check: NeedPathUpdate; AfterInstall: RefreshEnvironment

[Code]
// ===== 环境检测函数 =====

function IsJdk17Installed: Boolean;
var
  ResultCode: Integer;
begin
  Result := (ShellExec('', 'java', '--version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0));
end;

function IsGitInstalled: Boolean;
var
  ResultCode: Integer;
begin
  Result := (ShellExec('', 'git', '--version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0));
end;

function IsPythonInstalled: Boolean;
var
  ResultCode: Integer;
begin
  Result := (ShellExec('', 'python', '--version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0));
end;

function IsNodeInstalled: Boolean;
var
  ResultCode: Integer;
begin
  Result := (ShellExec('', 'node', '--version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0));
end;

// ===== 下载函数（使用 IDP） =====

function DownloadJdk: Boolean;
begin
  idpAddFile('{#RuntimeBaseURL}/jdk-17-jre-x64.zip', ExpandConstant('{tmp}\jdk-17-jre-x64.zip'));
  Result := True;
end;

function DownloadGit: Boolean;
begin
  idpAddFile('{#RuntimeBaseURL}/PortableGit-2.47.0-64-bit.7z.exe', ExpandConstant('{tmp}\PortableGit-2.47.0-64-bit.7z.exe'));
  Result := True;
end;

function DownloadPython: Boolean;
begin
  idpAddFile('{#RuntimeBaseURL}/python-3.12.4-embed-amd64.zip', ExpandConstant('{tmp}\python-3.12.4-embed-amd64.zip'));
  Result := True;
end;

function DownloadNode: Boolean;
begin
  idpAddFile('{#RuntimeBaseURL}/node-v22.12.0-win-x64.zip', ExpandConstant('{tmp}\node-v22.12.0-win-x64.zip'));
  Result := True;
end;

function DownloadNexusAIJar: Boolean;
begin
  idpAddFile('{#RuntimeBaseURL}/NexusAI.jar', ExpandConstant('{tmp}\NexusAI.jar'));
  Result := True;
end;

// ===== 安装后解压与 PATH 更新 =====

procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  AppDir: String;
begin
  AppDir := ExpandConstant('{app}');

  if CurStep = ssPostInstall then
  begin
    // 解压 JDK
    if IsComponentSelected('jdk') and not IsJdk17Installed then
      Exec('powershell.exe', '-Command "Expand-Archive -Path ''' + AppDir + '\jdk-17-jre-x64.zip'' -DestinationPath ''' + AppDir + '\java17'' -Force"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // 解压 Git
    if IsComponentSelected('git') and not IsGitInstalled then
      Exec(ExpandConstant('{tmp}\PortableGit-2.47.0-64-bit.7z.exe'), '-o"' + AppDir + '\git" -y', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // 解压 Python
    if IsComponentSelected('python') and not IsPythonInstalled then
      Exec('powershell.exe', '-Command "Expand-Archive -Path ''' + AppDir + '\python-3.12.4-embed-amd64.zip'' -DestinationPath ''' + AppDir + '\python'' -Force"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // 解压 Node.js
    if IsComponentSelected('nodejs') and not IsNodeInstalled then
      Exec('powershell.exe', '-Command "Expand-Archive -Path ''' + AppDir + '\node-v22.12.0-win-x64.zip'' -DestinationPath ''' + AppDir + '\node'' -Force"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // 复制 JAR
    FileCopy(ExpandConstant('{tmp}\NexusAI.jar'), AppDir + '\NexusAI.jar', False);
  end;
end;

function NeedPathUpdate: Boolean;
begin
  Result := IsComponentSelected('jdk') or IsComponentSelected('git') or IsComponentSelected('python') or IsComponentSelected('nodejs');
end;

procedure RefreshEnvironment;
begin
  // 广播环境变量变更
  SendBroadcastMessage(WM_WININICHANGE, 0, LongInt(PChar('Environment')));
end;
```

- [ ] **步骤 3：安装 Inno Setup 编译环境**

从 https://jrsoftware.org/isdl.php 下载 Inno Setup 6（如未安装）。

检查：`"C:/Program Files (x86)/Inno Setup 6/ISCC.exe"`

- [ ] **步骤 4：编译安装器测试**

```bash
cd 'D:/code/ai_project/javaclawbot/installer/windows'
"C:/Program Files (x86)/Inno Setup 6/ISCC.exe" NexusAI.iss
```

预期输出：`D:/code/ai_project/javaclawbot/dist/NexusAI-Setup-2.2.8.exe`

---

## 任务 4：编写 macOS 安装脚本

**文件：**
- 创建：`D:/code/ai_project/javaclawbot/installer/macos/install.sh`

- [ ] **步骤 1：编写 install.sh**

```bash
#!/bin/bash
set -e

APP_NAME="NexusAI"
APP_VERSION="2.2.8"
BASE_URL="http://192.168.20.125:9100/releases/${APP_VERSION}"
INSTALL_DIR="$HOME/Applications/NexusAI"
ARCH=$(uname -m)

# 映射架构到下载目录
case "$ARCH" in
  arm64) PLATFORM="macos-arm64" ;;
  x86_64) PLATFORM="macos-x64" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

echo "=== NexusAI 安装脚本 (macOS) ==="
echo "架构: $ARCH"
echo "安装目录: $INSTALL_DIR"
echo ""

# 创建目录
mkdir -p "$INSTALL_DIR"

# 下载并安装 JDK 17 JRE
if ! command -v java &>/dev/null || ! java --version 2>&1 | grep -q "17"; then
  echo "[1/4] 下载 JDK 17 运行时..."
  curl -L -o "/tmp/jdk-17-jre-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/jdk-17-jre-${PLATFORM}.tar.gz"
  echo "解压 JDK 17..."
  tar -xzf "/tmp/jdk-17-jre-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  echo "JDK 17 安装完成"
else
  echo "[1/4] JDK 17 ✅ 已安装"
fi

# 下载并安装 Git
if ! command -v git &>/dev/null; then
  echo "[2/4] 下载 Git..."
  curl -L -o "/tmp/git-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/git-${PLATFORM}.tar.gz"
  echo "解压 Git..."
  tar -xzf "/tmp/git-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  echo "Git 安装完成"
else
  echo "[2/4] Git ✅ 已安装"
fi

# 下载并安装 Python
if ! command -v python3 &>/dev/null; then
  echo "[3/4] 下载 Python..."
  curl -L -o "/tmp/python-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/python-${PLATFORM}.tar.gz"
  echo "解压 Python..."
  tar -xzf "/tmp/python-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  echo "Python 安装完成"
else
  echo "[3/4] Python ✅ 已安装"
fi

# 下载并安装 Node.js
if ! command -v node &>/dev/null; then
  echo "[4/4] 下载 Node.js..."
  curl -L -o "/tmp/node-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/node-${PLATFORM}.tar.gz"
  echo "解压 Node.js..."
  tar -xzf "/tmp/node-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  echo "Node.js 安装完成"
else
  echo "[4/4] Node.js ✅ 已安装"
fi

# 下载 NexusAI.jar
echo "下载 NexusAI 主程序..."
curl -L -o "$INSTALL_DIR/NexusAI.jar" \
  "${BASE_URL}/NexusAI.jar"

# 配置 PATH
SHELL_RC="$HOME/.zshrc"
if [ -f "$HOME/.bash_profile" ]; then
  SHELL_RC="$HOME/.bash_profile"
fi

if ! grep -q "NexusAI" "$SHELL_RC"; then
  echo "" >> "$SHELL_RC"
  echo "# NexusAI Runtime PATH" >> "$SHELL_RC"
  echo "export PATH=\"$INSTALL_DIR/java17/bin:$INSTALL_DIR/git/bin:$INSTALL_DIR/python/bin:$INSTALL_DIR/node/bin:\$PATH\"" >> "$SHELL_RC"
  echo "已追加 PATH 到 $SHELL_RC"
fi

# 清理临时文件
rm -f /tmp/jdk-17-jre-*.tar.gz /tmp/git-*.tar.gz /tmp/python-*.tar.gz /tmp/node-*.tar.gz

echo ""
echo "=== NexusAI 安装完成! ==="
echo "启动命令: java -jar $INSTALL_DIR/NexusAI.jar"
echo "请运行: source $SHELL_RC  或重新打开终端"
```

- [ ] **步骤 2：添加可执行权限验证**

```bash
chmod +x 'D:/code/ai_project/javaclawbot/installer/macos/install.sh'
```

---

## 任务 5：编写 Linux 安装脚本

**文件：**
- 创建：`D:/code/ai_project/javaclawbot/installer/linux/install.sh`

- [ ] **步骤 1：编写 install.sh**

```bash
#!/bin/bash
set -e

APP_NAME="NexusAI"
APP_VERSION="2.2.8"
BASE_URL="http://192.168.20.125:9100/releases/${APP_VERSION}"
INSTALL_DIR="$HOME/.local/share/NexusAI"
ARCH=$(uname -m)

# 映射架构
case "$ARCH" in
  aarch64) PLATFORM="linux-arm64" ;;
  x86_64) PLATFORM="linux-x64" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

echo "=== NexusAI 安装脚本 (Linux) ==="
echo "架构: $ARCH"
echo "安装目录: $INSTALL_DIR"
echo ""

# 检测包管理器，安装 7z 解压工具
if ! command -v unzip &>/dev/null && ! command -v 7z &>/dev/null; then
  if command -v apt-get &>/dev/null; then
    sudo apt-get install -y unzip
  elif command -v dnf &>/dev/null; then
    sudo dnf install -y unzip
  fi
fi

mkdir -p "$INSTALL_DIR"

# 下载并安装 JDK 17 JRE
if ! command -v java &>/dev/null || ! java --version 2>&1 | grep -q "17"; then
  echo "[1/4] 下载 JDK 17 运行时..."
  curl -L -o "/tmp/jdk-17-jre-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/jdk-17-jre-${PLATFORM}.tar.gz"
  echo "解压 JDK 17..."
  tar -xzf "/tmp/jdk-17-jre-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
else
  echo "[1/4] JDK 17 ✅ 已安装"
fi

# 下载并安装 Git
if ! command -v git &>/dev/null; then
  echo "[2/4] 下载 Git..."
  curl -L -o "/tmp/git-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/git-${PLATFORM}.tar.gz"
  tar -xzf "/tmp/git-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
else
  echo "[2/4] Git ✅ 已安装"
fi

# 下载并安装 Python
if ! command -v python3 &>/dev/null; then
  echo "[3/4] 下载 Python..."
  curl -L -o "/tmp/python-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/python-${PLATFORM}.tar.gz"
  tar -xzf "/tmp/python-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
else
  echo "[3/4] Python ✅ 已安装"
fi

# 下载并安装 Node.js
if ! command -v node &>/dev/null; then
  echo "[4/4] 下载 Node.js..."
  curl -L -o "/tmp/node-${PLATFORM}.tar.xz" \
    "${BASE_URL}/${PLATFORM}/node-${PLATFORM}.tar.xz"
  tar -xJf "/tmp/node-${PLATFORM}.tar.xz" -C "$INSTALL_DIR/"
else
  echo "[4/4] Node.js ✅ 已安装"
fi

# 下载 NexusAI.jar
curl -L -o "$INSTALL_DIR/NexusAI.jar" "${BASE_URL}/NexusAI.jar"

# 配置 PATH
PROFILE_RC="$HOME/.profile"
if ! grep -q "NexusAI" "$PROFILE_RC"; then
  echo "" >> "$PROFILE_RC"
  echo "# NexusAI Runtime PATH" >> "$PROFILE_RC"
  echo "export PATH=\"$INSTALL_DIR/java17/bin:$INSTALL_DIR/git/bin:$INSTALL_DIR/python/bin:$INSTALL_DIR/node/bin:\$PATH\"" >> "$PROFILE_RC"
  echo "已追加 PATH 到 $PROFILE_RC"
fi

# 创建 .desktop 快捷方式
mkdir -p "$HOME/.local/share/applications"
cat > "$HOME/.local/share/applications/nexusai.desktop" << EOF
[Desktop Entry]
Name=NexusAI
Comment=NexusAI - AI Assistant
Exec=java -jar $INSTALL_DIR/NexusAI.jar
Icon=$INSTALL_DIR/icon.png
Terminal=false
Type=Application
Categories=Development;
EOF

# 清理
rm -f /tmp/jdk-17-jre-*.tar.gz /tmp/git-*.tar.gz /tmp/python-*.tar.gz /tmp/node-*.tar.xz

echo ""
echo "=== NexusAI 安装完成! ==="
echo "启动: java -jar $INSTALL_DIR/NexusAI.jar"
echo "或从应用菜单中启动"
echo "请运行: source $PROFILE_RC  或重新登录"
```

- [ ] **步骤 2：添加可执行权限**

```bash
chmod +x 'D:/code/ai_project/javaclawbot/installer/linux/install.sh'
```

---

## 任务 6：版本号更新 & 最终验证

**文件：**
- 修改：`D:/code/ai_project/javaclawbot/CHANGELOG.md`
- 修改：`D:/code/ai_project/javaclawbot/pom.xml`

- [ ] **步骤 1：CHANGELOG 新增 2.3.0 条目**

```markdown
## [2.3.0] - 2026-05-10

### Added
- **一键安装包 (Inno Setup)**：Windows 安装向导，自动检测已有 Git/Python/Node.js/JDK 环境，缺失组件从内网自动下载并配置 PATH
- **macOS 安装脚本**：`installer/macos/install.sh` 一键检测+下载+配置
- **Linux 安装脚本**：`installer/linux/install.sh` 支持 apt/dnf，含 .desktop 快捷方式

### Changed
- **项目更名**：javaclawbot → NexusAI（pom.xml artifactId/finalName、脚本、CHANGELOG）
```

- [ ] **步骤 2：pom.xml 版本号更新**

```xml
<version>2.3.0</version>
```

- [ ] **步骤 3：完整构建验证**

```bash
cd 'D:/code/ai_project/javaclawbot'
mvn clean package -Dmaven.test.skip=true
```

验证输出：
- `target/NexusAI.jar` 存在
- 编译无错误

---

## 自我审查

### 1. 规范覆盖检查
| 规范需求 | 对应任务 |
|---------|---------|
| Windows Inno Setup 安装向导 | 任务 3 |
| 环境检测（已安装跳过） | 任务 3（Pascal 检测函数） |
| PATH 用户级配置 | 任务 3（Registry 段） |
| IDP 下载插件 | 任务 3 步骤 1 |
| 内网服务器下载 | 任务 3（RuntimeBaseURL） |
| macOS 脚本 | 任务 4 |
| Linux 脚本 | 任务 5 |
| 项目更名 NexusAI | 任务 2 |
| 运行时依赖准备 | 任务 1 |
| 版本号更新 | 任务 6 |

✅ 全覆盖

### 2. 占位符扫描
无 TBD/TODO/稍后实现。所有步骤含实际代码。

### 3. 类型一致性
- Inno Setup Pascal 函数名在 `[Components]` 和 `[Code]` 中一致 ✅
- Shell 脚本变量引用一致 ✅
- 下载 URL 路径格式统一 ✅
