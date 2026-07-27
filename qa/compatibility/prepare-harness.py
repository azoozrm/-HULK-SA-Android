#!/usr/bin/env python3
"""Inject the debug-only Compatibility Lab activity into a prepared project."""

from __future__ import annotations

import hashlib
from pathlib import Path
import sys


def tree_digest(root: Path) -> str:
    digest = hashlib.sha256()
    if not root.exists():
        return digest.hexdigest()
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare-harness.py <prepared-project>")

    project = Path(sys.argv[1]).resolve()
    app = project / "app"
    main_source = app / "src/main"
    if not (project / "settings.gradle.kts").is_file() or not main_source.is_dir():
        raise SystemExit(f"not a prepared Android project: {project}")

    before = tree_digest(main_source)
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
    (source_dir / "QaActivity.kt").write_text(
        source.read_text(encoding="utf-8"),
        encoding="utf-8",
    )

    after = tree_digest(main_source)
    if before != after:
        raise SystemExit("production src/main changed while preparing the QA harness")

    print("PASS: debug-only Compatibility Lab harness prepared")
    print(f"Production source digest: {after}")


if __name__ == "__main__":
    main()
