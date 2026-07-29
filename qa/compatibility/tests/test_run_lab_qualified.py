from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

LAB_ROOT = Path(__file__).resolve().parents[1]
RUN_LAB_QUALIFIED_SPEC = importlib.util.spec_from_file_location(
    "compatibility_run_lab_qualified",
    LAB_ROOT / "run-lab-qualified.py",
)
assert RUN_LAB_QUALIFIED_SPEC and RUN_LAB_QUALIFIED_SPEC.loader
RUN_LAB_QUALIFIED = importlib.util.module_from_spec(RUN_LAB_QUALIFIED_SPEC)
sys.modules[RUN_LAB_QUALIFIED_SPEC.name] = RUN_LAB_QUALIFIED
RUN_LAB_QUALIFIED_SPEC.loader.exec_module(RUN_LAB_QUALIFIED)


class Result:
    def __init__(self, dimensions: tuple[int, int] | None, returncode: int = 0) -> None:
        self.returncode = returncode
        self.stdout = b"" if dimensions is None else f"{dimensions[0]}x{dimensions[1]}".encode()


class FakeAdb:
    def __init__(self, samples: list[tuple[int, int] | None]) -> None:
        self.samples = iter(samples)
        self.calls = 0

    def run(self, args, timeout=45):
        self.calls += 1
        return Result(next(self.samples))


def layout_core() -> SimpleNamespace:
    bounds_re = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
    return SimpleNamespace(
        app_nodes=lambda root: root.iter("node"),
        parse_bounds=lambda raw: (
            tuple(map(int, bounds_re.fullmatch(raw).groups()))
            if bounds_re.fullmatch(raw)
            else None
        ),
        QA_TV_DOWNLOAD_LIST="qa-tv-download-list",
        QA_TV_DOWNLOAD_CARD_PREFIX="qa-tv-download-card:",
    )


class QualifiedHarnessRegressionTests(unittest.TestCase):
    def test_search_uses_vertical_focus_move_and_other_pages_use_rail_move(self) -> None:
        self.assertEqual(
            ("KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_UP"),
            RUN_LAB_QUALIFIED.focus_key_pair("search"),
        )
        self.assertEqual(
            ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT"),
            RUN_LAB_QUALIFIED.focus_key_pair("home"),
        )

    def test_geometry_requires_consecutive_matching_frames(self) -> None:
        core = SimpleNamespace(
            png_dimensions=lambda raw: tuple(map(int, raw.decode().split("x"))) if raw else None,
        )
        adb = FakeAdb(
            [
                (1440, 3120),
                (3120, 1440),
                (1440, 3120),
                (3120, 1440),
                (3120, 1440),
                (3120, 1440),
            ]
        )
        ticks = iter([0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7])
        with (
            patch.object(RUN_LAB_QUALIFIED.time, "monotonic", side_effect=lambda: next(ticks)),
            patch.object(RUN_LAB_QUALIFIED.time, "sleep"),
        ):
            stable, observed = RUN_LAB_QUALIFIED.wait_for_stable_geometry(
                core,
                adb,
                (3120, 1440),
                timeout=5.0,
                stable_reads=3,
            )
        self.assertTrue(stable)
        self.assertEqual((3120, 1440), observed)
        self.assertEqual(6, adb.calls)

    def test_geometry_failure_reports_last_observed_size(self) -> None:
        core = SimpleNamespace(
            png_dimensions=lambda raw: tuple(map(int, raw.decode().split("x"))) if raw else None,
        )
        adb = FakeAdb([(1440, 3120), (3120, 1440), (1440, 3120)])
        ticks = iter([0.0, 0.1, 0.2, 0.3, 0.4])
        with (
            patch.object(RUN_LAB_QUALIFIED.time, "monotonic", side_effect=lambda: next(ticks)),
            patch.object(RUN_LAB_QUALIFIED.time, "sleep"),
        ):
            stable, observed = RUN_LAB_QUALIFIED.wait_for_stable_geometry(
                core,
                adb,
                (3120, 1440),
                timeout=0.35,
                stable_reads=3,
            )
        self.assertFalse(stable)
        self.assertEqual((1440, 3120), observed)

    def test_tv_download_layout_requires_two_complete_non_overlapping_cards(self) -> None:
        xml = (
            '<hierarchy>'
            '<node content-desc="qa-download-transfer:bytes-positive" bounds="[0,0][1920,1080]" />'
            '<node content-desc="qa-tv-download-list" bounds="[20,340][1700,1080]" />'
            '<node content-desc="qa-tv-download-card:1" bounds="[20,340][1500,668]" />'
            '<node content-desc="qa-tv-download-card:2" bounds="[20,696][1500,1024]" />'
            '</hierarchy>'
        ).encode()
        measurement = RUN_LAB_QUALIFIED.measure_tv_download_layout(
            layout_core(),
            xml,
            density=320,
        )
        self.assertTrue(RUN_LAB_QUALIFIED.tv_download_layout_qualified(measurement))
        assert measurement is not None
        self.assertEqual(2, measurement["visible_card_count"])

    def test_tv_download_layout_rejects_one_card_even_with_positive_bytes(self) -> None:
        xml = (
            '<hierarchy>'
            '<node content-desc="qa-download-transfer:bytes-positive" bounds="[0,0][3840,2160]" />'
            '<node content-desc="qa-tv-download-list" bounds="[32,762][3448,2128]" />'
            '<node content-desc="qa-tv-download-card:1" bounds="[568,1360][3448,2016]" />'
            '</hierarchy>'
        ).encode()
        measurement = RUN_LAB_QUALIFIED.measure_tv_download_layout(
            layout_core(),
            xml,
            density=640,
        )
        self.assertFalse(RUN_LAB_QUALIFIED.tv_download_layout_qualified(measurement))
        assert measurement is not None
        self.assertEqual(1, measurement["visible_card_count"])


if __name__ == "__main__":
    unittest.main()
