#!/usr/bin/env python3
"""Prepare the disposable debug Compatibility Lab harness and markers."""

from __future__ import annotations

import hashlib
from pathlib import Path
import sys

from inject_quality_markers import inject_file


def tree_digest(root: Path, *, exclude: set[Path] | None = None) -> str:
    excluded = {item.resolve() for item in (exclude or set())}
    digest = hashlib.sha256()
    if not root.exists():
        return digest.hexdigest()
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        if path.resolve() in excluded:
            continue
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def prepare_qa_activity(source: str) -> str:
    """Force a fresh debug semantics node when page or transfer state changes.

    Android 9's accessibility bridge can retain the previous content description
    when one Compose semantics node mutates in place. The Compatibility Lab uses
    these descriptions only as debug evidence, so recreate that node whenever
    its evidence tuple changes. Every anchor is exact and the transform fails
    closed for unknown or already-instrumented harness source.
    """

    key_import = "import androidx.compose.runtime.key\n"
    if key_import in source:
        raise ValueError(
            "QA Activity already contains the API 28 semantics refresh key"
        )
    import_anchor = "import androidx.compose.runtime.getValue\n"
    import_count = source.count(import_anchor)
    if import_count != 1:
        raise ValueError(
            "QA Activity key import anchor must match exactly once; "
            f"found {import_count}"
        )
    source = source.replace(import_anchor, import_anchor + key_import, 1)

    start_marker = "private fun FixtureMain("
    end_marker = "\nprivate fun String.toDestination()"
    start = source.find(start_marker)
    if start < 0:
        raise ValueError("QA Activity FixtureMain start marker not found")
    end = source.find(end_marker, start)
    if end < 0:
        raise ValueError("QA Activity FixtureMain end marker not found")
    segment = source[start:end]

    box_anchor = (
        "    Box(\n"
        "        Modifier\n"
        "            .fillMaxSize()\n"
        "            .semantics(mergeDescendants = false) {"
    )
    box_count = segment.count(box_anchor)
    if box_count != 1:
        raise ValueError(
            "QA Activity marker Box anchor must match exactly once; "
            f"found {box_count}"
        )
    segment = segment.replace(
        box_anchor,
        "    key(\n"
        "        pageMarker,\n"
        "        hasOriginByteProgress,\n"
        "        hasRealDownloadProgress,\n"
        "        lastDownloadAction,\n"
        "    ) {\n"
        "        Box(\n"
        "            Modifier\n"
        "                .fillMaxSize()\n"
        "                .semantics(mergeDescendants = false) {",
        1,
    )
    function_tail = "\n    }\n}\n"
    if not segment.endswith(function_tail):
        raise ValueError(
            "QA Activity FixtureMain tail did not match the supported shape"
        )
    segment = segment[: -len(function_tail)] + "\n        }\n    }\n}\n"
    return source[:start] + segment + source[end:]


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare-harness.py <prepared-project>")

    project = Path(sys.argv[1]).resolve()
    app = project / "app"
    main_source = app / "src/main"
    if not (project / "settings.gradle.kts").is_file() or not main_source.is_dir():
        raise SystemExit(f"not a prepared Android project: {project}")

    marker_target = (
        main_source
        / "java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    )
    if not marker_target.is_file():
        raise SystemExit(f"missing Quality Lab marker target: {marker_target}")

    production_before = tree_digest(main_source)
    protected_before = tree_digest(main_source, exclude={marker_target})

    debug_root = app / "src/debug"
    manifest = debug_root / "AndroidManifest.xml"
    source_dir = debug_root / "java/sa/hulksa/player/qa"
    source_dir.mkdir(parents=True, exist_ok=True)
    manifest.parent.mkdir(parents=True, exist_ok=True)

    manifest.write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".qa.QaActivity"
            android:exported="true"
            android:screenOrientation="unspecified"
            android:theme="@style/Theme.HulkSA" />
    </application>
</manifest>
""",
        encoding="utf-8",
    )
    source = Path(__file__).with_name("QaActivity.kt")
    prepared_source = prepare_qa_activity(source.read_text(encoding="utf-8"))
    (source_dir / "QaActivity.kt").write_text(
        prepared_source,
        encoding="utf-8",
    )

    marker_report = debug_root / "quality-marker-injection.json"
    report = inject_file(marker_target, marker_report)

    protected_after = tree_digest(main_source, exclude={marker_target})
    if protected_before != protected_after:
        raise SystemExit(
            "Quality Lab marker injection changed an unexpected production file"
        )
    production_after = tree_digest(main_source)
    if production_before == production_after:
        raise SystemExit("Quality Lab marker injection did not change its target")

    print("PASS: disposable Compatibility Lab harness prepared")
    print(
        "PASS: temporary MainShell marker instrumentation is limited to the "
        "prepared debug checkout"
    )
    print(
        "PASS: debug page and transfer semantics recreate on evidence changes"
    )
    print(f"Canonical src/main digest before injection: {production_before}")
    print(f"Instrumented src/main digest: {production_after}")
    print(f"Marker source SHA-256: {report['original_sha256']}")
    print(f"Marker build SHA-256: {report['instrumented_sha256']}")


if __name__ == "__main__":
    main()
