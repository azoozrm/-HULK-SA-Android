#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import py_compile

ROOT = Path(__file__).resolve().parents[2]
QA_ACTIVITY = ROOT / "qa/compatibility/QaActivity.kt"
RUN_LAB = ROOT / "qa/compatibility/run-lab.py"
TEST_LAB = ROOT / "qa/compatibility/tests/test_lab.py"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def patch_qa_activity() -> None:
    text = QA_ACTIVITY.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import androidx.activity.compose.setContent\n",
        "import androidx.activity.compose.setContent\nimport androidx.lifecycle.lifecycleScope\n",
        "QaActivity lifecycleScope import",
    )
    text = replace_once(
        text,
        "import kotlinx.coroutines.delay\n",
        "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n",
        "QaActivity coroutine imports",
    )
    text = replace_once(
        text,
        "class QaActivity : ComponentActivity() {\n    private var downloadServer: QaRangeServer? = null\n",
        "class QaActivity : ComponentActivity() {\n    private var downloadServer: QaRangeServer? = null\n    private var downloadHarnessState by mutableStateOf<QaDownloadHarness?>(null)\n",
        "QaActivity state",
    )
    old = '''        val downloadHarness = if (scenario == "downloads") {
            prepareDownloadHarness()
        } else {
            null
        }
        setContent {
            HulkTheme {
                QaAuthenticatedShell(
                    initialDestination = scenario.toDestination(),
                    isTv = forcedTv || configTv,
                    downloadHarness = downloadHarness,
                )
            }
        }
'''
    new = '''        setContent {
            HulkTheme {
                QaAuthenticatedShell(
                    initialDestination = scenario.toDestination(),
                    isTv = forcedTv || configTv,
                    downloadHarness = downloadHarnessState,
                )
            }
        }
        if (scenario == "downloads") {
            lifecycleScope.launch {
                downloadHarnessState = withContext(Dispatchers.IO) {
                    prepareDownloadHarness()
                }
            }
        }
'''
    text = replace_once(text, old, new, "QaActivity asynchronous download harness")
    QA_ACTIVITY.write_text(text, encoding="utf-8")


def patch_run_lab() -> None:
    text = RUN_LAB.read_text(encoding="utf-8")
    node_anchor = '''def node_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
'''
    node_helper = '''def node_text_with_descendants(node: ET.Element) -> str:
    """Return semantics text exposed on the focused node or its merged children."""
    values: list[str] = []
    for descendant in node.iter("node"):
        value = node_text(descendant)
        if value and value not in values:
            values.append(value)
    return " ".join(values)


'''
    text = replace_once(text, node_anchor, node_helper + node_anchor, "descendant focus text helper")

    start = text.index("def focused_node(xml_bytes: bytes) -> dict[str, Any] | None:\n")
    end = text.index("\n\ndef is_expanded_rail_focus", start)
    focused = '''def focused_node(xml_bytes: bytes) -> dict[str, Any] | None:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return None
    for node in root.iter("node"):
        if node.attrib.get("focused") != "true":
            continue
        bounds = node_bounds(node)
        return {
            "text": node_text_with_descendants(node),
            "class": node.attrib.get("class", ""),
            "bounds": list(bounds) if bounds else None,
            "clickable": node.attrib.get("clickable") == "true",
            "focusable": node.attrib.get("focusable") == "true",
        }
    return None
'''
    text = text[:start] + focused + text[end:]

    old_sequence = '''        key_sequence = [
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
'''
    new_sequence = '''        if self.args.is_tv and page == "live":
            # RTL path: rail -> channel -> play -> favorite -> play -> channel -> next channel.
            key_sequence = [
                ("LEFT", 21),
                ("LEFT", 21),
                ("LEFT", 21),
                ("RIGHT", 22),
                ("RIGHT", 22),
                ("DOWN", 20),
            ]
        elif self.args.is_tv and page == "downloads":
            # Wi-Fi -> schedule -> concurrent -> row-1 cancel -> row-2 cancel -> priority -> primary.
            key_sequence = [
                ("LEFT", 21),
                ("LEFT", 21),
                ("DOWN", 20),
                ("DOWN", 20),
                ("RIGHT", 22),
                ("RIGHT", 22),
                ("UP", 19),
                ("LEFT", 21),
            ]
        else:
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
'''
    text = replace_once(text, old_sequence, new_sequence, "page-specific focus sequence")

    start = text.index("    def download_action_audit(self, orientation: str) -> None:\n")
    end = text.index("\n    def run(self) -> None:\n", start)
    function = '''    def download_action_audit(self, orientation: str) -> None:
        audit_root = self.out / "focus" / orientation / "downloads-actions"
        audit_root.mkdir(parents=True, exist_ok=True)
        checks: list[dict[str, Any]] = []
        known_markers: set[tuple[str, int]] = set()
        sequence_number = 0
        error: str | None = None

        def restart(scope: str) -> None:
            nonlocal known_markers
            case_dir = audit_root / scope
            case_dir.mkdir(parents=True, exist_ok=True)
            self.start_page("downloads", case_dir)
            initial_xml = dump_xml(self.adb, case_dir / ".initial.xml", attempts=2)
            known_markers = download_action_markers(initial_xml)
            (case_dir / ".initial.xml").unlink(missing_ok=True)

        def inspect(
            check_id: str,
            *,
            key_code: int | None = None,
            expected_labels: tuple[str, ...] = (),
            expected_action: str | None = None,
        ) -> bool:
            nonlocal sequence_number, known_markers
            sequence_number += 1
            step_root = audit_root / f"{sequence_number:02d}-{check_id}"
            step_root.mkdir(parents=True, exist_ok=True)
            previous = set(known_markers)
            before_xml = dump_xml(self.adb, step_root / "before.xml", attempts=2)
            focused_before = focused_node(before_xml)
            before_label = str((focused_before or {}).get("text") or "")
            before_focus_seen = not expected_labels or any(
                expected in before_label for expected in expected_labels
            )
            if key_code is not None:
                self.adb.shell(["input", "keyevent", str(key_code)])
            deadline = time.monotonic() + (5.0 if expected_action else 1.5)
            xml = before_xml
            markers = set(previous)
            focused_after = focused_before
            action_seen = expected_action is None
            while time.monotonic() < deadline:
                time.sleep(0.20)
                xml = dump_xml(self.adb, step_root / "ui.xml", attempts=2)
                markers = download_action_markers(xml)
                focused_after = focused_node(xml)
                if expected_action is not None:
                    action_seen = any(
                        action == expected_action and marker not in previous
                        for marker in markers
                        for action in (marker[0],)
                    )
                    if action_seen:
                        break
                else:
                    label = str((focused_after or {}).get("text") or "")
                    if focused_after is not None and (
                        not expected_labels or any(expected in label for expected in expected_labels)
                    ):
                        break
            known_markers = markers
            after_label = str((focused_after or {}).get("text") or "")
            after_focus_seen = not expected_labels or any(
                expected in after_label for expected in expected_labels
            )
            if expected_action is not None:
                success = bool(focused_before) and before_focus_seen and action_seen
                focused_evidence = focused_before
            else:
                success = bool(focused_after) and after_focus_seen
                focused_evidence = focused_after
            capture_png(self.adb, step_root / "screenshot.png")
            safe_write(
                step_root / "logcat.txt",
                command_text(
                    self.adb,
                    ["logcat", "-d", "-v", "threadtime"],
                    shell=False,
                    timeout=90,
                ),
            )
            checks.append(
                {
                    "id": check_id,
                    "success": success,
                    "expected_labels": list(expected_labels),
                    "expected_action": expected_action,
                    "focused": focused_evidence,
                    "focused_before": focused_before,
                    "focused_after": focused_after,
                    "action_markers": [
                        f"{action}:{revision}" for action, revision in sorted(markers)
                    ],
                    "reason": None if success else (
                        "expected action marker was not emitted"
                        if expected_action is not None and not action_seen
                        else "expected focused control was not reached"
                        if not (before_focus_seen if expected_action is not None else after_focus_seen)
                        else "no focused control was exposed"
                    ),
                    "evidence": {
                        "screenshot": (step_root / "screenshot.png").relative_to(self.out).as_posix(),
                        "before_xml": (step_root / "before.xml").relative_to(self.out).as_posix(),
                        "xml": (step_root / "ui.xml").relative_to(self.out).as_posix(),
                        "logcat": (step_root / "logcat.txt").relative_to(self.out).as_posix(),
                    },
                }
            )
            return success

        try:
            # First prove the complete D-pad graph without mutating download state.
            restart("navigation")
            inspect("top-wifi-initial", expected_labels=("كل الشبكات", "WiFi فقط"))
            inspect("top-schedule-focus", key_code=21, expected_labels=("الجدولة",))
            inspect("top-concurrent-focus", key_code=21, expected_labels=("متزامنة",))
            inspect("row-1-cancel", key_code=20, expected_labels=("الغاء",))
            inspect("row-2-cancel", key_code=20, expected_labels=("الغاء",))
            inspect("row-2-priority", key_code=22, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("row-2-primary", key_code=22, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-primary", key_code=19, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-priority", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))

            # Execute each state-changing callback from a fresh deterministic page.
            restart("wifi-action")
            inspect("top-wifi-action-focus", expected_labels=("كل الشبكات", "WiFi فقط"))
            inspect("top-wifi-executes", key_code=23, expected_labels=("كل الشبكات", "WiFi فقط"), expected_action="wifi")

            restart("schedule-action")
            inspect("top-schedule-action-focus", key_code=21, expected_labels=("الجدولة",))
            inspect("top-schedule-executes", key_code=23, expected_labels=("الجدولة",), expected_action="schedule")

            restart("concurrent-action")
            inspect("top-concurrent-action-schedule", key_code=21, expected_labels=("الجدولة",))
            inspect("top-concurrent-action-focus", key_code=21, expected_labels=("متزامنة",))
            inspect("top-concurrent-executes", key_code=23, expected_labels=("متزامنة",), expected_action="concurrent")

            restart("pause-action")
            inspect("pause-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-pause-executes", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="pause")

            restart("priority-action")
            inspect("priority-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("priority-row-1-focus", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("row-1-priority-executes", key_code=23, expected_labels=("عالية", "عادية", "منخفضة"), expected_action="priority")

            restart("cancel-action")
            inspect("cancel-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("cancel-row-1-priority", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("cancel-row-1-focus", key_code=21, expected_labels=("الغاء",))
            inspect("cancel-row-1-executes", key_code=23, expected_labels=("الغاء",), expected_action="cancel")
        except Exception as exc:
            error = f"{type(exc).__name__}: {exc}"[-1200:]
            self.record_harness_error(f"download-actions:{orientation}", exc)
        finally:
            result = {
                "orientation": orientation,
                "page": "downloads",
                "success": error is None and all(check.get("success") for check in checks),
                "error": error,
                "checks": checks,
            }
            self.manifest["download_actions"].append(result)
            safe_write(
                audit_root / "download-actions.json",
                json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            )
            self.flush_manifest()
'''
    text = text[:start] + function + text[end:]
    RUN_LAB.write_text(text, encoding="utf-8")


def patch_tests() -> None:
    text = TEST_LAB.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "download_action_markers = RUN_LAB_MODULE.download_action_markers\n",
        "download_action_markers = RUN_LAB_MODULE.download_action_markers\nfocused_node = RUN_LAB_MODULE.focused_node\n",
        "focused_node test import",
    )
    insertion_anchor = '''    def test_download_fixture_contains_expected_client_disconnects(self) -> None:
'''
    tests = '''    def test_focused_node_reads_text_from_semantics_descendants(self) -> None:
        xml = (
            '<hierarchy><node package="sa.hulksa.player.dev" focused="true" '
            'focusable="true" clickable="true" bounds="[0,0][200,80]">'
            '<node package="sa.hulksa.player.dev" text="ايقاف مؤقت" '
            'bounds="[20,10][180,70]" /></node></hierarchy>'
        ).encode()
        self.assertEqual("ايقاف مؤقت", focused_node(xml)["text"])

    def test_download_fixture_prepares_repository_off_main_thread(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        self.assertIn("downloadHarnessState by mutableStateOf<QaDownloadHarness?>(null)", source)
        self.assertIn("lifecycleScope.launch", source)
        self.assertIn("withContext(Dispatchers.IO)", source)
        self.assertLess(source.index("setContent {"), source.index("withContext(Dispatchers.IO)"))

    def test_tv_focus_sequences_are_page_specific_and_actions_are_isolated(self) -> None:
        source = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        self.assertIn('page == "live"', source)
        self.assertIn('page == "downloads"', source)
        for scope in (
            'restart("navigation")',
            'restart("wifi-action")',
            'restart("schedule-action")',
            'restart("concurrent-action")',
            'restart("pause-action")',
            'restart("priority-action")',
            'restart("cancel-action")',
        ):
            self.assertIn(scope, source)
        self.assertIn('"before_xml"', source)
        self.assertIn("node_text_with_descendants", source)

'''
    text = replace_once(text, insertion_anchor, tests + insertion_anchor, "new lab regression tests")
    TEST_LAB.write_text(text, encoding="utf-8")


def main() -> None:
    patch_qa_activity()
    patch_run_lab()
    patch_tests()
    py_compile.compile(str(RUN_LAB), doraise=True)
    py_compile.compile(str(TEST_LAB), doraise=True)
    print("API28 and TV focus audit repair applied cleanly")


if __name__ == "__main__":
    main()
