import json
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from apk_artifacts import ABIS, ARCHITECTURES, collect_apks
from github_api import read_version
from test_github_api import write_apks
from verify_apk import verify, verify_directory


class ApkArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.version, self.code = read_version()
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        write_apks(self.directory, self.version, self.code)
        self.metadata = self.directory / "output-metadata.json"
        quiet = patch("builtins.print")
        quiet.start()
        self.addCleanup(quiet.stop)

    def edit_metadata(self, change):
        metadata = json.loads(self.metadata.read_text())
        change(metadata)
        self.metadata.write_text(json.dumps(metadata))

    def test_collects_exact_complete_architecture_set(self):
        self.assertEqual(list(ARCHITECTURES), list(collect_apks(self.directory, self.version, self.code)))

    def test_missing_architecture_is_rejected(self):
        self.edit_metadata(lambda metadata: metadata["elements"].pop())
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def test_duplicate_architecture_is_rejected(self):
        self.edit_metadata(lambda metadata: metadata["elements"].append(metadata["elements"][0]))
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def test_version_mismatch_is_rejected(self):
        self.edit_metadata(lambda metadata: metadata["elements"][0].update(versionCode=self.code + 1))
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def test_debug_variant_is_rejected(self):
        self.edit_metadata(lambda metadata: metadata.update(variantName="debug"))
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def test_unknown_architecture_is_rejected(self):
        self.edit_metadata(lambda metadata: metadata["elements"][0]["filters"][0].update(value="unknown"))
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def test_extra_stale_apk_is_rejected(self):
        (self.directory / "stale.apk").write_bytes(b"stale")
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def test_path_outside_output_directory_is_rejected(self):
        self.edit_metadata(lambda metadata: metadata["elements"][0].update(outputFile="../outside.apk"))
        with self.assertRaises(ValueError):
            collect_apks(self.directory, self.version, self.code)

    def tool_results(self):
        signature = subprocess.CompletedProcess([], 0, stdout="Signer #1 certificate SHA-256 digest: aabb\n")
        badging = subprocess.CompletedProcess([], 0, stdout=(
            "package: name='io.github.gongpx20069.relativeear' "
            f"versionCode='{self.code}' versionName='{self.version}'\nsdkVersion:'26'\n"
        ))
        return [signature, badging]

    def test_actual_native_libraries_must_match_architecture(self):
        apk = self.directory / "app-arm64-v8a-release.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("lib/x86/libtest.so", b"native")
        with patch("verify_apk.subprocess.run", side_effect=self.tool_results()):
            with self.assertRaises(ValueError):
                verify(apk, self.directory, "arm64-v8a")

    def test_universal_must_contain_all_architectures(self):
        apk = self.directory / "app-universal-release.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            for abi in ABIS:
                archive.writestr(f"lib/{abi}/libtest.so", b"native")
        with patch("verify_apk.subprocess.run", side_effect=self.tool_results()):
            self.assertEqual("aabb", verify(apk, self.directory, "universal"))

    def test_unsigned_apk_is_rejected(self):
        with patch("verify_apk.subprocess.run", side_effect=subprocess.CalledProcessError(1, "apksigner")):
            with self.assertRaises(subprocess.CalledProcessError):
                verify(self.directory / "app-universal-release.apk", self.directory, "universal")

    def test_mismatched_signing_certificates_are_rejected(self):
        with patch("verify_apk.verify", side_effect=["aabb", "aabb", "ccdd", "aabb", "aabb"]):
            with self.assertRaises(ValueError):
                verify_directory(self.directory, self.directory)


if __name__ == "__main__":
    unittest.main()
