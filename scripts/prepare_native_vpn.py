#!/usr/bin/env python3
"""Build-time NoraProxy UI overlay onto PINNED upstream; Xray/VpnService untouched."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / "upstream"
APP = UPSTREAM / "V2rayNG" / "app"
SRC = APP / "src/main"
PIN = "9fcb1a30f81345920f9609029ae923b4ea0a866c"
PROTECTED = (
    "java/com/v2ray/ang/core/CoreServiceManager.kt",
    "java/com/v2ray/ang/core/CoreConfigManager.kt",
    "java/com/v2ray/ang/service/CoreVpnService.kt",
)

def replace(path, old, new):
    value = path.read_text(encoding="utf-8")
    if old not in value:
        raise SystemExit("Missing known pinned upstream anchor in " + str(path) + ": " + old[:90])
    path.write_text(value.replace(old, new, 1), encoding="utf-8")

def prepare():
    revision = subprocess.check_output(
        ["git", "-C", str(UPSTREAM), "rev-parse", "HEAD"], text=True
    ).strip()
    if revision != PIN:
        raise SystemExit("Refusing unverified upstream revision: " + revision)
    for name in PROTECTED:
        if not (SRC / name).is_file():
            raise SystemExit("Missing original native VPN runtime: " + name)

    copies = (
        ("native-vpn/NoraMainScreen.kt",
         "java/com/v2ray/ang/ui/main/MainScreen.kt"),
        ("native-vpn/NoraRouteSelector.kt",
         "java/com/v2ray/ang/ui/main/NoraRouteSelector.kt"),
        ("app/src/main/res/drawable/ic_launcher.xml",
         "res/drawable/nora_launcher.xml"),
    )
    for from_name, to_name in copies:
        to = SRC / to_name
        to.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / from_name, to)

    gradle = APP / "build.gradle.kts"
    replace(gradle, 'applicationId = "com.v2ray.ang"', 'applicationId = "app.noraproxy"')
    replace(gradle, 'versionCode = 745', 'versionCode = 200')
    replace(gradle, 'versionName = "2.3.5"', 'versionName = "0.2.0"')
    replace(gradle, 'v2rayNG_', 'NoraProxy_')

    strings = SRC / "res/values/strings.xml"
    replace(strings,
            '<string name="app_name" translatable="false">v2rayNG</string>',
            '<string name="app_name" translatable="false">NoraProxy</string>')

    manifest = SRC / "AndroidManifest.xml"
    replace(manifest, 'android:allowBackup="true"', 'android:allowBackup="false"')
    replace(manifest,
            'android:icon="@mipmap/ic_launcher"',
            'android:icon="@drawable/nora_launcher"')
    marker = (
        '        <activity\n'
        '            android:name=".ui.UrlSchemeActivity"\n'
        '            android:exported="true">'
    )
    replacement = marker + (
        '\n            <intent-filter>\n'
        '                <action android:name="android.intent.action.VIEW" />\n'
        '                <category android:name="android.intent.category.BROWSABLE" />\n'
        '                <category android:name="android.intent.category.DEFAULT" />\n'
        '                <data android:scheme="noraproxy" android:host="setup" />\n'
        '            </intent-filter>'
    )
    replace(manifest, marker, replacement)

    scheme = SRC / "java/com/v2ray/ang/ui/UrlSchemeActivity.kt"
    replace(scheme,
            '                    when (data?.host) {\n'
            '                        "install-config" -> {',
            '                    when (data?.host) {\n'
            '                        "setup" -> {\n'
            '                            val seller = data?.getQueryParameter("seller").orEmpty().take(80)\n'
            '                            if (seller.isNotBlank()) {\n'
            '                                getSharedPreferences("nora_brand", MODE_PRIVATE)\n'
            '                                    .edit().putString("seller", seller).apply()\n'
            '                            }\n'
            '                            val link = data?.getQueryParameter("url").orEmpty()\n'
            '                            if (link.startsWith("https://", ignoreCase = true)) {\n'
            '                                parseUri(link, null)\n'
            '                            }\n'
            '                        }\n'
            '                        "install-config" -> {')

    print("Prepared NoraProxy v0.2.0, embedded Xray/VpnService, package app.noraproxy")

if __name__ == "__main__":
    prepare()
