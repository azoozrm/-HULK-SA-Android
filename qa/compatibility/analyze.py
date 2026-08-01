#!/usr/bin/env python3
"""Analyzer entrypoint with fail-closed independent policy normalization."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
SPEC = importlib.util.spec_from_file_location("compatibility_analyzer_core", HERE / "analyzer_core.py")
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load analyzer_core.py")
CORE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CORE
SPEC.loader.exec_module(CORE)
from qualification_policy import normalize_summary

for _name in dir(CORE):
    if not _name.startswith("__"):
        globals().setdefault(_name, getattr(CORE, _name))

_core_analyze_run = CORE.analyze_run

def analyze_run(root):
    summary = normalize_summary(_core_analyze_run(root))
    path = Path(root) / "summary.json"
    path.write_text(__import__("json").dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return summary

CORE.analyze_run = analyze_run

if __name__ == "__main__":
    raise SystemExit(CORE.main())
