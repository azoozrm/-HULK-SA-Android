#!/usr/bin/env python3
from pathlib import Path

manifest_path = Path('qa/canonical/canonical-source.sha256')
manifest = manifest_path.read_text(encoding='utf-8')
old = '8aa1ed0cd73b75dc7555598298990bbc255d7291493a02070b84369d6f801b12  app/src/androidTest/java/sa/hulksa/player/ui/DownloadsFocusNavigationTest.kt'
new = '05dab578e5c1187459240c8557c5af501009c43498ac6328f6649ab4c6f59c69  app/src/androidTest/java/sa/hulksa/player/ui/DownloadsFocusNavigationTest.kt'
if manifest.count(old) != 1:
    raise SystemExit('expected exactly one old DownloadsFocusNavigationTest hash')
manifest_path.write_text(manifest.replace(old, new), encoding='utf-8')
print('PASS: canonical focus instrumentation hash updated')
