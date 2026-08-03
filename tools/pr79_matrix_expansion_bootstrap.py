#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:160]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


path = "tools/pr79_matrix_expansion.py"
replace_once(
    path,
    '''    family = str(profile["device_family"])
    expected_device = {
        "phone": "MOBILE",
        "tablet": "TABLET",
        "foldable": "TABLET" if min(logical_width, logical_height) >= 600 else "MOBILE",
        "tv": "TELEVISION",
    }[family]
''',
    '''    family = str(profile.get("device_family") or profile.get("form_factor") or "")
    expected_device = {
        "phone": "MOBILE",
        "tablet": "TABLET",
        "foldable": "TABLET" if min(logical_width, logical_height) >= 600 else "MOBILE",
        "tv": "TELEVISION",
        "television": "TELEVISION",
    }.get(family)
    if expected_device is None:
        raise SystemExit(f"Unsupported profile family {family!r}: {profile.get('id')}")
''',
)

text = Path(path).read_text(encoding="utf-8")
for marker in ("profile.get(\"form_factor\")", '"television": "TELEVISION"'):
    if marker not in text:
        raise SystemExit(f"Missing matrix bootstrap marker: {marker}")
