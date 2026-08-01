#!/usr/bin/env python3
"""Compatibility Lab entrypoint with independently-tested runtime overrides."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
SPEC = importlib.util.spec_from_file_location("compatibility_runtime_core", HERE / "runtime_core.py")
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load runtime_core.py")
CORE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CORE
SPEC.loader.exec_module(CORE)
from qualified_runtime import install
install(CORE)

for _name in dir(CORE):
    if not _name.startswith("__"):
        globals().setdefault(_name, getattr(CORE, _name))

if __name__ == "__main__":
    raise SystemExit(CORE.main())
