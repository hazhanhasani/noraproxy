#!/usr/bin/env python3
"""NoraProxy Telegram release caption + large APK delivery via bot MTProto.

Render: python3 scripts/telegram_release.py caption --version 0.2.4 --repo owner/repo --output /tmp/caption.html
Send:   python3 scripts/telegram_release.py send --version 0.2.4 --repo owner/repo \
          --apk signed/NoraProxy-0.2.4.apk --checksum signed/NoraProxy-0.2.4.apk.sha256 \
          --caption /tmp/caption.html --receipt /tmp/NoraProxy-0.2.4.telegram.json

Never print bot credentials or persist a Telegram login session to GitHub.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys

VERSION_PATTERN = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
REPO_PATTERN = re.compile(r"^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$")
ROOT = Path(__file__).resolve().parents[1]

# Telegram paragraphs contain mixed Persian, Latin version numbers and emoji.
RTL_MARK = "\u200f"
MAX_CAPTION_UNITS = 1024
CAPTION_BUDGET = 960  # Reserve space for Telegram HTML/entity processing.

def caption_units(value: str) -> int:
    """Count rendered text as Telegram does: UTF-16 units after HTML parsing."""
    visible = html.unescape(re.sub(r"<[^>]*>", "", value))
    return len(visible.encode("utf-16-le")) // 2

# User-facing fallback headings derived conservatively from conventional commits.
# Prefer carefully edited release-notes/vX.Y.Z.fa.txt for detailed announcements.
CATEGORIES = (
    (r"telegram|channel|announcement|mtproto", "ارسال خودکار فایل نصب و اطلاعیه انتشار در تلگرام"),
    (r"ota|update|release|publish|distribution", "بهبود سامانه دریافت و انتشار نسخه‌های جدید"),
    (r"logo|icon|branding|brand|launcher|theme|ui|layout|nav|font", "بهبود طراحی، آیکن‌ها و تجربه کاربری"),
    (r"route|country|location|server|vpn|xray|connection|tunnel", "بهینه‌سازی انتخاب لوکیشن و عملکرد اتصال"),
    (r"sign|certificate|security|checksum|integrity", "تقویت کنترل امنیت، اعتبار فایل و امضای نسخه"),
    (r"crash|bug|fix|stabil|reliab", "رفع اشکالات و ارتقای پایداری برنامه"),
    (r"perf|speed|latency|ping", "بهبود سرعت و اندازه‌گیری کیفیت سرورها"),
)

def check_version(version: str) -> str:
    if not VERSION_PATTERN.fullmatch(version):
        raise ValueError("Invalid semantic version: " + version)
    return version

def check_repo(repo: str) -> str:
    if not REPO_PATTERN.fullmatch(repo):
        raise ValueError("Invalid repository name")
    return repo

def notes_from_file(version: str) -> list[str]:
    notes = ROOT / "release-notes" / f"v{version}.fa.txt"
    if not notes.is_file():
        return []
    output = []
    for line in notes.read_text(encoding="utf-8").splitlines():
        clean = re.sub(r"^[•*+\-\u2022\s]+", "", line.strip())
        # Persian ZWNJ (نیم‌فاصله) is not printable() but must be preserved.
        clean = "".join(c for c in clean if c.isprintable() or c == "\u200c")
        if not clean or clean.startswith("#"):
            continue
        # Version headings are metadata, not a change to repeat in the bullets.
        if re.search(rf"\bNoraProxy\s*(?:\|\s*)?v?{re.escape(version)}\b", clean, re.I):
            continue
        if clean not in output:
            output.append(clean[:200])
    return output[:6]

def notes_from_git() -> list[str]:
    try:
        tags = subprocess.run(
            ["git", "tag", "--list", "v[0-9]*", "--sort=-version:refname"],
            cwd=ROOT, text=True, capture_output=True, check=True, timeout=15
        ).stdout.splitlines()
        baseline = tags[0] if tags else None
        cmd = ["git", "log", "--format=%s", "-40"]
        if baseline:
            cmd = ["git", "log", "--format=%s", f"{baseline}..HEAD"]
        subjects = subprocess.run(
            cmd, cwd=ROOT, text=True, capture_output=True, check=True, timeout=15
        ).stdout.splitlines()
    except (OSError, subprocess.SubprocessError):
        return []
    found: list[str] = []
    for subject in subjects:
        if subject.startswith("Merge ") or subject.startswith("Revert "):
            continue
        for pattern, summary in CATEGORIES:
            if re.search(pattern, subject, re.I):
                if summary not in found:
                    found.append(summary)
                break
    return found[:5]

def get_notes(version: str) -> list[str]:
    # A curated release-specific change log always takes priority.
    return notes_from_file(version) or notes_from_git() or [
        "اصلاحات و بهینه‌سازی‌های ثبت‌شده در این انتشار"
    ]

def compose_caption(version: str, repo: str, changes: list[str]) -> str:
    check_version(version)
    check_repo(repo)
    # RLM at each paragraph start fixes mixed RTL/LTR rendering; a blank line
    # between every change keeps wrapped notes legible on narrow Telegram screens.
    header = (
        f"{RTL_MARK}🚀 <b>NoraProxy | v{version}</b>\n"
        "━━━━━━━━━━━━━━━━\n"
        f"{RTL_MARK}✨ <b>نسخه جدید منتشر شد!</b>\n"
        f"{RTL_MARK}اتصال هوشمند، تجربه‌ای روان‌تر.\n\n"
        f"{RTL_MARK}🛠 <b>تغییرات این نسخه</b>\n\n"
    )
    footer = (
        "\n\n━━━━━━━━━━━━━━━━\n"
        f"{RTL_MARK}📦 <b>نسخه رسمی Android</b>\n"
        f"{RTL_MARK}🔐 امضای ثابت و بررسی SHA-256 تأییدشده\n"
        f"{RTL_MARK}♻️ نصب روی نسخه قبلی، بدون حذف اطلاعات\n\n"
        f'{RTL_MARK}🔗 <a href="https://github.com/{repo}/releases/tag/v{version}">'
        "مشاهده جزئیات انتشار</a>\n"
        f"{RTL_MARK}#NoraProxy #آپدیت"
    )
    included: list[str] = []
    for entry in changes:
        cleaned = " ".join(entry.split())
        if not cleaned:
            continue
        note = f"{RTL_MARK}• {html.escape(cleaned[:145], quote=False)}"
        possible = included + [note]
        if caption_units(header + "\n\n".join(possible) + footer) > CAPTION_BUDGET:
            continue
        included = possible
        if len(included) >= 5:
            break
    if not included:
        included = [f"{RTL_MARK}• بهبودهای فنی و اصلاحات این نسخه"]
    result = header + "\n\n".join(included) + footer
    if caption_units(result) > MAX_CAPTION_UNITS:
        raise ValueError("Caption exceeds Telegram's 1024 UTF-16-unit limit")
    return result

def verified_apk(apk: Path, checksum: Path, version: str) -> str:
    if apk.name != f"NoraProxy-{version}.apk":
        raise ValueError("Unexpected official APK filename")
    if not apk.is_file() or not checksum.is_file():
        raise ValueError("Verified APK and SHA256 file are required")
    if not 1_000_000 <= apk.stat().st_size <= 200_000_000:
        raise ValueError("Invalid APK size")
    parts = checksum.read_text(encoding="utf-8").strip().split()
    if len(parts) != 2 or not re.fullmatch(r"[a-fA-F0-9]{64}", parts[0]):
        raise ValueError("Malformed SHA256 manifest")
    if parts[1].lstrip("*") != apk.name:
        raise ValueError("Checksum filename does not match APK")
    digest = hashlib.sha256()
    with apk.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    actual = digest.hexdigest()
    if actual.lower() != parts[0].lower():
        raise ValueError("APK checksum mismatch; refusing Telegram publication")
    return actual

async def send_to_channel(args: argparse.Namespace) -> None:
    # Import lazily so caption previews/tests need no external dependency.
    from telethon import TelegramClient
    from telethon.sessions import StringSession

    required = ("NORA_TG_API_ID", "NORA_TG_API_HASH",
                "NORA_TG_BOT_TOKEN")
    missing = [k for k in required if not os.environ.get(k)]
    if missing:
        raise ValueError("Missing Telegram GitHub Secrets: " + ", ".join(missing))
    channel = os.environ.get("NORA_TG_CHANNEL_ID", "@noraproxy").strip()
    if not (re.fullmatch(r"@[A-Za-z0-9_]{5,32}", channel)
            or re.fullmatch(r"-100[0-9]{5,}", channel)):
        raise ValueError("Channel should be @public_username or -100... ID")

    sha = verified_apk(Path(args.apk), Path(args.checksum), args.version)
    caption = Path(args.caption).read_text(encoding="utf-8")
    if not caption or caption_units(caption) > MAX_CAPTION_UNITS:
        raise ValueError("Invalid Telegram caption length")

    # An ephemeral in-memory MTProto bot session avoids the 50 MB HTTP Bot API limit.
    client = TelegramClient(
        StringSession(), int(os.environ["NORA_TG_API_ID"]),
        os.environ["NORA_TG_API_HASH"],
        request_retries=3, connection_retries=3
    )
    try:
        await client.start(bot_token=os.environ["NORA_TG_BOT_TOKEN"])
        target = await client.get_input_entity(
            int(channel) if channel.startswith("-100") else channel
        )
        msg = await client.send_file(
            target, str(args.apk), caption=caption,
            force_document=True, mime_type="application/vnd.android.package-archive",
            parse_mode="html", allow_cache=False
        )
        if not msg or not msg.id:
            raise RuntimeError("Telegram did not return a message ID")
        # This receipt is PUBLIC metadata, not a token or Telegram session.
        Path(args.receipt).write_text(json.dumps({
            "tag": "v" + args.version,
            "message_id": msg.id,
            "apk_sha256": sha,
            "published_via": "MTProto bot"
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Uploaded NoraProxy v{args.version} APK to Telegram channel; message_id={msg.id}")
    finally:
        await client.disconnect()

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    preview = subs.add_parser("caption")
    delivery = subs.add_parser("send")
    for p in (preview, delivery):
        p.add_argument("--version", required=True, type=check_version)
        p.add_argument("--repo", required=True, type=check_repo)
    preview.add_argument("--output", type=Path, required=True)
    for flag in ("apk", "checksum", "caption", "receipt"):
        delivery.add_argument("--" + flag, required=True, type=Path)
    args = parser.parse_args()
    if args.command == "caption":
        result = compose_caption(args.version, args.repo, get_notes(args.version))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result + "\n", encoding="utf-8")
        print("Professional Telegram caption prepared for v" + args.version)
    else:
        asyncio.run(send_to_channel(args))

if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as exc:
        print("Telegram release error: " + str(exc), file=sys.stderr)
        sys.exit(1)
