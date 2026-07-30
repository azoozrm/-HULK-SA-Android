"""Scope-aware entry point adapter for the Quality Lab aggregate reporter.

The implementation remains in the adjacent ``aggregate.py`` module.  This
package deliberately re-exports that implementation so existing imports keep
working while ``python -m qa.quality.reporters.aggregate`` can apply the
workflow's lab-only exit policy without changing the evidence or release
recommendation written by the reporter.
"""

from __future__ import annotations

import importlib.util
from pathlib import Path
from types import ModuleType


_IMPLEMENTATION_PATH = Path(__file__).resolve().parent.parent / "aggregate.py"
_SPEC = importlib.util.spec_from_file_location(
    "qa.quality.reporters._aggregate_implementation",
    _IMPLEMENTATION_PATH,
)
if _SPEC is None or _SPEC.loader is None:
    raise ImportError(f"Unable to load aggregate reporter from {_IMPLEMENTATION_PATH}")

IMPLEMENTATION: ModuleType = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(IMPLEMENTATION)

# Preserve the public API used by the Quality Lab tests and callers.
for _name in dir(IMPLEMENTATION):
    if not _name.startswith("__"):
        globals()[_name] = getattr(IMPLEMENTATION, _name)

__all__ = [
    name
    for name in dir(IMPLEMENTATION)
    if not name.startswith("_")
]
