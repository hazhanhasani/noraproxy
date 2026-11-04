#!/usr/bin/env python3
"""Synchronize first-party Android promotion manifest with uploaded GitHub images.

The workflow runs after files under assets/ads change. Images are public; no
remote advertising endpoint, user information, or API key is required.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

MAX_IMAGE_BYTES = 2 * 1024 * 1024
MAX_BANNERS = 4
SAFE_IMAGE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,72}\.(?:webp|png|jpg|jpeg)$", re.I)


def valid_image(path: Path) -> bool:
    return (
        path.is_file()
        and not path.is_symlink()
        and SAFE_IMAGE.fullmatch(path.name) is not None
        and ".." not in path.name
        and 0 < path.stat().st_size <= MAX_IMAGE_BYTES
    )


def image_timestamp(root: Path, path: Path) -> float:
    """Pick the newest uploads, using Git history rather than checkout mtime."""
    try:
        result = subprocess.run(
            ["git", "-C", str(root), "log", "-1", "--format=%ct",
             "--", str(path.relative_to(root))],
            capture_output=True, text=True, check=True, timeout=10
        ).stdout.strip()
        return float(result) if result else path.stat().st_mtime
    except (subprocess.SubprocessError, OSError, ValueError):
        return path.stat().st_mtime


def valid_target(url: object) -> bool:
    if not isinstance(url, str) or len(url) > 2048:
        return False
    try:
        parsed = urlsplit(url)
        return parsed.scheme == "https" and bool(parsed.hostname) and not (
            parsed.username or parsed.password
        )
    except ValueError:
        return False


def synchronize(root: Path) -> bool:
    root = root.resolve()
    ads = root / "assets" / "ads"
    manifest_path = ads / "manifest.json"
    document = json.loads(manifest_path.read_text(encoding="utf-8"))
    if document.get("version") != 1 or not isinstance(document.get("banners"), list):
        raise ValueError("Unsupported NoraProxy promotion manifest")
    existing = {
        item["file"]: item
        for item in document["banners"]
        if isinstance(item, dict) and isinstance(item.get("file"), str)
    }
    images = sorted(
        (path for path in ads.iterdir() if valid_image(path)),
        key=lambda path: (image_timestamp(root, path), path.name),
        reverse=True,
    )[:MAX_BANNERS]

    banners = []
    for path in images:
        previous = existing.get(path.name, {})
        item = {
            "id": str(previous.get("id") or ("github-" + path.stem))[:70],
            "file": path.name,
            "title": str(previous.get("title") or "تبلیغات NoraProxy")[:80],
            "enabled": previous.get("enabled", True) is not False,
        }
        if valid_target(previous.get("target")):
            item["target"] = previous["target"]
        banners.append(item)
    document["banners"] = banners
    document["rotationSeconds"] = max(3, min(30, int(document.get("rotationSeconds", 8))))
    output = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    if output == manifest_path.read_text(encoding="utf-8"):
        return False
    manifest_path.write_text(output, encoding="utf-8")
    return True


if __name__ == "__main__":
    destination = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    changed = synchronize(destination)
    print("Promotion manifest " + ("updated" if changed else "already up to date"))
