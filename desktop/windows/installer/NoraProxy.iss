; Self-contained NoraProxy WPF desktop PREVIEW installer, not a working VPN.
; Per-user install/upgrade: no elevation, no drivers, DPAPI account store retained.
#define MyAppName "NoraProxy"
#define MyAppVersion "0.1.2"
#define MyAppExe "NoraProxy.exe"

[Setup]
AppId={{C1816051-D2AF-4AB6-8BCE-78EAD417DF3D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName=NoraProxy Windows Preview {#MyAppVersion}
AppPublisher=NoraProxy
AppPublisherURL=https://github.com/hazhanhasani/noraproxy
AppSupportURL=https://github.com/hazhanhasani/noraproxy/issues
DefaultDirName={localappdata}\Programs\NoraProxy
DefaultGroupName=NoraProxy
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
OutputBaseFilename=NoraProxy-Setup-{#MyAppVersion}-win-x64
Compression=lzma2
SolidCompression=yes
UninstallDisplayIcon={app}\{#MyAppExe}
CloseApplications=yes
RestartApplications=no
UsePreviousAppDir=yes
SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "..\..\..\out\windows\win-x64\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\NoraProxy"; Filename: "{app}\{#MyAppExe}"; WorkingDir: "{app}"
Name: "{autodesktop}\NoraProxy"; Filename: "{app}\{#MyAppExe}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExe}"; Description: "Launch NoraProxy"; Flags: postinstall nowait skipifsilent
