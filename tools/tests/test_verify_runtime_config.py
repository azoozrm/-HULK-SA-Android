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
    def write_build_config(
        self,
        directory: Path,
        portal: str = MODULE.PRODUCTION_PORTAL_URL,
        config: str = MODULE.PRODUCTION_CONFIG_URL,
    ) -> Path:
        path = directory / "BuildConfig.java"
        path.write_text(
            "\n".join(
                (
                    "package sa.hulksa.player;",
                    "public final class BuildConfig {",
                    f'  public static final String PORTAL_URL = "{portal}";',
                    f'  public static final String CONFIG_URL = "{config}";',
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

    def test_accepts_exact_production_config_and_dex_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(directory)
            archive = self.write_archive(
                directory,
                b"dex\n" + MODULE.PRODUCTION_PORTAL_URL.encode("utf-8"),
            )

            evidence = MODULE.verify(build_config, archive)

            self.assertEqual(MODULE.PRODUCTION_PORTAL_URL, evidence["portal_url"])
            self.assertEqual(1, evidence["dex_files"])

    def test_rejects_website_as_runtime_portal(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(
                directory,
                portal="https://hulksa.com",
            )
            archive = self.write_archive(directory, b"dex\nhttps://hulksa.com")

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)

    def test_rejects_placeholder_marker_in_dex(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            build_config = self.write_build_config(directory)
            archive = self.write_archive(
                directory,
                (
                    MODULE.PRODUCTION_PORTAL_URL.encode("utf-8")
                    + b"\nhttps://example.invalid"
                ),
            )

            with self.assertRaises(MODULE.VerificationError):
                MODULE.verify(build_config, archive)


if __name__ == "__main__":
    unittest.main()
