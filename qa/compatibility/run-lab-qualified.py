#!/usr/bin/env python3
"""Qualified Compatibility Lab entrypoint.

Keeps primary and retry captures separate, selects one result explicitly, and
adds bounded waits/evidence for the real download fixture without weakening the
positive-byte analyzer gate.
"""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import shutil
import sys
import time
from types import ModuleType
from typing import Any
import xml.etree.ElementTree as ET

DOWNLOAD_PROGRESS_MARKER = "qa-download-transfer:bytes-positive"
DOWNLOAD_PAGE_TIMEOUT_SECONDS = 45.0
DOWNLOAD_PROGRESS_TIMEOUT_SECONDS = 30.0
DEFAULT_PAGE_TIMEOUT_SECONDS = 15.0
VARIANT_GEOMETRY_TIMEOUT_SECONDS = 20.0
VARIANT_GEOMETRY_STABLE_READS = 3
TV_DOWNLOAD_LAYOUT_TIMEOUT_SECONDS = 45.0
TV_DOWNLOAD_LAYOUT_STABLE_READS = 3
TV_DOWNLOAD_CARD_MIN_HEIGHT_DP = 150.0


def page_marker_timeout(page: str) -> float:
    return DOWNLOAD_PAGE_TIMEOUT_SECONDS if page == "downloads" else DEFAULT_PAGE_TIMEOUT_SECONDS


def focus_key_pair(page: str) -> tuple[str, str]:
    """Return a reversible focus move that matches the page's input semantics."""
    if page == "search":
        return "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_UP"
    return "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT"


def wait_for_stable_geometry(
    core: ModuleType,
    adb: Any,
    expected: tuple[int, int],
    *,
    timeout: float = VARIANT_GEOMETRY_TIMEOUT_SECONDS,
    stable_reads: int = VARIANT_GEOMETRY_STABLE_READS,
) -> tuple[bool, tuple[int, int] | None]:
    """Require several consecutive screencaps at the requested orientation."""
    deadline = time.monotonic() + timeout
    observed: tuple[int, int] | None = None
    consecutive = 0
    while time.monotonic() < deadline:
        result = adb.run(["exec-out", "screencap", "-p"], timeout=45)
        if result.returncode == 0:
            observed = core.png_dimensions(result.stdout)
            consecutive = consecutive + 1 if observed == expected else 0
            if consecutive >= stable_reads:
                return True, observed
        else:
            consecutive = 0
        time.sleep(0.4)
    return False, observed


def measure_tv_download_layout(
    core: ModuleType,
    xml_bytes: bytes,
    density: int,
) -> dict[str, Any] | None:
    """Measure debug TV download markers without changing the product gate."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    list_bounds: tuple[int, int, int, int] | None = None
    cards: list[tuple[str, tuple[int, int, int, int]]] = []
    transfer_progress = False
    for node in core.app_nodes(root):
        description = (node.attrib.get("content-desc", "") or "").strip()
        if DOWNLOAD_PROGRESS_MARKER in description:
            transfer_progress = True
        bounds = core.parse_bounds(node.attrib.get("bounds", ""))
        if bounds is None:
            continue
        if core.QA_TV_DOWNLOAD_LIST in description:
            list_bounds = bounds
        if core.QA_TV_DOWNLOAD_CARD_PREFIX in description:
            cards.append((description, bounds))
    if list_bounds is None:
        return None

    lx1, ly1, lx2, ly2 = list_bounds
    visible = [
        (description, bounds)
        for description, bounds in cards
        if bounds[0] >= lx1 - 1
        and bounds[1] >= ly1 - 1
        and bounds[2] <= lx2 + 1
        and bounds[3] <= ly2 + 1
    ]
    visible.sort(key=lambda item: (item[1][1], item[1][0]))
    pixels_per_dp = max(density / 160.0, 0.01)
    heights_dp = [
        round((bounds[3] - bounds[1]) / pixels_per_dp, 2)
        for _, bounds in visible
    ]
    overlaps = [
        max(0, first[1][3] - second[1][1])
        for first, second in zip(visible, visible[1:])
        if first[1][3] - second[1][1] > 1
    ]
    return {
        "list_bounds_px": list(list_bounds),
        "visible_cards": [
            {"marker": description, "bounds_px": list(bounds)}
            for description, bounds in visible
        ],
        "visible_card_count": len(visible),
        "visible_card_heights_dp": heights_dp,
        "minimum_card_height_dp": TV_DOWNLOAD_CARD_MIN_HEIGHT_DP,
        "overlaps_px": overlaps,
        "transfer_progress": transfer_progress,
    }


def tv_download_layout_qualified(measurement: dict[str, Any] | None) -> bool:
    if measurement is None:
        return False
    heights = measurement.get("visible_card_heights_dp", [])
    return (
        bool(measurement.get("transfer_progress"))
        and int(measurement.get("visible_card_count", 0)) >= 2
        and len(heights) >= 2
        and min(heights[:2]) >= TV_DOWNLOAD_CARD_MIN_HEIGHT_DP
        and not measurement.get("overlaps_px")
    )


def wait_for_stable_tv_download_layout(
    core: ModuleType,
    adb: Any,
    scratch: Path,
    density: int,
    *,
    timeout: float = TV_DOWNLOAD_LAYOUT_TIMEOUT_SECONDS,
    stable_reads: int = TV_DOWNLOAD_LAYOUT_STABLE_READS,
) -> tuple[bool, bytes | None, dict[str, Any] | None]:
    """Wait for two complete TV cards to remain stable across consecutive dumps."""
    deadline = time.monotonic() + timeout
    consecutive = 0
    previous_signature: str | None = None
    last_xml: bytes | None = None
    last_measurement: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        try:
            last_xml = core.dump_xml(adb, scratch, attempts=1)
        except core.LabError:
            consecutive = 0
            previous_signature = None
            time.sleep(0.5)
            continue
        last_measurement = measure_tv_download_layout(core, last_xml, density)
        if tv_download_layout_qualified(last_measurement):
            signature = json.dumps(
                last_measurement.get("visible_cards", [])[:2],
                sort_keys=True,
            )
            consecutive = consecutive + 1 if signature == previous_signature else 1
            previous_signature = signature
            if consecutive >= stable_reads:
                return True, last_xml, last_measurement
        else:
            consecutive = 0
            previous_signature = None
        time.sleep(0.5)
    return False, last_xml, last_measurement


def _argument_value(argv: list[str], name: str) -> str:
    try:
        return argv[argv.index(name) + 1]
    except (ValueError, IndexError) as exc:
        raise SystemExit(f"missing required argument: {name}") from exc


def _replace_argument(argv: list[str], name: str, value: str) -> None:
    try:
        argv[argv.index(name) + 1] = value
    except (ValueError, IndexError) as exc:
        raise SystemExit(f"missing required argument: {name}") from exc


def _load_core() -> ModuleType:
    path = Path(__file__).with_name("run-lab.py")
    spec = importlib.util.spec_from_file_location("hulk_compatibility_run_lab_core", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"unable to load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def _has_real_manifest(path: Path) -> bool:
    manifest_path = path / "run-manifest.json"
    if not manifest_path.is_file():
        return False
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return False
    cases = manifest.get("cases")
    return isinstance(cases, list) and len(cases) > 0


def _clear_selected_root(root: Path) -> None:
    root.mkdir(parents=True, exist_ok=True)
    for child in list(root.iterdir()):
        if child.name == "attempts":
            continue
        if child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink()


def _copy_tree_contents(source: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    for child in source.iterdir():
        target = destination / child.name
        if child.is_dir():
            shutil.copytree(child, target, dirs_exist_ok=True)
        else:
            shutil.copy2(child, target)


def preserve_primary(root: Path) -> Path:
    primary = root / "attempts" / "primary"
    if primary.exists():
        shutil.rmtree(primary)
    primary.mkdir(parents=True, exist_ok=True)
    for child in list(root.iterdir()):
        if child.name == "attempts":
            continue
        shutil.move(str(child), str(primary / child.name))
    return primary


def select_attempt(root: Path, primary: Path, retry: Path) -> str:
    """Select a real retry capture, otherwise restore the real primary capture."""
    retry_is_real = _has_real_manifest(retry)
    primary_is_real = _has_real_manifest(primary)
    if retry_is_real:
        selected = "retry"
        source = retry
    elif primary_is_real:
        selected = "primary"
        source = primary
    else:
        selected = "retry" if retry.exists() else "primary"
        source = retry if retry.exists() else primary

    _clear_selected_root(root)
    if source.exists():
        _copy_tree_contents(source, root)
    (root / "selected-attempt.json").write_text(
        json.dumps(
            {
                "selected": selected,
                "primary_has_real_manifest": primary_is_real,
                "retry_has_real_manifest": retry_is_real,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    return selected


def install_qualified_behavior(core: ModuleType) -> None:
    original_set_variant = core.DeviceLab.set_variant

    def set_variant(self: Any, orientation: str, font_scale: float) -> None:
        original_set_variant(self, orientation, font_scale)
        expected = core.oriented_dimensions(self.args.width, self.args.height, orientation)
        stable, observed = wait_for_stable_geometry(core, self.adb, expected)
        if not stable:
            observed_label = f"{observed[0]}x{observed[1]}" if observed else "unavailable"
            raise core.LabError(
                f"display did not stabilize at {expected[0]}x{expected[1]} before "
                f"launching {orientation}; last screenshot was {observed_label}"
            )

    def launch_page_once(self: Any, page: str, case_dir: Path, attempt: int):
        is_tv = "true" if self.args.is_tv else "false"
        start = self.adb.shell(
            [
                "am", "start", "-S", "-W", "-n", f"{core.PACKAGE}/{core.ACTIVITY}",
                "--es", "scenario", page,
                "--ez", "isTv", is_tv,
                "--es", "orientation", self.current_orientation,
            ],
            timeout=90,
        )
        start_text = start.text + start.error_text
        core.safe_write(case_dir / f"start-attempt-{attempt}.txt", start_text)
        if start.returncode != 0 or "Error:" in start_text:
            raise core.LabError(f"activity launch failed for {page}: {start_text[-1000:]}")
        marker = f"{core.PAGE_MARKER_PREFIX}{page}"
        scratch = case_dir / f"marker-last-attempt-{attempt}.xml"
        marker_found, last_xml = core.wait_for_marker(
            self.adb,
            marker,
            scratch,
            timeout=page_marker_timeout(page),
        )
        if marker_found:
            scratch.unlink(missing_ok=True)
        elif last_xml is not None:
            core.safe_write(scratch, last_xml)
        return start_text, marker_found, last_xml

    def start_page(self: Any, page: str, case_dir: Path):
        self.adb.shell(["logcat", "-c"])
        self.adb.shell(["logcat", "-b", "crash", "-c"])
        self.adb.shell(["dumpsys", "gfxinfo", core.PACKAGE, "reset"])
        try:
            start_text, marker_found, last_xml = self._launch_page_once(page, case_dir, 1)
            if not marker_found:
                observed_page = core.active_page_marker(last_xml)
                core.safe_write(case_dir / "scenario-observed-attempt-1.txt", observed_page or "none")
                if observed_page and observed_page != page:
                    self.adb.shell(["am", "force-stop", core.PACKAGE])
                    core.time.sleep(1.0)
                    retry_text, marker_found, retry_xml = self._launch_page_once(page, case_dir, 2)
                    start_text += "\n--- retry ---\n" + retry_text
                    if not marker_found:
                        observed_page = core.active_page_marker(retry_xml) or observed_page
                        core.safe_write(case_dir / "scenario-observed-attempt-2.txt", observed_page or "none")
                        if observed_page and observed_page != page:
                            raise core.LabError(
                                f"scenario_activation_failure: requested={page!r}, "
                                f"observed={observed_page!r} after two explicit launches"
                            )
                elif observed_page is None:
                    core.time.sleep(1.5)

            expected = core.oriented_dimensions(
                self.args.width,
                self.args.height,
                self.current_orientation,
            )
            geometry_found, observed = core.wait_for_geometry(self.adb, expected)
            if not geometry_found:
                observed_label = f"{observed[0]}x{observed[1]}" if observed else "unavailable"
                raise core.LabError(
                    f"display did not reach {expected[0]}x{expected[1]} for "
                    f"{self.current_orientation}; last screenshot was {observed_label}"
                )

            if page == "downloads":
                scratch = case_dir / "download-transfer-wait.xml"
                found, transfer_xml = core.wait_for_marker(
                    self.adb,
                    DOWNLOAD_PROGRESS_MARKER,
                    scratch,
                    timeout=DOWNLOAD_PROGRESS_TIMEOUT_SECONDS,
                )
                if transfer_xml is not None:
                    core.safe_write(scratch, transfer_xml)
                core.safe_write(
                    case_dir / "download-transfer-wait.json",
                    json.dumps(
                        {
                            "marker": DOWNLOAD_PROGRESS_MARKER,
                            "found": found,
                            "timeout_seconds": DOWNLOAD_PROGRESS_TIMEOUT_SECONDS,
                            "observed_page": core.active_page_marker(transfer_xml),
                        },
                        indent=2,
                    )
                    + "\n",
                )
                if self.args.is_tv:
                    layout_scratch = case_dir / "tv-download-layout-wait.xml"
                    layout_found, layout_xml, layout_measurement = wait_for_stable_tv_download_layout(
                        core,
                        self.adb,
                        layout_scratch,
                        self.args.density,
                    )
                    if layout_xml is not None:
                        core.safe_write(layout_scratch, layout_xml)
                    core.safe_write(
                        case_dir / "tv-download-layout-wait.json",
                        json.dumps(
                            {
                                "found": layout_found,
                                "timeout_seconds": TV_DOWNLOAD_LAYOUT_TIMEOUT_SECONDS,
                                "stable_reads": TV_DOWNLOAD_LAYOUT_STABLE_READS,
                                "measurement": layout_measurement,
                            },
                            indent=2,
                        )
                        + "\n",
                    )
            else:
                core.time.sleep(0.8)
            return start_text, marker_found
        except Exception:
            try:
                core.dump_xml(self.adb, case_dir / "activation-failure.xml", attempts=2)
            except Exception:
                pass
            core.safe_write(
                case_dir / "activation-failure.logcat.txt",
                core.command_text(
                    self.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
            raise

    def focus_audit(self: Any, orientation: str) -> None:
        if not self.args.is_tv:
            return
        audit_root = self.out / "focus" / orientation
        audit_root.mkdir(parents=True, exist_ok=True)
        entries: list[dict[str, Any]] = []
        for page in core.PAGES:
            page_id = page["id"]
            page_dir = audit_root / page_id
            page_dir.mkdir(parents=True, exist_ok=True)
            move_key, restore_key = focus_key_pair(page_id)
            entry: dict[str, Any] = {
                "orientation": orientation,
                "page": page_id,
                "trace": [],
                "files": {},
                "input_sequence": [move_key, restore_key],
            }
            try:
                self.start_page(page_id, page_dir)
                before_xml = page_dir / "before.xml"
                before_png = page_dir / "before.png"
                expanded_xml = page_dir / "expanded.xml"
                expanded_png = page_dir / "expanded.png"
                restored_xml = page_dir / "restored.xml"
                restored_png = page_dir / "restored.png"

                before = core.dump_xml(self.adb, before_xml)
                core.capture_png(self.adb, before_png)
                before_node = core.focused_node(before)

                self.adb.shell(["input", "keyevent", move_key])
                core.time.sleep(0.5)
                expanded = core.dump_xml(self.adb, expanded_xml)
                core.capture_png(self.adb, expanded_png)
                expanded_node = core.focused_node(expanded)

                self.adb.shell(["input", "keyevent", restore_key])
                core.time.sleep(0.5)
                restored = core.dump_xml(self.adb, restored_xml)
                core.capture_png(self.adb, restored_png)
                restored_node = core.focused_node(restored)

                entry["trace"] = [
                    {"step": "before", "focused": before_node},
                    {"step": "expanded", "focused": expanded_node},
                    {"step": "restored", "focused": restored_node},
                ]
                entry["files"] = {
                    "xml": restored_xml.relative_to(self.out).as_posix(),
                    "screenshot": restored_png.relative_to(self.out).as_posix(),
                }
                if page_id == "home":
                    entry["rail_visual"] = {
                        "collapsed": {
                            "xml": before_xml.relative_to(self.out).as_posix(),
                            "screenshot": before_png.relative_to(self.out).as_posix(),
                        },
                        "expanded": {
                            "xml": expanded_xml.relative_to(self.out).as_posix(),
                            "screenshot": expanded_png.relative_to(self.out).as_posix(),
                        },
                    }
                    entry["expanded_rail_focus"] = core.is_expanded_rail_focus(
                        expanded_node,
                        self.args.width,
                    )
            except Exception as exc:
                entry["error"] = str(exc)[-1200:]
                self.record_harness_error(f"focus:{orientation}:{page_id}", exc)
            entries.append(entry)
        self.manifest["focus"].extend(entries)
        self.flush_manifest()

    core.DeviceLab.set_variant = set_variant
    core.DeviceLab._launch_page_once = launch_page_once
    core.DeviceLab.start_page = start_page
    core.DeviceLab.focus_audit = focus_audit


def main() -> int:
    original_argv = list(sys.argv)
    root = Path(_argument_value(original_argv, "--out")).resolve()
    is_retry = (root / "run-manifest.json").exists() or (root / "summary.json").exists()
    primary = root / "attempts" / "primary"
    retry = root / "attempts" / "retry"

    if is_retry:
        primary = preserve_primary(root)
        if retry.exists():
            shutil.rmtree(retry)
        retry.mkdir(parents=True, exist_ok=True)
        _replace_argument(sys.argv, "--out", str(retry))

    core = _load_core()
    install_qualified_behavior(core)
    exit_code = 0
    caught: BaseException | None = None
    try:
        exit_code = int(core.main())
    except BaseException as exc:  # preserve/choose evidence before propagating
        caught = exc
    finally:
        if is_retry:
            select_attempt(root, primary, retry)
        elif root.exists():
            (root / "selected-attempt.json").write_text(
                json.dumps({"selected": "primary"}, indent=2) + "\n",
                encoding="utf-8",
            )

    if caught is not None:
        raise caught
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
