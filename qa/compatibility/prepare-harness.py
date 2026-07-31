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


def replace_exact(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise ValueError(f"{label} must match exactly once; found {count}")
    return source.replace(old, new, 1)


def prepare_qa_activity(source: str) -> str:
    """Add a native debug-only accessibility evidence node.

    Android 9 UI Automator can retain a previous Compose semantics snapshot while
    the rendered page and durable download state have already advanced. A tiny
    native Android View provides the same authenticated page/transfer evidence
    through the platform accessibility tree. The transform is exact, debug-only,
    and fails closed for repeated, missing, or already-instrumented source.
    """

    guard = "val qualityEvidence = buildList {"
    if guard in source or "AndroidView(" in source:
        raise ValueError(
            "QA Activity already contains the native accessibility evidence node"
        )

    for anchor, addition, label in (
        (
            "import android.os.Environment\n",
            "import android.view.View\n"
            "import android.view.accessibility.AccessibilityEvent\n",
            "Android accessibility imports",
        ),
        (
            "import androidx.compose.foundation.layout.fillMaxSize\n",
            "import androidx.compose.foundation.layout.size\n",
            "Compose size import",
        ),
        (
            "import androidx.compose.runtime.getValue\n",
            "import androidx.compose.runtime.key\n",
            "Compose key import",
        ),
        (
            "import androidx.compose.ui.semantics.semantics\n",
            "import androidx.compose.ui.unit.dp\n"
            "import androidx.compose.ui.viewinterop.AndroidView\n",
            "AndroidView imports",
        ),
    ):
        source = replace_exact(
            source,
            anchor,
            anchor + addition,
            label,
        )

    start_marker = "private fun FixtureMain("
    end_marker = "\nprivate fun String.toDestination()"
    start = source.find(start_marker)
    if start < 0:
        raise ValueError("QA Activity FixtureMain start marker not found")
    end = source.find(end_marker, start)
    if end < 0:
        raise ValueError("QA Activity FixtureMain end marker not found")
    segment = source[start:end]

    marker_block = """    Box(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = false) {
                contentDescription = buildList {
                    add(pageMarker)
                    if (hasOriginByteProgress) {
                        add(QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER)
                    }
                    if (hasRealDownloadProgress) {
                        add(QA_DOWNLOAD_PROGRESS_MARKER)
                    }
                    lastDownloadAction?.let { marker ->
                        if (state.destination == MainDestination.DOWNLOADS) {
                            add("qa-download-action:$marker")
                        }
                    }
                }
                    .joinToString(",")
            },
    ) {
        MainShellScreen(
"""
    replacement = """    val qualityEvidence = buildList {
        add(pageMarker)
        if (hasOriginByteProgress) {
            add(QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER)
        }
        if (hasRealDownloadProgress) {
            add(QA_DOWNLOAD_PROGRESS_MARKER)
        }
        lastDownloadAction?.let { marker ->
            if (state.destination == MainDestination.DOWNLOADS) {
                add("qa-download-action:$marker")
            }
        }
    }
        .joinToString(",")

    Box(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = false) {
                contentDescription = qualityEvidence
            },
    ) {
        key(qualityEvidence) {
            AndroidView(
                factory = { context ->
                    View(context).apply {
                        importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    }
                },
                update = { view ->
                    view.contentDescription = qualityEvidence
                    view.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    )
                },
                modifier = Modifier.size(1.dp),
            )
        }
        MainShellScreen(
"""
    segment = replace_exact(
        segment,
        marker_block,
        replacement,
        "QA Activity evidence block",
    )
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
        "PASS: native debug accessibility evidence tracks page and transfer state"
    )
    print(f"Canonical src/main digest before injection: {production_before}")
    print(f"Instrumented src/main digest: {production_after}")
    print(f"Marker source SHA-256: {report['original_sha256']}")
    print(f"Marker build SHA-256: {report['instrumented_sha256']}")


if __name__ == "__main__":
    main()
