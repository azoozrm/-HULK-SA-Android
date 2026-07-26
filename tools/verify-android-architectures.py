#!/usr/bin/env python3
"""Verify ABI coverage and ELF identity in an Android APK or AAB."""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
import struct
import zipfile

EXPECTED = {
    "arm64-v8a": 183,
    "armeabi-v7a": 40,
    "x86_64": 62,
}
FORBIDDEN = {"x86"}
MACHINE_NAMES = {
    3: "EM_386",
    40: "EM_ARM",
    62: "EM_X86_64",
    183: "EM_AARCH64",
}


def parse_library_path(name: str) -> tuple[str, str] | None:
    parts = name.split("/")
    if len(parts) == 3 and parts[0] == "lib" and parts[2].endswith(".so"):
        return parts[1], parts[2]
    if (
        len(parts) == 4
        and parts[0] == "base"
        and parts[1] == "lib"
        and parts[3].endswith(".so")
    ):
        return parts[2], parts[3]
    return None


def elf_machine(payload: bytes, path: str) -> int:
    if len(payload) < 20 or payload[:4] != b"\x7fELF":
        raise ValueError(f"Native library is not a valid ELF file: {path}")
    data_encoding = payload[5]
    if data_encoding == 1:
        byte_order = "<"
    elif data_encoding == 2:
        byte_order = ">"
    else:
        raise ValueError(f"Unsupported ELF byte order in {path}: {data_encoding}")
    return struct.unpack(byte_order + "H", payload[18:20])[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--require-native-libs", action="store_true")
    args = parser.parse_args()

    if not args.archive.is_file():
        raise SystemExit(f"Archive not found: {args.archive}")

    errors: list[str] = []
    rows: list[tuple[str, str, str, str]] = []
    libraries: dict[str, set[str]] = defaultdict(set)
    found_abis: set[str] = set()

    with zipfile.ZipFile(args.archive) as archive:
        bad_zip_member = archive.testzip()
        if bad_zip_member:
            errors.append(f"ZIP integrity failure at {bad_zip_member}")

        native_entries = []
        for info in archive.infolist():
            parsed = parse_library_path(info.filename)
            if parsed:
                native_entries.append((info, *parsed))

        if args.require_native_libs and not native_entries:
            errors.append("No native libraries were found, but native libraries are required.")

        for info, abi, library_name in sorted(
            native_entries,
            key=lambda item: item[0].filename,
        ):
            found_abis.add(abi)
            libraries[library_name].add(abi)

            payload = archive.read(info)
            try:
                machine = elf_machine(payload, info.filename)
            except ValueError as exc:
                errors.append(str(exc))
                machine = -1

            expected_machine = EXPECTED.get(abi)
            if expected_machine is None:
                errors.append(f"Unapproved ABI found: {abi} ({info.filename})")
            elif machine != expected_machine:
                errors.append(
                    f"ELF machine mismatch for {info.filename}: "
                    f"expected {MACHINE_NAMES.get(expected_machine, expected_machine)}, "
                    f"found {MACHINE_NAMES.get(machine, machine)}"
                )

            compression = (
                "stored" if info.compress_type == zipfile.ZIP_STORED else "compressed"
            )
            rows.append(
                (abi, library_name, MACHINE_NAMES.get(machine, str(machine)), compression)
            )

    expected_abis = set(EXPECTED)
    missing_archive_abis = expected_abis - found_abis
    extra_archive_abis = found_abis - expected_abis
    forbidden_archive_abis = found_abis & FORBIDDEN

    if missing_archive_abis:
        errors.append("Missing qualified ABIs: " + ", ".join(sorted(missing_archive_abis)))
    if extra_archive_abis:
        errors.append("Unexpected ABIs: " + ", ".join(sorted(extra_archive_abis)))
    if forbidden_archive_abis:
        errors.append("Forbidden legacy ABIs: " + ", ".join(sorted(forbidden_archive_abis)))

    for library_name, abis in sorted(libraries.items()):
        missing = expected_abis - abis
        extra = abis - expected_abis
        if missing:
            errors.append(
                f"Library {library_name} is incomplete; missing: "
                + ", ".join(sorted(missing))
            )
        if extra:
            errors.append(
                f"Library {library_name} has unapproved ABIs: "
                + ", ".join(sorted(extra))
            )

    lines = [
        f"# Architecture Qualification — {args.archive.name}",
        "",
        f"- Result: {'FAIL' if errors else 'PASS'}",
        "- Qualified ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`",
        "- Excluded ABI: `x86`",
        f"- Native library families: {len(libraries)}",
        f"- Native library files: {len(rows)}",
        "",
        "| ABI | Library | ELF machine | ZIP storage |",
        "|---|---|---|---|",
    ]
    for abi, library_name, machine, compression in rows:
        lines.append(
            f"| `{abi}` | `{library_name}` | `{machine}` | {compression} |"
        )
    if not rows:
        lines.append("| — | No native libraries | — | — |")

    lines.extend(["", "## Validation"])
    if errors:
        lines.extend(f"- [FAIL] {error}" for error in errors)
    else:
        lines.extend(
            [
                "- [PASS] Archive ZIP integrity.",
                "- [PASS] Exact approved ABI set is present.",
                "- [PASS] Every native library exists for every approved ABI.",
                "- [PASS] ELF machine identity matches each ABI directory.",
                "- [PASS] Legacy x86 is absent.",
            ]
        )

    report = "\n".join(lines) + "\n"
    print(report, end="")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")

    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
