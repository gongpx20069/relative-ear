"""Collect a complete, version-consistent Android ABI APK set from AGP metadata."""

import json
from pathlib import Path

ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
ARCHITECTURES = (*ABIS, "universal")
APPLICATION_ID = "io.github.gongpx20069.relativeear"


def asset_name(version, architecture):
    if architecture not in ARCHITECTURES:
        raise ValueError(f"Unsupported architecture: {architecture}")
    return f"relative-ear-{version}-{architecture}.apk"


def collect_apks(directory, version, code):
    directory = directory.resolve()
    metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata["applicationId"] != APPLICATION_ID:
        raise ValueError("APK metadata has the wrong application ID")
    if metadata["variantName"] != "release":
        raise ValueError("Only release variant APKs can be published")
    apks = {}
    for element in metadata["elements"]:
        filters = element["filters"]
        if not filters:
            architecture = "universal"
        elif len(filters) == 1 and filters[0]["filterType"] == "ABI":
            architecture = filters[0]["value"]
        else:
            raise ValueError("Unexpected APK output filters")
        if architecture not in ARCHITECTURES or architecture in apks:
            raise ValueError(f"Unsupported or duplicate APK architecture: {architecture}")
        if element["versionName"] != version or element["versionCode"] != code:
            raise ValueError(f"APK metadata version mismatch: {architecture}")
        apk = (directory / element["outputFile"]).resolve()
        if apk.parent != directory or apk.suffix != ".apk":
            raise ValueError("APK output must be a file in the output directory")
        if not apk.is_file() or apk.stat().st_size == 0:
            raise ValueError(f"APK is missing or empty: {architecture}")
        apks[architecture] = apk
    if set(apks) != set(ARCHITECTURES):
        raise ValueError(f"Incomplete APK set; expected {', '.join(ARCHITECTURES)}")
    if set(apks.values()) != set(directory.glob("*.apk")) or len(set(apks.values())) != len(apks):
        raise ValueError("Duplicate APK paths or stale APK files in output directory")
    return {architecture: apks[architecture] for architecture in ARCHITECTURES}
