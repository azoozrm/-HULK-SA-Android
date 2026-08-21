from __future__ import annotations

import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "verify-runtime-config.py"
SPEC = importlib.util.spec_from_file_location("verify_runtime_config", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RuntimeConfigVerifierTest(unittest.TestCase):
    TEST_API_URL = MODULE.PRODUCTION_RESELLER_API_URL
    TEST_OPERATIONS_URL = MODULE.PRODUCTION_OPERATIONS_CONFIG_URL

    def write_build_config(
        self,
        directory: Path,
        reseller_api_url: str = TEST_API_URL,
        operations_config_url: str = TEST_OPERATIONS_URL,
    ) -> Path:
        path = directory / "BuildConfig.java"
        path.write_text(
            "\n".join(
                (
                    "package sa.hulksa.player;",
                    "public final class BuildConfig {",
                    f'  public static final String RESELLER_API_URL = "{reseller_api_url}";',
                    f'  public static final String OPERATIONS_CONFIG_URL = "{operations_config_url}";',
                    "}",
                ),
            ),
            encoding="utf-8",
        )
        return path

    def write_archive(self, directory: Path, dex_payload: bytes) -> Path:
        path = directory / "app-release.apk"
        with zipfile.ZipFile(path, "w") as package:
            package.writestr("classes.dex", dex_payload)
        return path

    def test_accepts_public_https_reseller_api_and_dex_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(directory)
            archive = self.write_archive(
                directory,
                b"dex\n" + self.TEST_API_URL.encode("utf-8") + b"\n" + self.TEST_OPERATIONS_URL.encode("utf-8"),
            )

            evidence = MODULE.verify(build_config, archive)

            self.assertEqual(self.TEST_API_URL, evidence["reseller_api_url"])
            self.assertEqual(self.TEST_OPERATIONS_URL, evidence["operations_config_url"])
            self.assertEqual(1, evidence["dex_files"])

    def test_rejects_non_https_reseller_api(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(
                directory,
                reseller_api_url="http://reseller-api.hulksa.com",
            )
            archive = self.write_archive(
                directory,
                b"dex\nhttp://reseller-api.hulksa.com\n" + self.TEST_OPERATIONS_URL.encode("utf-8"),
            )

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)

    def test_rejects_unreviewed_https_endpoint(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(
                directory,
                reseller_api_url="https://api.example.com",
            )
            archive = self.write_archive(
                directory,
                b"dex\nhttps://api.example.com\n" + self.TEST_OPERATIONS_URL.encode("utf-8"),
            )

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)

    def test_rejects_unreviewed_operations_endpoint(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(
                directory,
                operations_config_url="https://example.com/config",
            )
            archive = self.write_archive(
                directory,
                self.TEST_API_URL.encode("utf-8") + b"\nhttps://example.com/config",
            )

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)

    def test_rejects_placeholder_marker_in_dex(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(directory)
            archive = self.write_archive(
                directory,
                (
                    self.TEST_API_URL.encode("utf-8")
                    + b"\n"
                    + self.TEST_OPERATIONS_URL.encode("utf-8")
                    + b"\nhttps://example.invalid"
                ),
            )

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)

    def test_rejects_legacy_iptv_host_in_dex(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(directory)
            archive = self.write_archive(
                directory,
                self.TEST_API_URL.encode("utf-8")
                + b"\n"
                + self.TEST_OPERATIONS_URL.encode("utf-8")
                + b"\nhttp://3162356.xyz:8080",
            )

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)


if __name__ == "__main__":
    unittest.main()
