#!/usr/bin/env python3
"""Register NoraProxy's fixed Android signing identity as GitHub Actions Secrets.

Needs GitHub CLI (gh), an authenticated repository admin and the private
NoraProxy-signing-backup.zip on the user's own device. Secrets are piped to
gh on stdin, never included in command arguments or written into source.
"""
import base64
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

REPO = "hazhanhasani/noraproxy"
EXPECTED_CERT = "248e68eda1329080c3d05252392c585996b014280c4a380411b0cb3e88ee9a8a"

def fail(message):
    raise SystemExit("ERROR: " + message)

def gh(*args, input_bytes=None):
    run = subprocess.run(
        ["gh", *args], input=input_bytes, capture_output=True, check=False
    )
    if run.returncode:
        fail("GitHub CLI operation failed; confirm 'gh auth login' and Actions "
             "Secrets write permission. " + run.stderr.decode(errors="replace")[:400])
    return run.stdout

def main():
    if not shutil.which("gh"):
        fail("GitHub CLI not installed. Install gh and run 'gh auth login'.")
    if len(sys.argv) != 2:
        fail("Usage: python3 scripts/register_signing_secrets.py /path/to/NoraProxy-signing-backup.zip")

    archive = Path(sys.argv[1]).expanduser().resolve()
    if not archive.is_file():
        fail("Could not find private signing backup ZIP.")
    with zipfile.ZipFile(archive) as z:
        if not {"noraproxy-release.jks", "noraproxy-signing-credentials.txt"}.issubset(z.namelist()):
            fail("Signing archive contents are incomplete.")
        key_bytes = z.read("noraproxy-release.jks")
        lines = z.read("noraproxy-signing-credentials.txt").decode("utf-8").splitlines()
    if not 1024 <= len(key_bytes) <= 100000:
        fail("Invalid PKCS#12 keystore size.")
    keys = {"KEY_ALIAS", "STORE_PASSWORD", "KEY_PASSWORD", "CERT_SHA256"}
    props = dict(line.split("=", 1) for line in lines
                 if "=" in line and line.split("=", 1)[0] in keys)
    if props.get("CERT_SHA256", "").replace(":", "").lower() != EXPECTED_CERT:
        fail("Signing certificate identity does not match pinned release fingerprint.")
    for key in keys:
        if not props.get(key):
            fail("Missing signing metadata: " + key)

    gh("auth", "status")
    gh("repo", "view", REPO, "--json", "nameWithOwner")
    secrets = {
        "NORA_KEYSTORE_BASE64": base64.b64encode(key_bytes),
        "NORA_SIGNING_STORE_PASSWORD": props["STORE_PASSWORD"].encode("utf-8"),
        "NORA_SIGNING_KEY_ALIAS": props["KEY_ALIAS"].encode("utf-8"),
        "NORA_SIGNING_KEY_PASSWORD": props["KEY_PASSWORD"].encode("utf-8"),
    }
    for name, payload in secrets.items():
        gh("secret", "set", name, "--repo", REPO, input_bytes=payload)
        print("Registered:", name)

    existing = gh("secret", "list", "--repo", REPO).decode(errors="replace")
    for name in secrets:
        if name not in existing:
            fail("Could not confirm secret: " + name)
    print("SUCCESS: all four Actions Secrets are registered in " + REPO)
    print("The keystore and passwords were not printed or committed.")

if __name__ == "__main__":
    main()
