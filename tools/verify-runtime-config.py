#!/usr/bin/env python3
"""Verify reviewed public HULK endpoints and absence of IPTV hosts in APK/AAB DEX."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path
from urllib.parse import urlparse


PRODUCTION_RESELLER_API_URL = "https://hulksa.com"
PRODUCTION_OPERATIONS_CONFIG_URL = "https://hulksa.com/hulk-operations/api/app/v1/config/"
FORBIDDEN_DEX_MARKERS = (
    b"http://3162356.xyz:8080",
    b"3162356.xyz:8080",
    b"https://example.invalid",
    b"http://example.invalid",
)


class VerificationError(RuntimeError):
    """Raised when a release runtime configuration check fails."""


def read_build_config_field(path: Path, name: str) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.search(
        rf'public static final String {re.escape(name)} = "([^"]*)";',
        text,
    )
    if match is None:
        raise VerificationError(f"Generated BuildConfig is missing {name}.")
    return match.group(1)


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
    reseller_api_url = read_build_config_field(build_config, "RESELLER_API_URL")
    operations_config_url = read_build_config_field(build_config, "OPERATIONS_CONFIG_URL")
    if reseller_api_url != PRODUCTION_RESELLER_API_URL:
        raise VerificationError(
            "Generated release RESELLER_API_URL does not match the reviewed HULK API.",
        )
    parsed = urlparse(reseller_api_url)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.params
        or parsed.query
        or parsed.fragment
    ):
        raise VerificationError(
            "Release RESELLER_API_URL must be an HTTPS base URL without credentials, query, or fragment.",
        )
    hostname = parsed.hostname.lower()
    if hostname == "localhost" or hostname.endswith((".invalid", ".localhost")):
        raise VerificationError("Release RESELLER_API_URL is not a public endpoint.")
    if "3162356.xyz" in hostname:
        raise VerificationError("The legacy IPTV host cannot be used as the reseller API.")

    if operations_config_url != PRODUCTION_OPERATIONS_CONFIG_URL:
        raise VerificationError(
            "Generated release OPERATIONS_CONFIG_URL does not match the reviewed HULK endpoint.",
        )
    operations = urlparse(operations_config_url)
    if (
        operations.scheme != "https"
        or operations.hostname != "hulksa.com"
        or operations.username is not None
        or operations.password is not None
        or operations.params
        or operations.query
        or operations.fragment
        or operations.path != "/hulk-operations/api/app/v1/config/"
    ):
        raise VerificationError("Release OPERATIONS_CONFIG_URL is not the reviewed HTTPS endpoint.")

    dex_entries = read_dex_entries(archive)
    dex_payload = b"".join(payload for _, payload in dex_entries)
    if reseller_api_url.encode("utf-8") not in dex_payload:
        raise VerificationError(
            "Configured HULK reseller API is absent from the compiled DEX payload.",
        )
    if operations_config_url.encode("utf-8") not in dex_payload:
        raise VerificationError(
            "Configured HULK Operations endpoint is absent from the compiled DEX payload.",
        )
    if any(marker in dex_payload for marker in FORBIDDEN_DEX_MARKERS):
        raise VerificationError(
            "A forbidden legacy IPTV or placeholder endpoint is present in the compiled DEX payload.",
        )

    return {
        "reseller_api_url": reseller_api_url,
        "operations_config_url": operations_config_url,
        "dex_files": len(dex_entries),
        "dex_uncompressed_bytes": sum(len(payload) for _, payload in dex_entries),
        "archive_bytes": archive.stat().st_size,
    }


def render_report(archive: Path, evidence: dict[str, object]) -> str:
    return "\n".join(
        (
            f"# Runtime Configuration Qualification — {archive.name}",
            "",
            "- Result: PASS",
            f"- Release `BuildConfig.RESELLER_API_URL`: `{evidence['reseller_api_url']}`",
            f"- Release `BuildConfig.OPERATIONS_CONFIG_URL`: `{evidence['operations_config_url']}`",
            "- Public HTTPS reseller API marker in compiled DEX: PASS",
            "- Public HTTPS Operations marker in compiled DEX: PASS",
            "- Legacy IPTV host marker in compiled DEX: ABSENT",
            "- Placeholder endpoint marker in compiled DEX: ABSENT",
            f"- DEX files: {evidence['dex_files']}",
            f"- DEX uncompressed bytes: {evidence['dex_uncompressed_bytes']}",
            f"- Archive bytes: {evidence['archive_bytes']}",
            "",
            "No reseller IPTV host is compiled into the Android artifact.",
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
