# NoraProxy for Windows — bootstrap

## Current status

This folder introduces a **native Windows WPF desktop application** using .NET 10
LTS and a dedicated Windows CI runner. It is **NOT yet a VPN client**. The
Connect button is intentionally disabled until a tested native Windows tunnel
is available. No fake server list, fake delay or fake Connected status.

The Windows client is a separate product target. Android's Java/Kotlin UI,
`VpnService`, `libv2ray.aar`, and Android-specific TUN device cannot be copied
into an EXE. Existing Android builds and signing remain unchanged.

## What the preview does

- Persian RTL dark desktop navigation (Home, Locations, Subscriptions, Settings).
- Offline import of per-user HTTPS subscription URLs and raw VLESS, VMess,
  Trojan, Shadowsocks, Hysteria2 and TUIC share URIs (one per line).
- Reject unknown/insecure link schemes; no network downloads or core execution.
- Store imported bearer URLs and credentials in Windows user-scoped DPAPI
  encrypted storage under `%LOCALAPPDATA%\NoraProxy\profiles.dpapi`.
  The UI deliberately hides raw share links after importing them.
- Fail closed on a corrupted/unreadable encrypted profile rather than silently
  overwriting it. Imports are committed to storage before the UI confirms them.
- Run Windows-only smoke/integration tests and produce a portable, unsigned,
  self-contained `win-x64` preview ZIP as a **GitHub Actions artifact**, not an
  official signed release or Telegram update.

## Build (Windows 10/11 x64)

Requires Visual Studio 2026 or .NET SDK 10 with WPF targeting components.

```powershell
dotnet build desktop/windows/NoraProxy.Windows/NoraProxy.Windows.csproj -c Release
dotnet run --project desktop/windows/NoraProxy.Windows.Tests/NoraProxy.Windows.Tests.csproj -c Release
dotnet publish desktop/windows/NoraProxy.Windows/NoraProxy.Windows.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:PublishTrimmed=false -o out/windows/win-x64
```

Alternatively run GitHub Actions → `NoraProxy Windows Desktop Preview`. The
ZIP contains `NoraProxy.exe` and an explicit non-VPN disclosure. The executable
is not code-signed; Windows SmartScreen may warn on unknown publishers.

## Next milestones before a public VPN build

1. **Core interface:** select/pin a Windows-compatible Xray-core version,
   verify its release integrity and license notices; implement a process
   supervisor with controlled exit, restart, lifecycle and safe redacted logs.
2. **Import:** safely fetch HTTPS subscriptions and parse proxy profiles and
   provider quota metadata, without exposing private subscription tokens.
3. **Windows tunneling:** integrate a vetted and signed Windows TUN implementation
   with appropriately scoped privileged helper; implement DNS, routes,
   kill-switch/leak protections and rollback on crashes. Never download or
   invoke arbitrary admin commands from untrusted subscription data.
4. **Connectivity:** actual measured latency, one-country grouping, favorite
   locations, connect/disconnect UX, controlled failover and network-change
   detection. Only report VPN-connected after verified runtime state.
5. **Distribution:** multi-arch `win-x64`/`win-arm64` validation, signed binaries,
   installer, auto-updates with signature verification, rollback, integration
   and physical Windows device testing.

**Security:** Treat subscription URLs as bearer credentials. Avoid telemetry of
raw links, passwords, server addresses, private account tokens or config blobs.
DPAPI is tied to the Windows user profile: encrypted files cannot be moved to
another user/machine as an export without a separately designed opt-in process.
No Windows-only code or unsigned preview should replace the signed Android APK.


## Windows installable setup (v0.1.1)

The Windows-only CI now produces **NoraProxy-Setup-0.1.1-win-x64.exe**
using Inno Setup. Double-click to install on Windows 10/11 x64. The
setup contains the self-contained .NET 10 desktop app: no separate .NET
runtime or administrator permission is required. The installer adds a
Start Menu shortcut, optional Desktop shortcut and Windows Apps uninstaller.

A Windows runner verifies installing and uninstalling the EXE. Upgrades
preserve per-user encrypted subscriptions under
`%LOCALAPPDATA%\\NoraProxy\\profiles.dpapi`, even after uninstall.
This installer is **unsigned** and **does not provide VPN connectivity yet**.
No Xray, TUN driver, DNS changes or Windows routing changes are installed.
Only use files from the official repository; Windows SmartScreen may warn.

To build with Inno Setup 6 on Windows after `dotnet publish`, compile
`desktop/windows/installer/NoraProxy.iss` using ISCC.exe and point output
to `out/windows/installer/`.
