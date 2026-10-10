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
        ("native-vpn/NoraUpdater.kt",
         "java/com/v2ray/ang/ui/main/NoraUpdater.kt"),
        ("native-vpn/NoraInstallResultActivity.kt",
         "java/com/v2ray/ang/ui/main/NoraInstallResultActivity.kt"),
        ("native-vpn/assets/nora_icon.jpg",
         "res/drawable/nora_brand.jpg"),
        ("native-vpn/res/nora_provider_paths.xml",
         "res/xml/nora_provider_paths.xml"),
        ("native-vpn/res/nora_tab_home.xml", "res/drawable/nora_tab_home.xml"),
        ("native-vpn/res/nora_tab_locations.xml", "res/drawable/nora_tab_locations.xml"),
        ("native-vpn/res/nora_tab_subscription.xml", "res/drawable/nora_tab_subscription.xml"),
        ("native-vpn/res/nora_tab_settings.xml", "res/drawable/nora_tab_settings.xml"),
        ("app/src/main/res/drawable/ic_launcher.xml",
         "res/drawable/nora_launcher.xml"),
    )
    for from_name, to_name in copies:
        to = SRC / to_name
        to.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / from_name, to)

    gradle = APP / "build.gradle.kts"
    replace(gradle, 'applicationId = "com.v2ray.ang"', 'applicationId = "app.noraproxy"')
    replace(gradle, 'versionCode = 745', 'versionCode = 205')
    replace(gradle, 'versionName = "2.3.5"', 'versionName = "0.2.5"')
    # Both F-Droid and Play Store output names must use NoraProxy.
    output_names = gradle.read_text(encoding="utf-8")
    if "v2rayNG_" not in output_names:
        raise SystemExit("Upstream APK output name anchor is missing.")
    gradle.write_text(output_names.replace("v2rayNG_", "NoraProxy_"), encoding="utf-8")

    # Android resolves values-fa/strings.xml for Persian phones; overriding
    # only values/strings.xml leaves the launcher named v2rayNG on MIUI.
    found_app_names = 0
    for strings in sorted((SRC / "res").glob("values*/strings.xml")):
        current = strings.read_text(encoding="utf-8")
        old_name = '<string name="app_name" translatable="false">v2rayNG</string>'
        if old_name in current:
            strings.write_text(current.replace(old_name,
                '<string name="app_name" translatable="false">NoraProxy</string>'),
                encoding="utf-8")
            found_app_names += 1
    if found_app_names == 0:
        raise SystemExit("Unable to find localized upstream app labels.")

    manifest = SRC / "AndroidManifest.xml"
    replace(manifest, 'android:allowBackup="true"', 'android:allowBackup="false"')
    replace(manifest,
            'android:icon="@mipmap/ic_launcher"',
            'android:icon="@drawable/nora_brand"\n'
            '        android:roundIcon="@drawable/nora_brand"')
    replace(manifest,
            'android:label="@string/app_name"',
            'android:label="NoraProxy"')
    replace(manifest,
            '<uses-permission android:name="android.permission.INTERNET" />',
            '<uses-permission android:name="android.permission.INTERNET" />\n'
            '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />')
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

    provider = (
        '        <provider\n'
        '            android:name="androidx.core.content.FileProvider"\n'
        '            android:authorities="app.noraproxy.updates"\n'
        '            android:exported="false"\n'
        '            android:grantUriPermissions="true">\n'
        '            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"\n'
        '                android:resource="@xml/nora_provider_paths" />\n'
        '        </provider>\n'
    )
    callback_activity = (
        '        <activity\n'
        '            android:name=".ui.main.NoraInstallResultActivity"\n'
        '            android:exported="false"\n'
        '            android:theme="@android:style/Theme.Translucent.NoTitleBar" />\n'
    )
    replace(manifest, '    </application>',
            callback_activity + provider + '    </application>')

    activity = SRC / "java/com/v2ray/ang/ui/main/MainActivity.kt"
    replace(activity,
            '            LauncherManager.restartService(this)\n'
            '        }\n'
            '    }\n\n'
            '    override fun onKeyDown',
            '            if (mainViewModel.uiState.value.isRunning) LauncherManager.restartService(this)\n'
            '        }\n'
            '    }\n\n'
            '    override fun onKeyDown')

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

    print("Prepared NoraProxy v0.2.5, embedded Xray/VpnService, package app.noraproxy")

if __name__ == "__main__":
    prepare()
