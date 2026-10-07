"""Validate release APK signature, app ID and version before publishing."""

import argparse
import os
import re
import subprocess
from pathlib import Path
from github_api import read_version


def verify(apk, tools):
    suffix = ".exe" if os.name == "nt" else ""
    signer = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    subprocess.run([str(signer), "verify", "--verbose", str(apk)], check=True)
    badging = subprocess.run(
        [str(tools / ("aapt" + suffix)), "dump", "badging", str(apk)],
        check=True, capture_output=True, text=True, encoding="utf-8",
    ).stdout
    version, code = read_version()
    package = re.search(r"^package: (.+)$", badging, re.MULTILINE)
    if package is None:
        raise ValueError("Missing APK package metadata")
    fields = dict(re.findall(r"(\w+)='([^']*)'", package.group(1)))
    if (fields.get("name"), fields.get("versionCode"), fields.get("versionName")) != (
        "io.github.gongpx20069.relativeear", str(code), version,
    ):
        raise ValueError("APK application ID or version does not match the project")
    if "application-debuggable" in badging:
        raise ValueError("Refusing to publish a debuggable APK")
    if "sdkVersion:'26'" not in badging:
        raise ValueError("APK minimum Android version must be API 26")
    print(f"Verified signed release APK {version}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--tools", type=Path, required=True)
    args = parser.parse_args()
    verify(args.apk, args.tools)
