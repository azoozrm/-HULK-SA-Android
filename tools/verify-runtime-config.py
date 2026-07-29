#!/usr/bin/env python3
"""Verify the production runtime endpoint in generated BuildConfig and APK/AAB DEX."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path
from urllib.parse import urlparse


PRODUCTION_PORTAL_URL = "http://3162356.xyz:8080"
PRODUCTION_CONFIG_URL = ""
FORBIDDEN_DEX_MARKERS = (
    b"https://example.invalid",
    b"http://example.invalid",
)


class VerificationError(RuntimeError):
    """Raised when a release runtime configuration check fails."""


def read_build_config(path: Path) -> tuple[str, str]:
    text = path.read_text(encoding="utf-8")

    def field(name: str) -> str:
        match = re.search(
            rf'public static final String {re.escape(name)} = "([^"]*)";',
            text,
        )
        if match is None:
            raise VerificationError(f"Generated BuildConfig is missing {name}.")
        return match.group(1)

    return field("PORTAL_URL"), field("CONFIG_URL")


def read_dex_entries(archive: Path) -> list[tuple[str, bytes]]:
    try:
        with zipfile.ZipFile(archive) as package:
            corrupt_entry = package.testzip()
            if corrupt_entry is not None:
                raise VerificationError("Android archive ZIP integrity failed.")
            entries = [
                (name, package.read(name))
                for name in package.namelist()
                if name.endswith(".dex")
            ]
    except zipfile.BadZipFile as error:
        raise VerificationError("Android artifact is not a valid ZIP archive.") from error

    if not entries:
        raise VerificationError("Android artifact contains no DEX files.")
    return entries


def verify(build_config: Path, archive: Path) -> dict[str, object]:
    portal_url, config_url = read_build_config(build_config)
    if portal_url != PRODUCTION_PORTAL_URL:
        raise VerificationError(
            "Generated release PORTAL_URL does not match the canonical HULK endpoint.",
        )
    if config_url != PRODUCTION_CONFIG_URL:
        raise VerificationError(
            "Generated release CONFIG_URL must be empty.",
        )

    parsed = urlparse(portal_url)
    if (
        parsed.scheme != "http"
        or parsed.hostname != "3162356.xyz"
        or parsed.port != 8080
        or parsed.path not in ("", "/")
        or parsed.params
        or parsed.query
        or parsed.fragment
    ):
        raise VerificationError("Canonical endpoint structure validation failed.")

    dex_entries = read_dex_entries(archive)
    dex_payload = b"".join(payload for _, payload in dex_entries)
    if PRODUCTION_PORTAL_URL.encode("utf-8") not in dex_payload:
        raise VerificationError(
            "Canonical HULK endpoint is absent from the compiled DEX payload.",
        )
    if any(marker in dex_payload for marker in FORBIDDEN_DEX_MARKERS):
        raise VerificationError(
            "A forbidden placeholder endpoint is present in the compiled DEX payload.",
        )

    return {
        "portal_url": portal_url,
        "config_url": config_url,
        "dex_files": len(dex_entries),
        "dex_uncompressed_bytes": sum(len(payload) for _, payload in dex_entries),
        "archive_bytes": archive.stat().st_size,
    }


def render_report(archive: Path, evidence: dict[str, object]) -> str:
    config_display = evidence["config_url"] or "(empty)"
    return "\n".join(
        (
            f"# Runtime Configuration Qualification — {archive.name}",
            "",
            "- Result: PASS",
            f"- Release `BuildConfig.PORTAL_URL`: `{evidence['portal_url']}`",
            f"- Release `BuildConfig.CONFIG_URL`: `{config_display}`",
            "- Canonical endpoint marker in compiled DEX: PASS",
            "- `example.invalid` marker in compiled DEX: ABSENT",
            f"- DEX files: {evidence['dex_files']}",
            f"- DEX uncompressed bytes: {evidence['dex_uncompressed_bytes']}",
            f"- Archive bytes: {evidence['archive_bytes']}",
            "",
            "`hulksa.com` remains allowed only for store, account, app-download, or support links; "
            "it is not the compiled service endpoint.",
            "",
        ),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path, help="APK or AAB to inspect")
    parser.add_argument(
        "--build-config",
        required=True,
        type=Path,
        help="Generated release BuildConfig.java",
    )
    parser.add_argument("--report", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        evidence = verify(args.build_config, args.archive)
        report = render_report(args.archive, evidence)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")
        print(report, end="")
        return 0
    except (OSError, VerificationError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
