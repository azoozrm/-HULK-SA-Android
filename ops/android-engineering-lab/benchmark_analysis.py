#!/usr/bin/env python3
"""Bounded analysis helpers for Gate 3 (macrobenchmark) evidence.

This module only reads benchmark JSON and returns structured summaries. It deliberately
keeps the target application compilation state separate from the self-instrumenting test
APK context: the macrobenchmark JSON `context.compilationMode` describes the test APK, not
the target app under test, so it is labelled explicitly and never used as the target state.

The functions are importable so they can be covered by deterministic fixture tests.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any, Dict, List, Optional

TEST_APK_CONTEXT_NOTE = (
    "context.compilationMode describes the self-instrumenting macrobenchmark TEST APK "
    "and must not be used as the target application compilation state"
)


def load_benchmark_data(path: str | Path) -> Dict[str, Any]:
    with Path(path).open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError("benchmark JSON root is not an object")
    return data


def metric_runs(metric: Optional[Dict[str, Any]]) -> List[float]:
    if not isinstance(metric, dict):
        return []
    runs = metric.get("runs")
    if not isinstance(runs, list):
        return []
    values: List[float] = []
    for run in runs:
        if isinstance(run, (int, float)):
            values.append(float(run))
    return values


def classify_metric(metric: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    """Classify a metric without guessing. UNKNOWN when evidence is insufficient."""
    if metric is None:
        return {"status": "UNKNOWN", "reason": "metric absent from JSON"}
    if not isinstance(metric, dict):
        return {"status": "INVALID", "reason": "metric is not an object"}

    runs = metric_runs(metric)
    if not runs:
        return {"status": "MISSING", "reason": "metric has no numeric runs"}

    for value in runs:
        if not math.isfinite(value):
            return {"status": "INVALID", "reason": "non-finite sample present"}

    cv = metric.get("coefficientOfVariation")
    noisy = isinstance(cv, (int, float)) and float(cv) > 0.3
    return {
        "status": "VALID",
        "reason": "usable samples",
        "noisy": noisy,
        "count": len(runs),
    }


def summarize_benchmarks(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    benchmarks = data.get("benchmarks")
    if not isinstance(benchmarks, list):
        return []
    summaries: List[Dict[str, Any]] = []
    for benchmark in benchmarks:
        if not isinstance(benchmark, dict):
            continue
        metrics = benchmark.get("metrics")
        metric_summaries: Dict[str, Any] = {}
        if isinstance(metrics, dict):
            for name, entry in metrics.items():
                classification = classify_metric(entry if isinstance(entry, dict) else None)
                metric_summaries[name] = {
                    "classification": classification,
                    "minimum": entry.get("minimum") if isinstance(entry, dict) else None,
                    "median": entry.get("median") if isinstance(entry, dict) else None,
                    "maximum": entry.get("maximum") if isinstance(entry, dict) else None,
                    "coefficientOfVariation": entry.get("coefficientOfVariation")
                    if isinstance(entry, dict)
                    else None,
                    "runs": metric_runs(entry if isinstance(entry, dict) else None),
                }
        summaries.append(
            {
                "className": benchmark.get("className", ""),
                "name": benchmark.get("name", ""),
                "metrics": metric_summaries,
            }
        )
    return summaries


def compilation_context(data: Dict[str, Any]) -> Dict[str, Any]:
    context = data.get("context") if isinstance(data.get("context"), dict) else {}
    return {
        "test_apk_context_compilationMode": context.get("compilationMode"),
        "test_apk_context_sdk": (context.get("build") or {}).get("version", {}).get("sdk")
        if isinstance(context.get("build"), dict)
        else None,
        "note": TEST_APK_CONTEXT_NOTE,
    }


def main(argv: Optional[List[str]] = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("benchmark_json")
    args = parser.parse_args(argv)

    data = load_benchmark_data(args.benchmark_json)
    print(json.dumps(
        {
            "compilation_context": compilation_context(data),
            "benchmarks": summarize_benchmarks(data),
        },
        ensure_ascii=False,
        indent=2,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
