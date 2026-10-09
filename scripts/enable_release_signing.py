#!/usr/bin/env python3
"""Fail-closed signing config for official NoraProxy release APK builds.

Never writes private credentials to Git, build.gradle.kts, or log output.
Only invoked after NoraProxy pinned runtime overlay is applied.
"""
from pathlib import Path
import os

root = Path(__file__).resolve().parents[1]
gradle = root / "upstream/V2rayNG/app/build.gradle.kts"

env_names = (
    "NORA_SIGNING_STORE_FILE",
    "NORA_SIGNING_STORE_PASSWORD",
    "NORA_SIGNING_KEY_ALIAS",
    "NORA_SIGNING_KEY_PASSWORD",
)
missing = [name for name in env_names if not os.getenv(name)]
if missing:
    raise SystemExit("Required release-signing environment secrets are missing: " + ", ".join(missing))

if not Path(os.environ["NORA_SIGNING_STORE_FILE"]).is_file():
    raise SystemExit("Release signing keystore file is missing.")

text = gradle.read_text(encoding="utf-8")
marker = "    buildTypes {\n        release {"
if text.count(marker) != 1:
    raise SystemExit("Expected one upstream buildTypes.release anchor.")

inject = '''    signingConfigs {
        create("noraRelease") {
            storeFile = file(System.getenv("NORA_SIGNING_STORE_FILE"))
            storePassword = System.getenv("NORA_SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("NORA_SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("NORA_SIGNING_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("noraRelease")'''
text = text.replace(marker, inject, 1)
gradle.write_text(text, encoding="utf-8")
print("NoraProxy release signing configured from private CI secrets (values not printed).")
