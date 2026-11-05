#!/usr/bin/env python3
"""Configure trusted NoraProxy APK signing without storing secrets in Git.

Gradle Kotlin DSL no longer relies on environment variables being present inside
its worker process. Credentials are read from a 0600 temporary file and the
build script stores only the non-secret file path.
"""
from pathlib import Path
import base64
import json
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
    raise SystemExit("Required signing environment variables are missing: " + ", ".join(missing))

store = Path(os.environ["NORA_SIGNING_STORE_FILE"]).resolve()
if not store.is_file():
    raise SystemExit("Release signing keystore file is missing.")

# Temporary file is outside the checkout, never included in a Git or APK
# artifact. Values are encoded to avoid Java .properties delimiter escaping.
credentials = store.with_suffix(".signing.properties")
with credentials.open("x", encoding="ascii") as stream:
    for name in env_names:
        value = os.environ[name].encode("utf-8")
        encoded = base64.b64encode(value).decode("ascii")
        stream.write(name + "=" + encoded + "\n")
credentials.chmod(0o600)

source = "import java.io.File\nimport java.util.Base64\nimport java.util.Properties\n" + gradle.read_text(encoding="utf-8")
marker = "    buildTypes {\n        debug {"
if source.count(marker) != 1:
    raise SystemExit("Expected one upstream buildTypes.debug anchor.")

# No secret value is embedded into the Gradle source or logged. Debug and
# release use separate signing configurations and the same production key.
inject = """    val noraSigningProperties = Properties().apply {
        File(NORA_SIGNING_PROPERTIES_PATH).inputStream().use { load(it) }
    }
    val noraSigningValue: (String) -> String = { name ->
        String(
            Base64.getDecoder().decode(
                requireNotNull(noraSigningProperties.getProperty(name))
            ), Charsets.UTF_8
        )
    }

    signingConfigs {
        getByName("debug") {
            storeFile = File(noraSigningValue("NORA_SIGNING_STORE_FILE"))
            storePassword = noraSigningValue("NORA_SIGNING_STORE_PASSWORD")
            keyAlias = noraSigningValue("NORA_SIGNING_KEY_ALIAS")
            keyPassword = noraSigningValue("NORA_SIGNING_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
        create("noraRelease") {
            storeFile = File(noraSigningValue("NORA_SIGNING_STORE_FILE"))
            storePassword = noraSigningValue("NORA_SIGNING_STORE_PASSWORD")
            keyAlias = noraSigningValue("NORA_SIGNING_KEY_ALIAS")
            keyPassword = noraSigningValue("NORA_SIGNING_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    require(signingConfigs.getByName("debug").storeFile?.isFile == true) {
        "NoraProxy trusted debug keystore was not configured."
    }
    require(signingConfigs.getByName("noraRelease").storeFile?.isFile == true) {
        "NoraProxy release keystore was not configured."
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")"""
inject = inject.replace("NORA_SIGNING_PROPERTIES_PATH", json.dumps(str(credentials)))
source = source.replace(marker, inject, 1)
release = "        release {"
if source.count(release) != 1:
    raise SystemExit("Expected one upstream release buildType anchor.")
source = source.replace(release,
    release + '\n            signingConfig = signingConfigs.getByName("noraRelease")', 1)
gradle.write_text(source, encoding="utf-8")
print("Trusted debug/release signing configured using protected temporary credentials.")
