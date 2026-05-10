; ============================================================
; NexusAI Windows Inno Setup 一键安装脚本 (全离线 + 内置下载)
; 生成: NexusAI-Setup-2.3.0.exe
; 使用 Inno Setup 6.1+ 内置 DownloadTemporaryFile，零外部依赖
; ============================================================

#define MyAppName "NexusAI"
#define MyAppVersion "2.3.0"
#define MyAppPublisher "NexusAI"
#define RuntimeBaseURL "http://192.168.20.125:9100/releases/2.3.0/windows"

[Setup]
AppId={{B3C4D5E6-F7A8-9012-BCDE-F12345678901}
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
PrivilegesRequired=lowest

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

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\NexusAI.bat"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\NexusAI.bat"

[Registry]
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; \
  ValueName: "Path"; ValueData: "{olddata};{app}\java17\bin;{app}\git\bin;{app}\python;{app}\node"; \
  Check: NeedPathUpdate; Flags: preservestringtype

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
      WizardForm.ComponentsList.ItemCaption[0] := 'JDK 17 Runtime - Will download ~40MB';

    if IsGitInstalled then
      WizardForm.ComponentsList.ItemCaption[1] := 'Git Version Control - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[1] := 'Git Version Control - Will download ~59MB';

    if IsPythonInstalled then
      WizardForm.ComponentsList.ItemCaption[2] := 'Python Script Engine - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[2] := 'Python Script Engine - Will download ~11MB';

    if IsNodeInstalled then
      WizardForm.ComponentsList.ItemCaption[3] := 'Node.js Engine - Already installed (skip)'
    else
      WizardForm.ComponentsList.ItemCaption[3] := 'Node.js Engine - Will download ~33MB';
  end;
end;

// ========================================================================
// 内置下载 (DownloadTemporaryFile + 重试)
// ========================================================================
var
  DownloadPage: TOutputProgressWizardPage;

function DownloadFileRetry(const URL, DestFile: string; MaxRetries: Integer): Boolean;
var
  Retry: Integer;
begin
  Result := False;
  for Retry := 1 to MaxRetries do
  begin
    Log(Format('Downloading %s (attempt %d)...', [URL, Retry]));
    try
      DownloadTemporaryFile(URL, DestFile, '', '');
      if FileExists(DestFile) then
      begin
        Log('Download succeeded.');
        Result := True;
        Exit;
      end;
    except
      Log(Format('Download failed (attempt %d).', [Retry]));
    end;
    if Retry < MaxRetries then
      Sleep(2000);
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  AppDir, TmpDir, URL, FileName: string;
  TotalSteps, CurrentStep: Integer;
begin
  Result := '';
  NeedsRestart := False;

  AppDir := ExpandConstant('{app}');
  TmpDir := ExpandConstant('{tmp}');

  // 计算总步骤
  TotalSteps := 1; // NexusAI.jar always
  if WizardIsComponentSelected('jdk') and not IsJdk17Installed then TotalSteps := TotalSteps + 1;
  if WizardIsComponentSelected('git') and not IsGitInstalled then TotalSteps := TotalSteps + 1;
  if WizardIsComponentSelected('python') and not IsPythonInstalled then TotalSteps := TotalSteps + 1;
  if WizardIsComponentSelected('nodejs') and not IsNodeInstalled then TotalSteps := TotalSteps + 1;

  DownloadPage := CreateOutputProgressPage('Downloading runtime dependencies...',
    'Downloading from internal server. This may take a few minutes.');

  CurrentStep := 0;

  // ---- 下载 NexusAI.jar ----
  CurrentStep := CurrentStep + 1;
  DownloadPage.SetText(Format('Downloading NexusAI (%d/%d)...', [CurrentStep, TotalSteps]),
    'NexusAI.jar ~270MB');
  DownloadPage.SetProgress(CurrentStep, TotalSteps + 1);
  FileName := TmpDir + '\NexusAI.jar';
  URL := '{#RuntimeBaseURL}/NexusAI.jar';
  if not FileExists(FileName) then
  begin
    if not DownloadFileRetry(URL, FileName, 3) then
    begin
      Result := 'Failed to download NexusAI.jar. Check network.';
      Exit;
    end;
  end;

  // ---- 下载 JDK 17 ----
  if WizardIsComponentSelected('jdk') and not IsJdk17Installed then
  begin
    CurrentStep := CurrentStep + 1;
    DownloadPage.SetText(Format('Downloading JDK 17 (%d/%d)...', [CurrentStep, TotalSteps]),
      'jdk-17-jre-x64.zip ~40MB');
    DownloadPage.SetProgress(CurrentStep, TotalSteps + 1);
    FileName := TmpDir + '\jdk-17-jre-x64.zip';
    URL := '{#RuntimeBaseURL}/jdk-17-jre-x64.zip';
    if not FileExists(FileName) then
    begin
      if not DownloadFileRetry(URL, FileName, 3) then
      begin
        Result := 'Failed to download JDK 17. Check network.';
        Exit;
      end;
    end;
  end;

  // ---- 下载 Git ----
  if WizardIsComponentSelected('git') and not IsGitInstalled then
  begin
    CurrentStep := CurrentStep + 1;
    DownloadPage.SetText(Format('Downloading Git (%d/%d)...', [CurrentStep, TotalSteps]),
      'PortableGit-2.47.0-64-bit.7z.exe ~59MB');
    DownloadPage.SetProgress(CurrentStep, TotalSteps + 1);
    FileName := TmpDir + '\PortableGit-2.47.0-64-bit.7z.exe';
    URL := '{#RuntimeBaseURL}/PortableGit-2.47.0-64-bit.7z.exe';
    if not FileExists(FileName) then
    begin
      if not DownloadFileRetry(URL, FileName, 3) then
      begin
        Result := 'Failed to download Git. Check network.';
        Exit;
      end;
    end;
  end;

  // ---- 下载 Python ----
  if WizardIsComponentSelected('python') and not IsPythonInstalled then
  begin
    CurrentStep := CurrentStep + 1;
    DownloadPage.SetText(Format('Downloading Python (%d/%d)...', [CurrentStep, TotalSteps]),
      'python-3.12.4-embed-amd64.zip ~11MB');
    DownloadPage.SetProgress(CurrentStep, TotalSteps + 1);
    FileName := TmpDir + '\python-3.12.4-embed-amd64.zip';
    URL := '{#RuntimeBaseURL}/python-3.12.4-embed-amd64.zip';
    if not FileExists(FileName) then
    begin
      if not DownloadFileRetry(URL, FileName, 3) then
      begin
        Result := 'Failed to download Python. Check network.';
        Exit;
      end;
    end;
  end;

  // ---- 下载 Node.js ----
  if WizardIsComponentSelected('nodejs') and not IsNodeInstalled then
  begin
    CurrentStep := CurrentStep + 1;
    DownloadPage.SetText(Format('Downloading Node.js (%d/%d)...', [CurrentStep, TotalSteps]),
      'node-v22.12.0-win-x64.zip ~33MB');
    DownloadPage.SetProgress(CurrentStep, TotalSteps + 1);
    FileName := TmpDir + '\node-v22.12.0-win-x64.zip';
    URL := '{#RuntimeBaseURL}/node-v22.12.0-win-x64.zip';
    if not FileExists(FileName) then
    begin
      if not DownloadFileRetry(URL, FileName, 3) then
      begin
        Result := 'Failed to download Node.js. Check network.';
        Exit;
      end;
    end;
  end;

  DownloadPage.SetProgress(TotalSteps + 1, TotalSteps + 1);
end;

// ========================================================================
// 安装后解压
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
    Log('Extracting dependencies...');

    // 复制 JAR
    if FileExists(TmpDir + '\NexusAI.jar') then
      CopyFile(TmpDir + '\NexusAI.jar', AppDir + '\NexusAI.jar', False);

    // 解压 JDK (用 tar 因为 zip 可能没有)
    if WizardIsComponentSelected('jdk') and not IsJdk17Installed and FileExists(TmpDir + '\jdk-17-jre-x64.zip') then
      Exec('powershell.exe', '-NoProfile -Command "Expand-Archive -Path ''' + TmpDir + '\jdk-17-jre-x64.zip'' -DestinationPath ''' + AppDir + '\java17'' -Force"',
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

    // 创建启动批处理
    SaveStringToFile(AppDir + '\NexusAI.bat',
      '@echo off' + #13#10 +
      'start "" "' + AppDir + '\java17\bin\javaw.exe" -jar "' + AppDir + '\NexusAI.jar"' + #13#10,
      False);

    Log('Extraction complete.');
  end;
end;

// ========================================================================
// PATH
// ========================================================================
function NeedPathUpdate: Boolean;
begin
  Result := WizardIsComponentSelected('jdk') or WizardIsComponentSelected('git')
         or WizardIsComponentSelected('python') or WizardIsComponentSelected('nodejs');
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
    if RegQueryStringValue(HKCU, 'Environment', 'Path', CurrentPath) then
    begin
      StringChange(CurrentPath, ';' + AppDir + '\java17\bin', '');
      StringChange(CurrentPath, ';' + AppDir + '\git\bin', '');
      StringChange(CurrentPath, ';' + AppDir + '\python', '');
      StringChange(CurrentPath, ';' + AppDir + '\node', '');
      RegWriteStringValue(HKCU, 'Environment', 'Path', CurrentPath);
    end;
  end;
end;
