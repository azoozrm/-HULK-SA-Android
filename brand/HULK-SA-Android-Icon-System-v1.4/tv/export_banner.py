#!/usr/bin/env python3
"""Export current flat-gold TV banners; authoring dependency: export_assets.py."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from export_assets import export_banners

if __name__ == "__main__":
    export_banners()
