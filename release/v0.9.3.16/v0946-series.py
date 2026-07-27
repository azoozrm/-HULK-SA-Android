#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rep(path, old, new, label, count=1):
    p = root / path
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    if old not in s:
        raise SystemExit(f"missing {label}")
    p.write_text(s.replace(old, new, count), encoding="utf-8")


series = "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt"

rep(
    series,
    '''            contentPadding = PaddingValues(
                start = if (isTv) 20.dp else 12.dp,
                end = if (isTv) 20.dp else 12.dp,
                bottom = if (isTv) 42.dp else 28.dp,
            ),
''',
    '''            contentPadding = PaddingValues(
                start = if (isTv) 36.dp else 12.dp,
                end = if (isTv) 36.dp else 12.dp,
                bottom = if (isTv) 42.dp else 28.dp,
            ),
''',
    "series episode grid safe padding",
)

rep(
    series,
    '''                    modifier = Modifier.padding(
                        start = if (isTv) 18.dp else 7.dp,
                        end = if (isTv) 18.dp else 7.dp,
                    ),
''',
    '''                    modifier = Modifier.padding(
                        start = if (isTv) 10.dp else 7.dp,
                        end = if (isTv) 10.dp else 7.dp,
                    ),
''',
    "series episode card balanced padding",
)

print("Prepared v0.9.3.16 series episode safe padding")
