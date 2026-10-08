"""GitHub repository and draft-first APK releases, using the REST API."""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from apk_artifacts import asset_name, collect_apks

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.github.com"
REPO = "gongpx20069/relative-ear"


def read_version(path=ROOT / "version.properties"):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key in values:
            raise ValueError(f"Duplicate version property: {key}")
        values[key.strip()] = value.strip()
    name = values["versionName"]
    code = int(values["versionCode"])
    if not re.fullmatch(r"0\.0\.[1-9][0-9]*", name) or name != f"0.0.{code}" or not 1 <= code <= 2_100_000_000:
        raise ValueError("Version must be 0.0.x with matching positive versionCode")
    return name, code


def validate_tag(tag):
    version, _ = read_version()
    if tag != f"v{version}":
        raise ValueError(f"Tag must match version.properties: v{version}")
    return version


def token():
    value = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if value:
        return value
    try:
        result = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, check=False)
    except FileNotFoundError:
        result = None
    if result is not None and result.returncode == 0 and result.stdout.strip():
        return result.stdout.strip()
    environment = os.environ.copy()
    environment.update(GIT_TERMINAL_PROMPT="0", GCM_INTERACTIVE="Never")
    try:
        credential = subprocess.run(
            ["git", "credential", "fill"],
            input="protocol=https\nhost=github.com\nusername=gongpx20069\n\n",
            capture_output=True, text=True, check=False, env=environment,
        )
    except FileNotFoundError as error:
        raise ValueError("Set GH_TOKEN or authenticate with GitHub CLI/Git Credential Manager") from error
    if credential.returncode == 0:
        fields = dict(line.split("=", 1) for line in credential.stdout.splitlines() if "=" in line)
        if fields.get("password"):
            return fields["password"]
    raise ValueError("No GitHub credential for gongpx20069; authenticate or set GH_TOKEN without committing it")


class GitHub:
    def __init__(self, access_token):
        self.access_token = access_token

    def request(self, method, path, data=None, content_type="application/json", missing_ok=False):
        url = path if path.startswith("https://") else API + path
        if urllib.parse.urlparse(url).hostname not in ("api.github.com", "uploads.github.com"):
            raise ValueError("Unexpected GitHub API host")
        body = json.dumps(data).encode() if isinstance(data, dict) else data
        request = urllib.request.Request(
            url,
            data=body,
            method=method,
            headers={
                "Authorization": f"Bearer {self.access_token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "relative-ear-release",
                "Content-Type": content_type,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                payload = response.read()
                return json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            raise RuntimeError(f"GitHub REST {method} failed with HTTP {error.code}") from error


def create_repository(client):
    user = client.request("GET", "/user")
    if user["login"].lower() != "gongpx20069":
        raise ValueError("Authenticated user must be gongpx20069")
    existing = client.request("GET", f"/repos/{REPO}", missing_ok=True)
    if existing:
        if existing["private"]:
            raise ValueError("Existing repository is private; refusing to change its visibility automatically")
        print(f"Public repository already exists: {existing['html_url']}")
        return
    repository = client.request(
        "POST", "/user/repos",
        {"name": "relative-ear", "private": False, "auto_init": False,
         "description": "Android singing accuracy, relative pitch training and live monophonic melody detection"},
    )
    print(repository["html_url"])


def publish_release(client, tag, apk_directory):
    version = validate_tag(tag)
    _, code = read_version()
    apks = collect_apks(apk_directory, version, code)
    uploads = {}
    checksums = []
    for architecture, apk in apks.items():
        name = asset_name(version, architecture)
        content = apk.read_bytes()
        uploads[name] = (content, "application/vnd.android.package-archive")
        checksums.append(f"{hashlib.sha256(content).hexdigest()}  {name}\n")
    uploads["SHA256SUMS.txt"] = ("".join(checksums).encode(), "text/plain")
    prefix = f"/repos/{REPO}"
    commit = client.request("GET", f"{prefix}/commits/{urllib.parse.quote(tag, safe='')}")["sha"]
    release = client.request("GET", f"{prefix}/releases/tags/{tag}", missing_ok=True)
    if release is not None and not release["draft"]:
        raise ValueError("Release is already published; increment the version instead of overwriting it")
    if release is not None and release["target_commitish"] != commit:
        raise ValueError("Existing draft references another commit")
    if release is None:
        release = client.request(
            "POST", f"{prefix}/releases",
            {"tag_name": tag, "target_commitish": commit, "name": f"Relative Ear {version}",
             "draft": True, "prerelease": True,
             "body": (
                 "Android 8.0+ early preview.\n\n"
                 "Features: fixed C4=Do listening and singing, three/five/eight-note or custom C4-C5 ranges, "
                 "solfege or C4/D4 answer labels, eighth-note playback at 60/80/100/120 BPM, "
                 "interactive note previews, a redesigned music-studio UI, sing-back pitch scoring, "
                 "live single-note melody detection, local training history, and manual in-app update checks "
                 "with ABI-aware download prompts (including published previews). "
                 "Practice history groups each round into one card, with complete per-note details on tap; "
                 "partial rounds and existing records are retained. "
                 "A standalone C4-C5 eight-key piano provides note previews and immersive landscape fullscreen, "
                 "with an exit button and Android Back restoring the previous orientation. "
                 "Other practice/detection pages no longer embed the piano. "
                 "Rapid key taps safely replace the previous note after audio cleanup. "
                 "Piano, flute and pure-tone voices are selectable in normal/fullscreen piano layouts and saved locally. "
                 "Instrument voices are synthesized approximations, not recorded samples; training/replay tones stay unchanged. "
                 "Detected notes can be replayed as synthesized tones with their original durations and silences; "
                 "a red playhead follows actual audio output. Replay retains the most recent 60 seconds, "
                 "not the original microphone recording. "
                 "User guides are split into an English-default README and a separate Simplified Chinese page; "
                 "the app UI remains Chinese. CI collects screenshots and a JUnit report from one complete test run.\n\n"
                 "Limitations: no song identification or reliable polyphonic/伴奏 transcription. "
                 "Device microphone accuracy still requires real-device evaluation.\n\n"
                 "Choose the APK matching your device: `arm64-v8a` for modern ARM phones, "
                 "`armeabi-v7a` for 32-bit ARM, `x86_64` or `x86` for Intel devices/emulators. "
                 "If unsure, choose `universal`. Each APK is independently installable.\n\n"
                 f"Assets: `relative-ear-{version}-<architecture>.apk`. "
                 "Verify downloads against `SHA256SUMS.txt`. "
                 "Release builds share a persistent signing key; debug builds cannot replace them.\n"
             )},
        )
    release_id = release["id"]
    expected = set(uploads)
    assets = client.request("GET", f"{prefix}/releases/{release_id}/assets?per_page=100")
    if any(asset["name"] not in expected for asset in assets):
        raise ValueError("Draft contains unexpected assets; inspect it before retrying")
    for asset in assets:
        client.request("DELETE", f"{prefix}/releases/assets/{asset['id']}")
    upload = release["upload_url"].split("{", 1)[0]
    for upload_name, (content, content_type) in uploads.items():
        result = client.request(
            "POST", upload + "?" + urllib.parse.urlencode({"name": upload_name}), content, content_type,
        )
        if result["state"] != "uploaded" or result["size"] != len(content):
            raise RuntimeError(f"Asset upload was not confirmed: {upload_name}")
    confirmed = client.request("GET", f"{prefix}/releases/{release_id}/assets?per_page=100")
    if (len(confirmed) != len(expected) or {asset["name"] for asset in confirmed} != expected
            or any(asset["state"] != "uploaded" or asset["size"] != len(uploads[asset["name"]][0])
                   for asset in confirmed)):
        raise RuntimeError("Release assets are incomplete; draft will not be published")
    published = client.request("PATCH", f"{prefix}/releases/{release_id}", {"draft": False})
    print(published["html_url"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("create-repo")
    validate = commands.add_parser("validate-version")
    validate.add_argument("--tag", required=True)
    release = commands.add_parser("release")
    release.add_argument("--tag", required=True)
    release.add_argument("--apk-dir", required=True, type=Path)
    args = parser.parse_args()
    if args.command == "validate-version":
        print(validate_tag(args.tag))
        return
    if args.command == "release":
        validate_tag(args.tag)
    client = GitHub(token())
    if args.command == "create-repo":
        create_repository(client)
    else:
        publish_release(client, args.tag, args.apk_dir)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError, urllib.error.URLError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
