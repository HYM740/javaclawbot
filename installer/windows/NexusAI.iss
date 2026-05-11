; ============================================================
; NexusAI Windows 全离线一键安装脚本
; 生成: NexusAI-Setup-2.3.2.exe
; 所有运行时依赖预打包于安装器内，零网络下载，零外部依赖
; 构建前需: 1) mvn package 生成 NexusAI.jar
;           2) runtime/ 目录准备好 JDK/Git/Python/Node.js 离线包
; ============================================================

#define MyAppName "NexusAI"
#define MyAppVersion "2.3.2"
#define MyAppPublisher "NexusAI"
; 全离线安装 — 运行时依赖从本地目录捆绑
#define RuntimeDir "..\runtime"
#define JarSource "..\..\target\NexusAI.jar"

[Setup]
AppId={{B3C4D5E6-F7A8-9012-BCDE-F12345678901}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
; 确保显示目录选择页面（默认已启用，显式声明防止意外覆盖）
DisableDirPage=no
DirExistsWarning=yes
OutputDir=..\..\dist
OutputBaseFilename=NexusAI-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#MyAppName}
PrivilegesRequired=lowest
ChangesEnvironment=yes
SetupIconFile=app-icon.ico
UninstallDisplayIcon={app}\app-icon.ico

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Types]
Name: "full"; Description: "Complete installation"
Name: "custom"; Description: "Custom installation"; Flags: iscustom

[Components]
Name: "jdk"; Description: "JDK 17 Runtime"; Types: full custom
Name: "git"; Description: "Git Version Control"; Types: full custom
Name: "python"; Description: "Python Script Engine"; Types: full custom
Name: "nodejs"; Description: "Node.js JavaScript Engine"; Types: full custom

[Files]
; 图标
Source: "app-icon.ico"; DestDir: "{app}"; Flags: ignoreversion

; 主程序 JAR — 从本地 target/ 目录捆绑（需先 mvn package）
Source: "{#JarSource}"; DestDir: "{app}"; Flags: ignoreversion

; 运行时依赖 — 从本地 runtime/ 目录捆绑至临时目录，安装后解压
; 已安装组件自动跳过（Check: not IsXxxInstalled），节省安装包体积和安装时间
Source: "{#RuntimeDir}\jdk-17-x64.zip"; DestDir: "{tmp}"; \
  Components: jdk; Check: not IsJdk17Installed; Flags: deleteafterinstall

Source: "{#RuntimeDir}\PortableGit-2.47.0-64-bit.7z.exe"; DestDir: "{tmp}"; \
  Components: git; Check: not IsGitInstalled; Flags: deleteafterinstall

Source: "{#RuntimeDir}\python-3.12.4-embed-amd64.zip"; DestDir: "{tmp}"; \
  Components: python; Check: not IsPythonInstalled; Flags: deleteafterinstall

Source: "{#RuntimeDir}\node-v22.12.0-win-x64.zip"; DestDir: "{tmp}"; \
  Components: nodejs; Check: not IsNodeInstalled; Flags: deleteafterinstall

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\NexusAI.bat"; IconFilename: "{app}\app-icon.ico"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\NexusAI.bat"; IconFilename: "{app}\app-icon.ico"

[Registry]
; ---- PATH 环境变量 (用户级，逐条条件写入) ----
; 1) 始终将 {app} 加入 PATH，确保 nexusai.cmd 全局可用（已存在则跳过）
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}"; \
  Check: NeedAppInPath; Flags: preservestringtype

; 2) 仅当安装了我们内置的 JDK 时，才将 java17\bin 加入 PATH
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}\java17\bin"; \
  Check: NeedJdkHome; Flags: preservestringtype

; 3) 仅当安装了我们内置的 Git 时，才将 git\bin, git\cmd, git\usr\bin 加入 PATH
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}\git\bin;{app}\git\cmd;{app}\git\usr\bin"; \
  Check: NeedGitPath; Flags: preservestringtype

; 4) 仅当安装了我们内置的 Python 时，才将 python 加入 PATH
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}\python"; \
  Check: NeedPythonPath; Flags: preservestringtype

; 5) 仅当安装了我们内置的 Node.js 时，才将 node 加入 PATH
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}\node"; \
  Check: NeedNodePath; Flags: preservestringtype

; ---- 其他环境变量 ----
; 永久设置 NEXUS_HOME，方便后续脚本和工具获取安装路径
Root: HKCU; Subkey: "Environment"; ValueType: string; \
  ValueName: "NEXUS_HOME"; ValueData: "{app}"; \
  Flags: preservestringtype

; 仅当安装了内置 JDK 时，设置 JAVA_HOME
Root: HKCU; Subkey: "Environment"; ValueType: string; \
  ValueName: "JAVA_HOME"; ValueData: "{app}\java17"; \
  Check: NeedJdkHome; Flags: preservestringtype

[Run]
Filename: "{app}\NexusAI.bat"; Description: "Launch {#MyAppName}"; \
  Flags: nowait postinstall skipifsilent

[Code]
// ========================================================================
// 环境检测
// ========================================================================
function IsJdk17Installed: Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('cmd.exe', '/c java --version 2>&1 | find "17"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

function IsGitInstalled: Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('cmd.exe', '/c git --version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

function IsPythonInstalled: Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('cmd.exe', '/c python --version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

function IsNodeInstalled: Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('cmd.exe', '/c node --version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

// ========================================================================
// 组件页面 — 已安装项标注状态
// ========================================================================
procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpSelectComponents then
  begin
    if IsJdk17Installed then
      WizardForm.ComponentsList.ItemCaption[0] := 'JDK 17 Runtime - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[0] := 'JDK 17 Runtime - Bundled (~150MB)';

    if IsGitInstalled then
      WizardForm.ComponentsList.ItemCaption[1] := 'Git Version Control - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[1] := 'Git Version Control - Bundled (~59MB)';

    if IsPythonInstalled then
      WizardForm.ComponentsList.ItemCaption[2] := 'Python Script Engine - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[2] := 'Python Script Engine - Bundled (~11MB)';

    if IsNodeInstalled then
      WizardForm.ComponentsList.ItemCaption[3] := 'Node.js Engine - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[3] := 'Node.js Engine - Bundled (~33MB)';
  end;
end;

// ========================================================================
// 安装后解压 (运行时依赖由 [Files] 段已复制到 {tmp})
// ========================================================================
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  AppDir, TmpDir: string;
begin
  AppDir := ExpandConstant('{app}');
  TmpDir := ExpandConstant('{tmp}');

  if CurStep = ssPostInstall then
  begin
    Log('Extracting bundled runtime dependencies...');

    // 解压完整 JDK 17 到 java17 目录
    if WizardIsComponentSelected('jdk') and not IsJdk17Installed and FileExists(TmpDir + '\jdk-17-x64.zip') then
      Exec('powershell.exe', '-NoProfile -Command "Expand-Archive -Path ''' + TmpDir + '\jdk-17-x64.zip'' -DestinationPath ''' + AppDir + '\java17'' -Force"',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // Git 自解压
    if WizardIsComponentSelected('git') and not IsGitInstalled and FileExists(TmpDir + '\PortableGit-2.47.0-64-bit.7z.exe') then
      Exec(TmpDir + '\PortableGit-2.47.0-64-bit.7z.exe', '-o"' + AppDir + '\git" -y',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // Python zip
    if WizardIsComponentSelected('python') and not IsPythonInstalled and FileExists(TmpDir + '\python-3.12.4-embed-amd64.zip') then
      Exec('powershell.exe', '-NoProfile -Command "Expand-Archive -Path ''' + TmpDir + '\python-3.12.4-embed-amd64.zip'' -DestinationPath ''' + AppDir + '\python'' -Force"',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // Node zip
    if WizardIsComponentSelected('nodejs') and not IsNodeInstalled and FileExists(TmpDir + '\node-v22.12.0-win-x64.zip') then
      Exec('powershell.exe', '-NoProfile -Command "Expand-Archive -Path ''' + TmpDir + '\node-v22.12.0-win-x64.zip'' -DestinationPath ''' + AppDir + '\node'' -Force"',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // 创建全局命令行启动器 nexusai.cmd
    // 优先使用注册表设置的 NEXUS_HOME，回退到安装路径
    SaveStringToFile(AppDir + '\nexusai.cmd',
      '@echo off' + #13#10 +
      'if not defined NEXUS_HOME set "NEXUS_HOME=' + AppDir + '"' + #13#10 +
      'java --version 2>&1 | find "17" >nul' + #13#10 +
      'if %ERRORLEVEL% EQU 0 (' + #13#10 +
      '  java -Dfile.encoding=utf-8 -jar "%NEXUS_HOME%\NexusAI.jar" %*' + #13#10 +
      '  goto :eof' + #13#10 +
      ')' + #13#10 +
      'if exist "%NEXUS_HOME%\java17\bin\java.exe" (' + #13#10 +
      '  "%NEXUS_HOME%\java17\bin\java.exe" -Dfile.encoding=utf-8 -jar "%NEXUS_HOME%\NexusAI.jar" %*' + #13#10 +
      '  goto :eof' + #13#10 +
      ')' + #13#10 +
      'echo ERROR: Java 17 not found.' + #13#10 +
      'pause >nul',
      False);

    // 创建桌面/开始菜单启动批处理
    // 优先使用注册表设置的 NEXUS_HOME，回退到安装路径
    SaveStringToFile(AppDir + '\NexusAI.bat',
      '@echo off' + #13#10 +
      'if not defined NEXUS_HOME set "NEXUS_HOME=' + AppDir + '"' + #13#10 +
      'java --version 2>&1 | find "17" >nul' + #13#10 +
      'if %ERRORLEVEL% EQU 0 (' + #13#10 +
      '  start "" java -Dfile.encoding=utf-8 -jar "%NEXUS_HOME%\NexusAI.jar"' + #13#10 +
      '  goto :eof' + #13#10 +
      ')' + #13#10 +
      'if exist "%NEXUS_HOME%\java17\bin\java.exe" (' + #13#10 +
      '  start "" "%NEXUS_HOME%\java17\bin\java.exe" -Dfile.encoding=utf-8 -jar "%NEXUS_HOME%\NexusAI.jar"' + #13#10 +
      '  goto :eof' + #13#10 +
      ')' + #13#10 +
      'echo ERROR: Java 17 not found.' + #13#10 +
      'pause >nul',
      False);

    Log('Extraction complete.');
  end;
end;

// ========================================================================
// PATH 条件判断 (仅当组件被选中且系统未安装时，才添加对应 PATH)
// ========================================================================

// {app} 去重检查 — 避免重复安装时 PATH 重复追加
function NeedAppInPath: Boolean;
var
  CurrentPath, AppDir: string;
begin
  AppDir := ExpandConstant('{app}');
  Result := True;
  if RegQueryStringValue(HKCU, 'Environment', 'Path', CurrentPath) then
    if Pos(AppDir, CurrentPath) > 0 then
      Result := False;
end;

// 仅当选中 JDK 组件且系统无 JDK 17 时
function NeedJdkHome: Boolean;
begin
  Result := WizardIsComponentSelected('jdk') and not IsJdk17Installed;
end;

// 仅当选中 Git 组件且系统无 Git 时
function NeedGitPath: Boolean;
begin
  Result := WizardIsComponentSelected('git') and not IsGitInstalled;
end;

// 仅当选中 Python 组件且系统无 Python 时
function NeedPythonPath: Boolean;
begin
  Result := WizardIsComponentSelected('python') and not IsPythonInstalled;
end;

// 仅当选中 Node.js 组件且系统无 Node.js 时
function NeedNodePath: Boolean;
begin
  Result := WizardIsComponentSelected('nodejs') and not IsNodeInstalled;
end;

// ========================================================================
// 卸载清理
// ========================================================================
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  CurrentPath, AppDir: string;
begin
  AppDir := ExpandConstant('{app}');
  if CurUninstallStep = usPostUninstall then
  begin
    // 清理 PATH 环境变量中的所有安装目录条目
    if RegQueryStringValue(HKCU, 'Environment', 'Path', CurrentPath) then
    begin
      StringChange(CurrentPath, ';' + AppDir, '');
      StringChange(CurrentPath, ';' + AppDir + '\java17\bin', '');
      StringChange(CurrentPath, ';' + AppDir + '\git\bin', '');
      StringChange(CurrentPath, ';' + AppDir + '\git\cmd', '');
      StringChange(CurrentPath, ';' + AppDir + '\git\usr\bin', '');
      StringChange(CurrentPath, ';' + AppDir + '\python', '');
      StringChange(CurrentPath, ';' + AppDir + '\node', '');
      RegWriteStringValue(HKCU, 'Environment', 'Path', CurrentPath);
    end;
    // 清理 NEXUS_HOME 和 JAVA_HOME 环境变量
    RegDeleteValue(HKCU, 'Environment', 'NEXUS_HOME');
    RegDeleteValue(HKCU, 'Environment', 'JAVA_HOME');
  end;
end;
