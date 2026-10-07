"""Validate release APK signature, app ID and version before publishing."""

import argparse
import os
import re
import subprocess
import zipfile
from pathlib import Path
from github_api import read_version
from apk_artifacts import ABIS, APPLICATION_ID, collect_apks


def verify(apk, tools, architecture):
    suffix = ".exe" if os.name == "nt" else ""
    signer = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    signature = subprocess.run(
        [str(signer), "verify", "--print-certs", str(apk)],
        check=True, capture_output=True, text=True, encoding="utf-8",
    ).stdout
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
    if len(fingerprints) != 1:
        raise ValueError("Expected exactly one signing certificate")
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
        APPLICATION_ID, str(code), version,
    ):
        raise ValueError("APK application ID or version does not match the project")
    if "application-debuggable" in badging:
        raise ValueError("Refusing to publish a debuggable APK")
    if "sdkVersion:'26'" not in badging:
        raise ValueError("APK minimum Android version must be API 26")
    with zipfile.ZipFile(apk) as archive:
        abis = {name.split("/")[1] for name in archive.namelist()
                if name.startswith("lib/") and name.endswith(".so")}
    expected = set(ABIS) if architecture == "universal" else {architecture}
    if abis != expected:
        raise ValueError(f"APK native libraries do not match {architecture}: {sorted(abis)}")
    print(f"Verified signed release APK {version} ({architecture})")
    return fingerprints[0].lower()


def verify_directory(directory, tools):
    version, code = read_version()
    fingerprints = {
        verify(apk, tools, architecture)
        for architecture, apk in collect_apks(directory, version, code).items()
    }
    if len(fingerprints) != 1:
        raise ValueError("All architecture APKs must use the same signing certificate")
    print("Complete ABI set verified with one shared signing certificate")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-dir", type=Path, required=True)
    parser.add_argument("--tools", type=Path, required=True)
    args = parser.parse_args()
    verify_directory(args.apk_dir, args.tools)
