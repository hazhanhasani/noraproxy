#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?version is required}"
REPO="${2:?repository owner/name is required}"
BASE="${3:-signed}"
TAG="v$VERSION"
FILE="NoraProxy-$VERSION.apk"
RECEIPT="NoraProxy-$VERSION.telegram.json"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Invalid release version"
  exit 1
fi
if ! [[ "$REPO" =~ ^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$ ]]; then
  echo "::error::Invalid repository"
  exit 1
fi

# A receipt is published only after a confirmed Telegram send.
if gh release view "$TAG" --repo "$REPO" --json assets \
  --jq '.assets[].name' | grep -Fxq "$RECEIPT"; then
  echo "Already posted $TAG to Telegram. Skipping duplicate."
  exit 0
fi

missing=()
for key in NORA_TG_API_ID NORA_TG_API_HASH NORA_TG_BOT_TOKEN NORA_TG_CHANNEL_ID; do
  if [[ -z "${!key:-}" ]]; then
    missing+=("$key")
  fi
done
if ((${#missing[@]})); then
  if [[ "${NORA_TG_OPTIONAL:-false}" == "true" ]]; then
    echo "::warning::Telegram is not configured: ${missing[*]} missing. Official GitHub Release remains available."
    exit 0
  fi
  echo "::error::Missing GitHub Secrets: ${missing[*]}"
  exit 1
fi

mkdir -p "$BASE"
if [[ ! -s "$BASE/$FILE" || ! -s "$BASE/$FILE.sha256" ]]; then
  echo "Downloading verified APK and checksum from GitHub Releases..."
  gh release download "$TAG" --repo "$REPO" \
    --pattern "$FILE" --pattern "$FILE.sha256" --dir "$BASE"
fi
(cd "$BASE" && sha256sum --check "$FILE.sha256")

CAPTION="${RUNNER_TEMP:-/tmp}/noraproxy-$VERSION-caption.html"
RECEIPT_FILE="${RUNNER_TEMP:-/tmp}/$RECEIPT"
python3 scripts/telegram_release.py caption \
  --version "$VERSION" --repo "$REPO" --output "$CAPTION"
python3 -m pip install --disable-pip-version-check --quiet 'telethon==1.45.0'
python3 scripts/telegram_release.py send \
  --version "$VERSION" --repo "$REPO" \
  --apk "$BASE/$FILE" --checksum "$BASE/$FILE.sha256" \
  --caption "$CAPTION" --receipt "$RECEIPT_FILE"

# Telegram upload succeeded: save public metadata as an idempotency marker.
gh release upload "$TAG" "$RECEIPT_FILE" --repo "$REPO"
echo "NoraProxy $TAG posted to Telegram; receipt saved."
