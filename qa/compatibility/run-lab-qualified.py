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
from types import ModuleType
from typing import Any

DOWNLOAD_PROGRESS_MARKER = "qa-download-transfer:bytes-positive"
DOWNLOAD_PAGE_TIMEOUT_SECONDS = 45.0
DOWNLOAD_PROGRESS_TIMEOUT_SECONDS = 30.0
DEFAULT_PAGE_TIMEOUT_SECONDS = 15.0


def page_marker_timeout(page: str) -> float:
    return DOWNLOAD_PAGE_TIMEOUT_SECONDS if page == "downloads" else DEFAULT_PAGE_TIMEOUT_SECONDS


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

    core.DeviceLab._launch_page_once = launch_page_once
    core.DeviceLab.start_page = start_page


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
