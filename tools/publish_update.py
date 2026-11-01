#!/usr/bin/env python3
"""Generate update.json for Raster Dial from a built APK.

The manifest is the GitHub fallback source (committed at the repo root) and is
mirrored to the Chengdu server. See docs/UPDATE_SOURCE.md for the full flow.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def sdk_dir():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value:
            return Path(value)
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].strip().replace("\\", "/"))
    return None


def aapt_bin():
    found = shutil.which("aapt")
    if found:
        return found
    sdk = sdk_dir()
    if sdk:
        build_tools = sdk / "build-tools"
        if build_tools.is_dir():
            for directory in sorted(build_tools.iterdir(), reverse=True):
                for name in ("aapt.exe", "aapt"):
                    candidate = directory / name
                    if candidate.is_file():
                        return str(candidate)
    raise SystemExit("aapt not found; set ANDROID_HOME or put build-tools on PATH")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", default=str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    parser.add_argument("--chengdu-base", default="https://updates.example.com/raster-dial",
                        help="Chengdu base URL; the APK filename is appended")
    parser.add_argument("--github-url", default="",
                        help="GitHub release asset URL; defaults to the v<version> release asset")
    parser.add_argument("--notes", default="")
    parser.add_argument("--out", default=str(ROOT / "update.json"))
    args = parser.parse_args()

    apk = Path(args.apk)
    if not apk.is_file():
        raise SystemExit("missing APK: " + str(apk))

    badging = subprocess.check_output(
        [aapt_bin(), "dump", "badging", str(apk)], text=True, errors="replace")
    match = re.search(r"package:.*versionCode='(\d+)'.*versionName='([^']+)'", badging)
    if not match:
        raise SystemExit("cannot parse version from aapt badging")
    version_code = int(match.group(1))
    version_name = match.group(2)
    match_sdk = re.search(r"sdkVersion:'(\d+)'", badging)
    min_sdk = int(match_sdk.group(1)) if match_sdk else 0

    data = apk.read_bytes()
    filename = "raster-dial-%s-debug.apk" % version_name
    github = args.github_url.strip() or (
        "https://github.com/kdjdhe/raster-dial/releases/download/v%s/%s"
        % (version_name, filename))
    chengdu = args.chengdu_base.rstrip("/") + "/" + filename
    notes = args.notes.strip() or ("Raster Dial %s" % version_name)

    manifest = {
        "versionCode": version_code,
        "versionName": version_name,
        "minSdk": min_sdk,
        "publishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sha256": hashlib.sha256(data).hexdigest(),
        "size": len(data),
        "notes": notes,
        "apkUrls": [chengdu, github],
    }
    Path(args.out).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote", args.out)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
