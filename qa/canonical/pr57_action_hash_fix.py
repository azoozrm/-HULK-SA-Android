#!/usr/bin/env python3
from pathlib import Path

manifest_path = Path('qa/canonical/canonical-source.sha256')
manifest = manifest_path.read_text(encoding='utf-8')
old = '05dab578e5c1187459240c8557c5af501009c43498ac6328f6649ab4c6f59c69  app/src/androidTest/java/sa/hulksa/player/ui/DownloadsFocusNavigationTest.kt'
new = 'e4d77606b8646957eebbf341269c6137524c93da9f1a6e023fbfa035b2124edc  app/src/androidTest/java/sa/hulksa/player/ui/DownloadsFocusNavigationTest.kt'
if manifest.count(old) != 1:
    raise SystemExit('expected exactly one previous DownloadsFocusNavigationTest hash')
manifest_path.write_text(manifest.replace(old, new), encoding='utf-8')
print('PASS: canonical action instrumentation hash updated')
