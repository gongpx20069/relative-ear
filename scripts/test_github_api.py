import tempfile
import unittest
import json
import hashlib
import subprocess
from pathlib import Path
from unittest.mock import patch

import github_api
from apk_artifacts import ARCHITECTURES, APPLICATION_ID, asset_name


def write_apks(directory, version, code):
    elements = []
    for architecture in ARCHITECTURES:
        filename = f"app-{architecture}-release.apk"
        (directory / filename).write_bytes(f"test apk {architecture}".encode())
        elements.append({
            "filters": [] if architecture == "universal" else [{"filterType": "ABI", "value": architecture}],
            "versionName": version, "versionCode": code, "outputFile": filename,
        })
    (directory / "output-metadata.json").write_text(json.dumps({
        "applicationId": APPLICATION_ID, "variantName": "release", "elements": elements,
    }), encoding="utf-8")


class FakeGitHub:
    def __init__(self, published=False, fail_upload=False, fail_second=False, wrong_user=False, draft_commit=None, fail_at=None):
        self.published = published
        self.fail_upload = fail_upload
        self.assets = []
        self.did_publish = False
        self.created_public = False
        self.contents = {}
        self.fail_second = fail_second
        self.wrong_user = wrong_user
        self.draft_commit = draft_commit
        self.fail_at = fail_at

    def request(self, method, path, data=None, content_type=None, missing_ok=False):
        if path == "/user":
            return {"login": "other-user" if self.wrong_user else "gongpx20069"}
        if path == f"/repos/{github_api.REPO}":
            return None
        if path == "/user/repos":
            self.created_public = data["private"] is False
            return {"html_url": "https://github.com/" + github_api.REPO}
        if "/commits/" in path:
            return {"sha": "commit"}
        if "/releases/tags/" in path:
            if self.draft_commit:
                return {"draft": True, "target_commitish": self.draft_commit}
            return {"draft": False} if self.published else None
        if method == "POST" and path.endswith("/releases"):
            return {"id": 1, "upload_url": "https://uploads.github.com/repos/test/releases/1/assets{?name}",
                    "draft": True}
        if method == "GET" and "/assets?" in path:
            return self.assets
        if path.startswith("https://uploads.github.com"):
            if self.fail_upload or (self.fail_second and self.assets) or len(self.assets) == self.fail_at:
                raise RuntimeError("Upload failed")
            from urllib.parse import parse_qs, urlparse
            name = parse_qs(urlparse(path).query)["name"][0]
            asset = {"id": len(self.assets) + 1, "name": name, "state": "uploaded", "size": len(data)}
            self.assets.append(asset)
            self.contents[name] = data
            return asset
        if method == "PATCH":
            self.did_publish = True
            return {"html_url": "https://github.com/test/release"}
        raise AssertionError(f"Unexpected request: {method} {path}")


class GitHubApiTest(unittest.TestCase):
    def setUp(self):
        self.version, self.code = github_api.read_version()
        self.tag = f"v{self.version}"
        quiet = patch("builtins.print")
        quiet.start()
        self.addCleanup(quiet.stop)

    def test_version_contract(self):
        self.assertEqual(f"0.0.{self.code}", self.version)
        self.assertEqual(self.version, github_api.validate_tag(self.tag))
        for tag in ("v0.1.0", "v0.0.01", self.version, f"v0.0.{self.code + 1}", self.tag + "; echo bad"):
            with self.assertRaises(ValueError):
                github_api.validate_tag(tag)

    def test_invalid_versions(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            for content in (
                "versionName=0.0.0\nversionCode=0",
                "versionName=0.0.2\nversionCode=1",
                "versionName=0.0.01\nversionCode=1",
                "versionName=0.0.2147483647\nversionCode=2147483647",
                "versionName=0.0.1\nversionCode=1\nversionCode=2",
            ):
                path.write_text(content)
                with self.assertRaises(ValueError):
                    github_api.read_version(path)

    def test_creates_public_repository(self):
        client = FakeGitHub()
        github_api.create_repository(client)
        self.assertTrue(client.created_public)

    def test_wrong_account_does_not_create_repository(self):
        client = FakeGitHub(wrong_user=True)
        with self.assertRaises(ValueError):
            github_api.create_repository(client)
        self.assertFalse(client.created_public)

    def test_draft_only_publishes_after_all_architectures_and_checksums(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            client = FakeGitHub()
            github_api.publish_release(client, self.tag, directory)
            self.assertTrue(client.did_publish)
            self.assertEqual({asset_name(self.version, architecture) for architecture in ARCHITECTURES} | {"SHA256SUMS.txt"},
                             {asset["name"] for asset in client.assets})
            self.assertEqual(6, len(client.assets))
            lines = client.contents["SHA256SUMS.txt"].decode().splitlines()
            self.assertEqual(5, len(lines))
            for line in lines:
                digest, filename = line.split("  ", 1)
                self.assertEqual(hashlib.sha256(client.contents[filename]).hexdigest(), digest)

    def test_failure_keeps_draft_unpublished(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            client = FakeGitHub(fail_upload=True)
            with self.assertRaises(RuntimeError):
                github_api.publish_release(client, self.tag, directory)
            self.assertFalse(client.did_publish)

    def test_published_release_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            with self.assertRaises(ValueError):
                github_api.publish_release(FakeGitHub(published=True), self.tag, directory)

    def test_second_asset_failure_does_not_publish(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            client = FakeGitHub(fail_second=True)
            with self.assertRaises(RuntimeError):
                github_api.publish_release(client, self.tag, directory)
            self.assertEqual(1, len(client.assets))
            self.assertFalse(client.did_publish)

    def test_existing_draft_commit_must_match(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            client = FakeGitHub(draft_commit="another-commit")
            with self.assertRaises(ValueError):
                github_api.publish_release(client, self.tag, directory)
            self.assertFalse(client.did_publish)

    def test_failure_of_any_architecture_or_checksum_prevents_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            for index in range(6):
                with self.subTest(asset=index):
                    client = FakeGitHub(fail_at=index)
                    with self.assertRaises(RuntimeError):
                        github_api.publish_release(client, self.tag, directory)
                    self.assertEqual(index, len(client.assets))
                    self.assertFalse(client.did_publish)

    def test_missing_apk_does_not_call_api(self):
        client = FakeGitHub()
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            write_apks(directory, self.version, self.code)
            (directory / "app-arm64-v8a-release.apk").unlink()
            with self.assertRaises(ValueError):
                github_api.publish_release(client, self.tag, directory)
        self.assertFalse(client.did_publish)

    def test_unexpected_api_host_is_rejected(self):
        with self.assertRaises(ValueError):
            github_api.GitHub("test").request("GET", "https://example.com")

    def test_no_token_is_logged(self):
        with patch.dict("os.environ", {"GH_TOKEN": "private-value"}):
            self.assertEqual("private-value", github_api.token())

    def test_account_specific_git_credential_fallback(self):
        responses = [
            subprocess.CompletedProcess([], 1, stdout="", stderr="not authenticated"),
            subprocess.CompletedProcess([], 0, stdout="username=gongpx20069\npassword=saved-test-token\n"),
        ]
        with patch.dict("os.environ", {}, clear=True), patch("github_api.subprocess.run", side_effect=responses) as run:
            self.assertEqual("saved-test-token", github_api.token())
            lookup = run.call_args
            self.assertIn("username=gongpx20069", lookup.kwargs["input"])
            self.assertEqual("0", lookup.kwargs["env"]["GIT_TERMINAL_PROMPT"])
            self.assertEqual("Never", lookup.kwargs["env"]["GCM_INTERACTIVE"])

    def test_git_credential_fallback_without_github_cli(self):
        responses = [FileNotFoundError(), subprocess.CompletedProcess([], 0, stdout="password=saved-test-token\n")]
        with patch.dict("os.environ", {}, clear=True), patch("github_api.subprocess.run", side_effect=responses):
            self.assertEqual("saved-test-token", github_api.token())

    def test_missing_credential_fails_explicitly(self):
        failure = subprocess.CompletedProcess([], 1, stdout="", stderr="unavailable")
        with patch.dict("os.environ", {}, clear=True), patch("github_api.subprocess.run", return_value=failure):
            with self.assertRaises(ValueError):
                github_api.token()


if __name__ == "__main__":
    unittest.main()
