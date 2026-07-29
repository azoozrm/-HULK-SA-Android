#!/usr/bin/env python3
"""Drive the debug-only HULK SA Compatibility Lab on one emulator."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
from typing import Any, Iterable
import xml.etree.ElementTree as ET

from lab_config import PAGES


PACKAGE = "sa.hulksa.player.dev"
ACTIVITY = "sa.hulksa.player.qa.QaActivity"
PAGE_MARKER_PREFIX = "qa-page:"
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
TRANSIENT_ADB_RE = re.compile(
    r"device offline|device ['\"][^'\"]+['\"] not found|"
    r"no devices/emulators found|cannot connect to daemon|"
    r"protocol fault|connection reset",
    re.IGNORECASE,
)


class LabError(RuntimeError):
    pass


@dataclass
class CommandResult:
    returncode: int
    stdout: bytes
    stderr: bytes

    @property
    def text(self) -> str:
        return self.stdout.decode("utf-8", errors="replace")

    @property
    def error_text(self) -> str:
        return self.stderr.decode("utf-8", errors="replace")


class Adb:
    def __init__(self, serial: str | None = None) -> None:
        self.prefix = ["adb"]
        if serial:
            self.prefix += ["-s", serial]

    def run(
        self,
        args: Iterable[str],
        *,
        check: bool = False,
        timeout: int = 120,
        input_bytes: bytes | None = None,
    ) -> CommandResult:
        command = self.prefix + [str(item) for item in args]
        result: CommandResult | None = None
        for attempt in range(4):
            try:
                completed = subprocess.run(
                    command,
                    input=input_bytes,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    timeout=timeout,
                    check=False,
                )
            except subprocess.TimeoutExpired as exc:
                raise LabError(
                    f"command timed out after {timeout}s: {' '.join(command)}"
                ) from exc
            result = CommandResult(completed.returncode, completed.stdout, completed.stderr)
            detail = (result.error_text + "\n" + result.text).strip()
            if (
                result.returncode == 0
                or attempt == 3
                or not TRANSIENT_ADB_RE.search(detail)
            ):
                break
            print(
                f"ADB transport unavailable; recovery attempt {attempt + 1}/3",
                file=sys.stderr,
                flush=True,
            )
            try:
                subprocess.run(
                    self.prefix + ["wait-for-device"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    timeout=min(timeout, 30),
                    check=False,
                )
            except subprocess.TimeoutExpired:
                pass
            time.sleep(0.75 * (attempt + 1))
        assert result is not None
        if check and result.returncode != 0:
            detail = (result.error_text or result.text).strip()[-1200:]
            raise LabError(f"command failed ({result.returncode}): {' '.join(command)}\n{detail}")
        return result

    def shell(
        self,
        args: Iterable[str],
        *,
        check: bool = False,
        timeout: int = 120,
    ) -> CommandResult:
        return self.run(["shell", *args], check=check, timeout=timeout)


def parse_bool(value: str) -> bool:
    lowered = value.strip().lower()
    if lowered in {"1", "true", "yes", "on"}:
        return True
    if lowered in {"0", "false", "no", "off"}:
        return False
    raise argparse.ArgumentTypeError(f"invalid boolean: {value}")


def safe_write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def command_text(adb: Adb, args: Iterable[str], *, shell: bool = True, timeout: int = 120) -> str:
    result = adb.shell(args, timeout=timeout) if shell else adb.run(args, timeout=timeout)
    return result.text + (("\n" + result.error_text) if result.error_text else "")


def dump_xml(adb: Adb, destination: Path, attempts: int = 5) -> bytes:
    last_error = ""
    for _ in range(attempts):
        result = adb.shell(
            ["uiautomator", "dump", "--compressed", "/sdcard/hulk-qa-window.xml"],
            timeout=45,
        )
        if result.returncode == 0:
            pulled = adb.run(["exec-out", "cat", "/sdcard/hulk-qa-window.xml"], timeout=30)
            if pulled.returncode == 0 and b"<hierarchy" in pulled.stdout:
                safe_write(destination, pulled.stdout)
                return pulled.stdout
            last_error = (pulled.error_text or pulled.text).strip()
        else:
            last_error = (result.error_text or result.text).strip()
        time.sleep(0.6)
    raise LabError(f"UI hierarchy unavailable: {last_error[-600:]}")


def capture_png(adb: Adb, destination: Path) -> bytes:
    result = adb.run(["exec-out", "screencap", "-p"], timeout=45)
    if result.returncode != 0 or not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
        detail = (result.error_text or result.text).strip()
        raise LabError(f"screenshot capture failed: {detail[-600:]}")
    safe_write(destination, result.stdout)
    return result.stdout


def png_dimensions(data: bytes) -> tuple[int, int] | None:
    if len(data) < 24 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return None
    return struct.unpack(">II", data[16:24])


def wait_for_geometry(
    adb: Adb,
    expected: tuple[int, int],
    timeout: float = 12.0,
) -> tuple[bool, tuple[int, int] | None]:
    deadline = time.monotonic() + timeout
    observed: tuple[int, int] | None = None
    while time.monotonic() < deadline:
        result = adb.run(["exec-out", "screencap", "-p"], timeout=45)
        if result.returncode == 0:
            observed = png_dimensions(result.stdout)
            if observed == expected:
                return True, observed
        time.sleep(0.5)
    return False, observed


def wait_for_marker(adb: Adb, marker: str, scratch: Path, timeout: float = 12.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            data = dump_xml(adb, scratch, attempts=1)
            if marker.encode("utf-8") in data:
                return True
            dialog_center = external_error_dialog_center(data)
            if dialog_center:
                adb.shell(
                    ["input", "tap", str(dialog_center[0]), str(dialog_center[1])]
                )
                time.sleep(0.8)
                continue
        except LabError:
            pass
        time.sleep(0.5)
    return False


def node_text(node: ET.Element) -> str:
    return " ".join(
        value.strip()
        for value in (
            node.attrib.get("text", ""),
            node.attrib.get("content-desc", ""),
        )
        if value.strip()
    )


def node_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(node.attrib.get("bounds", ""))
    if not match:
        return None
    return tuple(map(int, match.groups()))  # type: ignore[return-value]


def find_node_center(xml_bytes: bytes, label: str) -> tuple[int, int] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    exact: list[tuple[int, tuple[int, int, int, int], ET.Element]] = []
    partial: list[tuple[int, tuple[int, int, int, int], ET.Element]] = []
    for node in root.iter("node"):
        bounds = node_bounds(node)
        if not bounds:
            continue
        x1, y1, x2, y2 = bounds
        if x2 <= x1 or y2 <= y1:
            continue
        text = node_text(node)
        if not text:
            continue
        score = int(node.attrib.get("clickable") == "true") * 2 + int(
            node.attrib.get("focusable") == "true"
        )
        entry = (score, bounds, node)
        if text == label:
            exact.append(entry)
        elif label in text:
            partial.append(entry)
    candidates = exact or partial
    if not candidates:
        return None
    candidates.sort(key=lambda item: (-item[0], (item[1][2] - item[1][0]) * (item[1][3] - item[1][1])))
    _, (x1, y1, x2, y2), _ = candidates[0]
    return ((x1 + x2) // 2, (y1 + y2) // 2)


def external_error_dialog_center(xml_bytes: bytes) -> tuple[int, int] | None:
    """Find the close action for a non-HULK Android application-error dialog."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    title = ""
    close_bounds: tuple[int, int, int, int] | None = None
    for node in root.iter("node"):
        resource_id = node.attrib.get("resource-id", "")
        if resource_id == "android:id/alertTitle":
            title = node.attrib.get("text", "").strip()
        elif resource_id == "android:id/aerr_close":
            close_bounds = node_bounds(node)
    if not title or not close_bounds or "hulk" in title.casefold():
        return None
    x1, y1, x2, y2 = close_bounds
    if x2 <= x1 or y2 <= y1:
        return None
    return ((x1 + x2) // 2, (y1 + y2) // 2)


def focused_node(xml_bytes: bytes) -> dict[str, Any] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    for node in root.iter("node"):
        if node.attrib.get("focused") != "true":
            continue
        bounds = node_bounds(node)
        return {
            "text": node_text(node),
            "class": node.attrib.get("class", ""),
            "bounds": list(bounds) if bounds else None,
            "clickable": node.attrib.get("clickable") == "true",
            "focusable": node.attrib.get("focusable") == "true",
        }
    return None


def is_expanded_rail_focus(
    node: dict[str, Any] | None,
    display_width: int,
) -> bool:
    """Recognize a focused item after the RTL TV navigation rail expands."""
    if not node or not node.get("bounds") or display_width <= 0:
        return False
    x1, _, x2, _ = node["bounds"]
    node_width = x2 - x1
    return (
        x1 >= display_width * 0.65
        and x2 >= display_width * 0.90
        and node_width >= display_width * 0.14
    )


def parse_start_metrics(text: str) -> dict[str, int]:
    result: dict[str, int] = {}
    for key in ("ThisTime", "TotalTime", "WaitTime"):
        match = re.search(rf"^{key}:\s*(\d+)", text, re.MULTILINE)
        if match:
            result[key] = int(match.group(1))
    return result


def oriented_dimensions(width: int, height: int, orientation: str) -> tuple[int, int]:
    if orientation == "landscape" and width < height:
        return height, width
    if orientation == "portrait" and width > height:
        return height, width
    return width, height


class DeviceLab:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.out = args.out.resolve()
        self.raw = self.out / "raw"
        self.adb = Adb(args.serial)
        self.current_orientation = "landscape" if args.is_tv else "portrait"
        self.manifest: dict[str, Any] = {
            "schema_version": 2,
            "device": {
                "id": args.device_id,
                "name": args.device_name,
                "family": args.family,
                "api": args.api,
                "target": args.target,
                "arch": args.arch,
                "profile": args.profile,
                "requested_width": args.width,
                "requested_height": args.height,
                "requested_density": args.density,
                "orientations": args.orientations,
                "font_scales": args.font_scales,
                "is_tv": args.is_tv,
            },
            "pages": list(PAGES),
            "cases": [],
            "navigation": [],
            "focus": [],
            "harness_errors": [],
            "started_at_epoch": int(time.time()),
        }
        self.out.mkdir(parents=True, exist_ok=True)
        self.flush_manifest()

    def flush_manifest(self) -> None:
        self.manifest["updated_at_epoch"] = int(time.time())
        safe_write(
            self.out / "run-manifest.json",
            json.dumps(self.manifest, ensure_ascii=False, indent=2) + "\n",
        )

    def record_harness_error(self, scope: str, error: BaseException | str) -> None:
        message = str(error).replace("\x00", "")[-2000:]
        self.manifest["harness_errors"].append({"scope": scope, "message": message})
        self.flush_manifest()

    def setup(self) -> None:
        self.adb.run(["wait-for-device"], check=True, timeout=180)
        boot = self.adb.shell(["getprop", "sys.boot_completed"], check=True).text.strip()
        if boot != "1":
            raise LabError(f"emulator is not fully booted: sys.boot_completed={boot!r}")

        for namespace, key, value in (
            ("global", "window_animation_scale", "0"),
            ("global", "transition_animation_scale", "0"),
            ("global", "animator_duration_scale", "0"),
            ("global", "hide_error_dialogs", "1"),
            ("global", "show_first_crash_dialog", "0"),
            ("global", "show_restart_in_crash_dialog", "0"),
            ("secure", "show_ime_with_hard_keyboard", "0"),
            ("secure", "anr_show_background", "0"),
            ("global", "debug.force_rtl", "1"),
        ):
            self.adb.shell(["settings", "put", namespace, key, value])

        install = self.adb.run(
            ["install", "-r", "-t", str(self.args.apk.resolve())],
            timeout=240,
        )
        safe_write(self.out / "install.txt", install.text + install.error_text)
        if install.returncode != 0:
            raise LabError(f"APK installation failed: {(install.error_text or install.text)[-1000:]}")

        packages = self.adb.shell(["pm", "list", "packages", PACKAGE], check=True).text
        if f"package:{PACKAGE}" not in packages:
            raise LabError(f"installed package not found: {PACKAGE}")

        self.adb.shell(["pm", "clear", PACKAGE], check=True)
        self.adb.shell(["wm", "size", f"{self.args.width}x{self.args.height}"], check=True)
        self.adb.shell(["wm", "density", str(self.args.density)], check=True)
        self.adb.shell(["settings", "put", "system", "accelerometer_rotation", "0"])
        self.adb.shell(["input", "keyevent", "82"])
        time.sleep(1)

        metadata_commands = {
            "adb-devices.txt": (False, ["devices", "-l"]),
            "wm-size.txt": (True, ["wm", "size"]),
            "wm-density.txt": (True, ["wm", "density"]),
            "display.txt": (True, ["dumpsys", "display"]),
            "features.txt": (True, ["pm", "list", "features"]),
            "properties.txt": (
                True,
                [
                    "getprop",
                ],
            ),
        }
        for filename, (shell, command) in metadata_commands.items():
            safe_write(
                self.out / "device" / filename,
                command_text(self.adb, command, shell=shell, timeout=90),
            )
        self.flush_manifest()

    def set_variant(self, orientation: str, font_scale: float) -> None:
        self.current_orientation = orientation
        rotation = 0 if orientation == "portrait" else 1
        if self.args.is_tv:
            rotation = 0
        self.adb.shell(["settings", "put", "system", "font_scale", f"{font_scale:.2f}"])
        self.adb.shell(["settings", "put", "system", "user_rotation", str(rotation)])
        self.adb.shell(["wm", "user-rotation", "lock", str(rotation)])
        self.adb.shell(["am", "force-stop", PACKAGE])
        time.sleep(1.0)

    def start_page(self, page: str, case_dir: Path) -> tuple[str, bool]:
        self.adb.shell(["logcat", "-c"])
        self.adb.shell(["logcat", "-b", "crash", "-c"])
        self.adb.shell(["dumpsys", "gfxinfo", PACKAGE, "reset"])
        is_tv = "true" if self.args.is_tv else "false"
        start = self.adb.shell(
            [
                "am",
                "start",
                "-S",
                "-W",
                "-n",
                f"{PACKAGE}/{ACTIVITY}",
                "--es",
                "scenario",
                page,
                "--ez",
                "isTv",
                is_tv,
                "--es",
                "orientation",
                self.current_orientation,
            ],
            timeout=90,
        )
        start_text = start.text + start.error_text
        safe_write(case_dir / "start.txt", start_text)
        if start.returncode != 0 or "Error:" in start_text:
            raise LabError(f"activity launch failed for {page}: {start_text[-1000:]}")
        marker = f"{PAGE_MARKER_PREFIX}{page}"
        marker_scratch = case_dir / ".marker.xml"
        marker_found = wait_for_marker(
            self.adb,
            marker,
            marker_scratch,
            timeout=15,
        )
        expected = oriented_dimensions(
            self.args.width,
            self.args.height,
            self.current_orientation,
        )
        geometry_found, observed = wait_for_geometry(self.adb, expected)
        if not geometry_found:
            observed_label = (
                f"{observed[0]}x{observed[1]}" if observed else "unavailable"
            )
            raise LabError(
                f"display did not reach {expected[0]}x{expected[1]} for "
                f"{self.current_orientation}; last screenshot was "
                f"{observed_label}"
            )
        marker_scratch.unlink(missing_ok=True)
        # Downloads use a debug-only loopback origin and must prove actual byte
        # progress. Give the repository enough time to probe and receive at
        # least one bounded chunk before capturing the hierarchy.
        time.sleep(3.0 if page == "downloads" else 0.8)
        return start_text, marker_found

    def capture_case(self, page: str, orientation: str, font_scale: float) -> None:
        scale_id = f"font-{int(round(font_scale * 100)):03d}"
        case_id = f"{orientation}/{scale_id}/{page}"
        case_dir = self.raw / orientation / scale_id / page
        case_dir.mkdir(parents=True, exist_ok=True)
        record: dict[str, Any] = {
            "id": case_id,
            "page": page,
            "orientation": orientation,
            "font_scale": font_scale,
            "marker": f"{PAGE_MARKER_PREFIX}{page}",
            "marker_found": False,
            "files": {},
            "capture_error": None,
        }
        try:
            start_text, marker_found = self.start_page(page, case_dir)
            record["marker_found"] = marker_found
            record["start_metrics_ms"] = parse_start_metrics(start_text)

            file_map = {
                "screenshot": case_dir / "screenshot.png",
                "xml": case_dir / "ui.xml",
                "logcat": case_dir / "logcat.txt",
                "system_events": case_dir / "system-events.logcat.txt",
                "crash_log": case_dir / "crash.logcat.txt",
                "gfxinfo": case_dir / "gfxinfo.txt",
                "meminfo": case_dir / "meminfo.txt",
                "window": case_dir / "window.txt",
                "activity": case_dir / "activity.txt",
            }
            capture_png(self.adb, file_map["screenshot"])
            dump_xml(self.adb, file_map["xml"])
            pid = self.adb.shell(["pidof", PACKAGE]).text.strip().split()
            log_args = ["logcat", "-d", "-v", "threadtime"]
            if pid:
                log_args += ["--pid", pid[0]]
                record["pid"] = int(pid[0]) if pid[0].isdigit() else pid[0]
            safe_write(file_map["logcat"], command_text(self.adb, log_args, shell=False, timeout=90))
            safe_write(
                file_map["system_events"],
                command_text(
                    self.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
            safe_write(
                file_map["crash_log"],
                command_text(
                    self.adb,
                    ["logcat", "-b", "crash", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=60,
                ),
            )
            safe_write(
                file_map["gfxinfo"],
                command_text(self.adb, ["dumpsys", "gfxinfo", PACKAGE, "framestats"], timeout=90),
            )
            safe_write(
                file_map["meminfo"],
                command_text(self.adb, ["dumpsys", "meminfo", PACKAGE], timeout=90),
            )
            safe_write(
                file_map["window"],
                command_text(self.adb, ["dumpsys", "window", "windows"], timeout=90),
            )
            safe_write(
                file_map["activity"],
                command_text(self.adb, ["dumpsys", "activity", "activities"], timeout=90),
            )
            for key, path in file_map.items():
                record["files"][key] = path.relative_to(self.out).as_posix()
        except Exception as exc:
            record["capture_error"] = str(exc)[-1800:]
            self.record_harness_error(f"capture:{case_id}", exc)
            try:
                capture_png(self.adb, case_dir / "failure.png")
                record["files"]["failure_screenshot"] = (
                    case_dir / "failure.png"
                ).relative_to(self.out).as_posix()
            except Exception:
                pass
            safe_write(
                case_dir / "failure.logcat.txt",
                command_text(self.adb, ["logcat", "-d", "-v", "threadtime"], shell=False, timeout=90),
            )
            record["files"]["failure_logcat"] = (
                case_dir / "failure.logcat.txt"
            ).relative_to(self.out).as_posix()
        finally:
            marker_scratch = case_dir / ".marker.xml"
            if marker_scratch.exists():
                marker_scratch.unlink()
            self.manifest["cases"].append(record)
            self.flush_manifest()

    def navigation_audit(self, orientation: str) -> None:
        audit_dir = self.out / "navigation" / orientation
        audit_dir.mkdir(parents=True, exist_ok=True)
        entries: list[dict[str, Any]] = []
        try:
            _, home_marker_found = self.start_page("home", audit_dir)
            for page in PAGES:
                page_id = page["id"]
                label = page["label"]
                marker = f"{PAGE_MARKER_PREFIX}{page_id}"
                entry: dict[str, Any] = {
                    "orientation": orientation,
                    "page": page_id,
                    "label": label,
                    "success": page_id == "home" and home_marker_found,
                    "reason": None,
                }
                if page_id == "home":
                    if not entry["success"]:
                        entry["reason"] = "home destination marker was not observed"
                    entries.append(entry)
                    continue

                center: tuple[int, int] | None = None
                last_xml = b""
                for attempt in range(8):
                    last_xml = dump_xml(self.adb, audit_dir / ".navigation.xml", attempts=2)
                    center = find_node_center(last_xml, label)
                    if center:
                        break
                    width, height = oriented_dimensions(
                        self.args.width,
                        self.args.height,
                        orientation,
                    )
                    y = max(40, int(height * 0.045))
                    if attempt % 2 == 0:
                        self.adb.shell(
                            ["input", "swipe", str(int(width * 0.78)), str(y), str(int(width * 0.22)), str(y), "350"]
                        )
                    else:
                        self.adb.shell(
                            ["input", "swipe", str(int(width * 0.22)), str(y), str(int(width * 0.78)), str(y), "350"]
                        )
                    time.sleep(0.5)
                if center is None:
                    entry["reason"] = "navigation item not exposed in the visible UI hierarchy"
                else:
                    entry["tap"] = list(center)
                    self.adb.shell(["input", "tap", str(center[0]), str(center[1])])
                    entry["success"] = wait_for_marker(
                        self.adb,
                        marker,
                        audit_dir / ".navigation-target.xml",
                        timeout=10,
                    )
                    if not entry["success"]:
                        entry["reason"] = f"destination marker did not become {marker}"
                if not entry["success"]:
                    evidence = audit_dir / page_id
                    evidence.mkdir(parents=True, exist_ok=True)
                    try:
                        capture_png(self.adb, evidence / "screenshot.png")
                        dump_xml(self.adb, evidence / "ui.xml")
                    except Exception:
                        pass
                    safe_write(
                        evidence / "logcat.txt",
                        command_text(
                            self.adb,
                            ["logcat", "-d", "-v", "threadtime"],
                            shell=False,
                            timeout=90,
                        ),
                    )
                    entry["evidence"] = evidence.relative_to(self.out).as_posix()
                entries.append(entry)
        except Exception as exc:
            self.record_harness_error(f"navigation:{orientation}", exc)
            entries.append(
                {
                    "orientation": orientation,
                    "page": "<audit>",
                    "success": False,
                    "reason": str(exc)[-1000:],
                }
            )
        finally:
            for scratch in audit_dir.glob(".navigation*.xml"):
                scratch.unlink(missing_ok=True)
            self.manifest["navigation"].extend(entries)
            safe_write(
                audit_dir / "navigation.json",
                json.dumps(entries, ensure_ascii=False, indent=2) + "\n",
            )
            self.flush_manifest()

    def focus_audit(self, page: str, orientation: str) -> None:
        audit_dir = self.out / "focus" / orientation / page
        audit_dir.mkdir(parents=True, exist_ok=True)
        key_sequence = [
            ("RIGHT", 22),
            ("LEFT", 21),
            ("UP", 19),
            ("DOWN", 20),
            ("DOWN", 20),
            ("DOWN", 20),
            ("RIGHT", 22),
            ("RIGHT", 22),
            ("LEFT", 21),
            ("UP", 19),
            ("DOWN", 20),
            ("DOWN", 20),
        ]
        trace: list[dict[str, Any]] = []
        rail_visual: dict[str, dict[str, str]] = {}
        error: str | None = None
        try:
            self.start_page(page, audit_dir)
            is_home_tv = page == "home" and self.args.is_tv
            initial_xml_path = (
                audit_dir / "rail-collapsed.xml"
                if is_home_tv
                else audit_dir / ".focus.xml"
            )
            initial = dump_xml(self.adb, initial_xml_path)
            if is_home_tv:
                collapsed_png = audit_dir / "rail-collapsed.png"
                capture_png(self.adb, collapsed_png)
                rail_visual["collapsed"] = {
                    "screenshot": collapsed_png.relative_to(self.out).as_posix(),
                    "xml": initial_xml_path.relative_to(self.out).as_posix(),
                }
            trace.append({"step": 0, "key": "INITIAL", "focused": focused_node(initial)})
            display_width, _ = oriented_dimensions(
                self.args.width,
                self.args.height,
                orientation,
            )
            for index, (name, code) in enumerate(key_sequence, start=1):
                self.adb.shell(["input", "keyevent", str(code)])
                time.sleep(0.25)
                xml = dump_xml(self.adb, audit_dir / ".focus.xml", attempts=2)
                focused = focused_node(xml)
                trace.append({"step": index, "key": name, "focused": focused})
                if (
                    is_home_tv
                    and "expanded" not in rail_visual
                    and is_expanded_rail_focus(focused, display_width)
                ):
                    time.sleep(0.35)
                    expanded_xml_path = audit_dir / "rail-expanded.xml"
                    expanded_xml = dump_xml(self.adb, expanded_xml_path, attempts=2)
                    if is_expanded_rail_focus(focused_node(expanded_xml), display_width):
                        expanded_png = audit_dir / "rail-expanded.png"
                        capture_png(self.adb, expanded_png)
                        rail_visual["expanded"] = {
                            "screenshot": expanded_png.relative_to(self.out).as_posix(),
                            "xml": expanded_xml_path.relative_to(self.out).as_posix(),
                        }
            capture_png(self.adb, audit_dir / "screenshot.png")
            final_xml = dump_xml(self.adb, audit_dir / "ui.xml")
            safe_write(
                audit_dir / "logcat.txt",
                command_text(
                    self.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
            safe_write(
                audit_dir / "window.txt",
                command_text(self.adb, ["dumpsys", "window", "windows"], timeout=90),
            )
            del final_xml
        except Exception as exc:
            error = str(exc)[-1200:]
            self.record_harness_error(f"focus:{orientation}:{page}", exc)
        finally:
            (audit_dir / ".focus.xml").unlink(missing_ok=True)
            result = {
                "orientation": orientation,
                "page": page,
                "trace": trace,
                "rail_visual": rail_visual,
                "error": error,
                "files": {
                    key: (audit_dir / filename).relative_to(self.out).as_posix()
                    for key, filename in (
                        ("screenshot", "screenshot.png"),
                        ("xml", "ui.xml"),
                        ("logcat", "logcat.txt"),
                        ("window", "window.txt"),
                    )
                    if (audit_dir / filename).exists()
                },
            }
            self.manifest["focus"].append(result)
            safe_write(
                audit_dir / "focus-trace.json",
                json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            )
            self.flush_manifest()

    def run(self) -> None:
        self.setup()
        orientations = [item.strip() for item in self.args.orientations.split(",") if item.strip()]
        font_scales = [float(item.strip()) for item in self.args.font_scales.split(",") if item.strip()]
        for orientation in orientations:
            for font_scale in font_scales:
                self.set_variant(orientation, font_scale)
                for page in PAGES:
                    self.capture_case(page["id"], orientation, font_scale)
            self.set_variant(orientation, 1.0)
            self.navigation_audit(orientation)
            if self.args.is_tv:
                for page in PAGES:
                    self.focus_audit(page["id"], orientation)
        self.manifest["finished_at_epoch"] = int(time.time())
        self.flush_manifest()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--device-name", required=True)
    parser.add_argument("--family", choices=("phone", "tablet", "tv"), required=True)
    parser.add_argument("--api", type=int, required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--arch", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--density", type=int, required=True)
    parser.add_argument("--orientations", required=True)
    parser.add_argument("--font-scales", required=True)
    parser.add_argument("--is-tv", type=parse_bool, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--serial")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    lab = DeviceLab(args)
    try:
        lab.run()
    except Exception as exc:
        lab.record_harness_error("device-setup-or-run", exc)
        try:
            safe_write(
                lab.out / "device-failure.logcat.txt",
                command_text(
                    lab.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
        except Exception as log_error:
            lab.record_harness_error("device-failure-logcat", log_error)
    try:
        from analyze import analyze_run

        summary = analyze_run(lab.out)
    except Exception as exc:
        lab.record_harness_error("analysis", exc)
        print(f"Compatibility Lab analysis failed: {exc}", file=sys.stderr)
        return 2

    print(
        f"{summary['overall_status']}: {summary['device']['name']} — "
        f"{summary['case_count']} captures, {summary['critical_count']} critical, "
        f"{summary['warning_count']} warnings, "
        f"{summary['infrastructure_error_count']} infrastructure errors"
    )
    if summary["infrastructure_error_count"]:
        return 2
    if summary["critical_count"]:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
