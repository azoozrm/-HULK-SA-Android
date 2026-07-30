#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return source.replace(old, new, 1)


def replace_between(source: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = source.find(start)
    if start_index < 0:
        raise SystemExit(f"{label}: start marker not found")
    end_index = source.find(end, start_index + len(start))
    if end_index < 0:
        raise SystemExit(f"{label}: end marker not found")
    return source[:start_index] + replacement + source[end_index:]


def patch_activity() -> None:
    path = ROOT / "qa/compatibility/QaActivity.kt"
    source = path.read_text(encoding="utf-8")
    fixture_main = r'''@Composable
private fun FixtureMain(
    initial: HulkUiState,
    initialPage: MainDestination,
    downloadRepository: DownloadRepository?,
    originProgress: () -> Boolean,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(initial) }
    var actionRevision by remember { mutableStateOf(0) }
    var lastDownloadAction by remember { mutableStateOf<String?>(null) }

    fun publishDownloadAction(action: String) {
        actionRevision += 1
        lastDownloadAction = "$action:$actionRevision"
    }

    fun refreshDownloads(repository: DownloadRepository) {
        state = state.copy(
            downloads = repository.downloads(),
            downloadSettings = repository.settings(),
        )
    }

    LaunchedEffect(downloadRepository) {
        while (downloadRepository != null) {
            val downloads = downloadRepository.downloads()
            val settings = downloadRepository.settings()
            if (downloads != state.downloads || settings != state.downloadSettings) {
                state = state.copy(downloads = downloads, downloadSettings = settings)
            }
            delay(250L)
        }
    }
    val page = state.destination.name.lowercase(Locale.US)
    val transferredBytes = state.downloads.maxOfOrNull(OfflineDownload::bytesDownloaded) ?: 0L
    val rootDescription = buildList {
        add("qa-page:$page")
        if (state.destination == MainDestination.DOWNLOADS && originProgress()) {
            add(QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER)
        }
        if (state.destination == MainDestination.DOWNLOADS && transferredBytes > 0L) {
            add(QA_DOWNLOAD_PROGRESS_MARKER)
        }
        lastDownloadAction?.let { marker ->
            if (state.destination == MainDestination.DOWNLOADS) {
                add("qa-download-action:$marker")
            }
        }
    }.joinToString(",")
    Box(Modifier.fillMaxSize().semantics { contentDescription = rootDescription }) {
        MainShellScreen(
            state = state,
            isTv = initialPage == MainDestination.HOME && isTelevision(context),
            navigationMemory = remember { NavigationMemoryStore() },
            isFavorite = { item -> "${item.type.name}:${item.id}" in state.favorites },
            onSelectDestination = { destination -> state = state.copy(destination = destination) },
            onSelectCategory = { category -> state = state.copy(selectedCategoryId = category?.id) },
            onSearch = { query -> state = state.copy(searchQuery = query) },
            onOpen = {},
            onOpenHistory = {},
            onToggleFavorite = {},
            onRefresh = {},
            onClearHistory = {},
            onPlayDownload = {},
            onDeleteDownload = { item ->
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(downloads = state.downloads.filterNot { it.downloadId == item.downloadId })
                } else {
                    repository.remove(item.downloadId)
                    refreshDownloads(repository)
                }
                publishDownloadAction("cancel")
            },
            onRetryDownload = { item ->
                val repository = downloadRepository
                if (repository != null) {
                    val action = when (item.status) {
                        OfflineStatus.QUEUED,
                        OfflineStatus.CHECKING,
                        OfflineStatus.DOWNLOADING,
                        -> {
                            repository.pause(item.downloadId)
                            "pause"
                        }
                        OfflineStatus.PAUSED,
                        OfflineStatus.WAITING_SCHEDULE,
                        OfflineStatus.WAITING_NETWORK,
                        OfflineStatus.WAITING_STORAGE,
                        OfflineStatus.FAILED,
                        -> {
                            repository.resume(item.downloadId)
                            "resume"
                        }
                        OfflineStatus.COMPLETED -> "play"
                    }
                    refreshDownloads(repository)
                    publishDownloadAction(action)
                }
            },
            onToggleWifiOnly = {
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloadSettings = state.downloadSettings.copy(
                            wifiOnly = !state.downloadSettings.wifiOnly,
                        ),
                    )
                } else {
                    repository.setWifiOnly(!state.downloadSettings.wifiOnly)
                    refreshDownloads(repository)
                }
                publishDownloadAction("wifi")
            },
            onToggleDownloadSchedule = {
                val next = if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NOW) {
                    DownloadScheduleMode.NIGHT
                } else {
                    DownloadScheduleMode.NOW
                }
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(downloadSettings = state.downloadSettings.copy(scheduleMode = next))
                } else {
                    repository.setScheduleMode(next)
                    refreshDownloads(repository)
                }
                publishDownloadAction("schedule")
            },
            onCycleConcurrentDownloads = {
                val next = (state.downloadSettings.concurrentDownloads % 3) + 1
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloadSettings = state.downloadSettings.copy(concurrentDownloads = next),
                    )
                } else {
                    repository.setConcurrentDownloads(next)
                    refreshDownloads(repository)
                }
                publishDownloadAction("concurrent")
            },
            onCycleDownloadPriority = { item ->
                val repository = downloadRepository
                if (repository != null) {
                    repository.cyclePriority(item.downloadId)
                    refreshDownloads(repository)
                }
                publishDownloadAction("priority")
            },
            onRunDiagnostics = {},
            onLogout = {},
        )
    }
}

'''
    source = replace_between(
        source,
        "@Composable\nprivate fun FixtureMain(",
        "private class QaRangeServer",
        fixture_main,
        "FixtureMain",
    )
    source = replace_once(
        source,
        "private const val QA_DOWNLOAD_WRITE_DELAY_MS = 10L",
        "private const val QA_DOWNLOAD_WRITE_DELAY_MS = 100L",
        "fixture transfer delay",
    )
    required = (
        'repository.pause(item.downloadId)',
        'repository.resume(item.downloadId)',
        'repository.cyclePriority(item.downloadId)',
        'repository.setConcurrentDownloads(next)',
        'repository.remove(item.downloadId)',
        'qa-download-action:$marker',
    )
    missing = [item for item in required if item not in source]
    if missing:
        raise SystemExit(f"QaActivity missing real action contracts: {missing}")
    path.write_text(source, encoding="utf-8")


def patch_runner() -> None:
    path = ROOT / "qa/compatibility/run-lab.py"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        'DOWNLOAD_ORIGIN_MARKER = "qa-download-origin:bytes-positive"\n',
        'DOWNLOAD_ORIGIN_MARKER = "qa-download-origin:bytes-positive"\n'
        'DOWNLOAD_ACTION_RE = re.compile(r"qa-download-action:([a-z]+):(\\d+)")\n',
        "download action marker regex",
    )
    helper_anchor = '''def visible_package_names(xml_bytes: bytes) -> list[str]:
'''
    helper_index = source.find(helper_anchor)
    if helper_index < 0:
        raise SystemExit("visible_package_names helper not found")
    next_class = source.find("\n\n@dataclass", helper_index)
    if next_class < 0:
        raise SystemExit("dataclass anchor after helpers not found")
    helpers = source[helper_index:next_class]
    if "def download_action_markers" not in helpers:
        source = source[:next_class] + r'''


def download_action_markers(xml_bytes: bytes) -> set[tuple[str, int]]:
    text = xml_bytes.decode("utf-8", errors="ignore")
    return {
        (match.group(1), int(match.group(2)))
        for match in DOWNLOAD_ACTION_RE.finditer(text)
    }
''' + source[next_class:]

    method = r'''
    def download_action_audit(self, orientation: str, root: Path) -> dict[str, Any]:
        audit_root = root / "focus" / orientation / "downloads-actions"
        audit_root.mkdir(parents=True, exist_ok=True)
        checks: list[dict[str, Any]] = []
        known_markers: set[tuple[str, int]] = set()
        sequence_number = 0

        def restart(scope: str) -> None:
            nonlocal known_markers
            case_dir = audit_root / scope
            case_dir.mkdir(parents=True, exist_ok=True)
            self.apply_case_settings(orientation, 1.0)
            self.start_page("downloads", case_dir)
            known_markers = download_action_markers(self.adb.dump_ui())

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
            if key_code is not None:
                self.adb.key(key_code)
            deadline = time.monotonic() + (5.0 if expected_action else 1.5)
            xml = b""
            markers: set[tuple[str, int]] = set()
            focused: dict[str, Any] | None = None
            action_seen = expected_action is None
            while time.monotonic() < deadline:
                time.sleep(0.20)
                xml = self.adb.dump_ui()
                markers = download_action_markers(xml)
                focused = focused_node(xml)
                if expected_action is not None:
                    action_seen = any(
                        action == expected_action and marker not in previous
                        for marker in markers
                        for action in (marker[0],)
                    )
                if focused is not None and action_seen:
                    break
            known_markers = markers
            label = ""
            if focused:
                label = str(focused.get("text") or focused.get("content_description") or "")
            focus_seen = not expected_labels or any(expected in label for expected in expected_labels)
            success = bool(focused) and focus_seen and action_seen
            (step_root / "ui.xml").write_bytes(xml or self.adb.dump_ui())
            (step_root / "screenshot.png").write_bytes(self.adb.screencap())
            (step_root / "logcat.txt").write_bytes(self.adb.run(["logcat", "-d", "-v", "threadtime"]).stdout)
            checks.append(
                {
                    "id": check_id,
                    "success": success,
                    "expected_labels": list(expected_labels),
                    "expected_action": expected_action,
                    "focused": focused,
                    "action_markers": [f"{action}:{revision}" for action, revision in sorted(markers)],
                    "reason": None if success else (
                        "expected action marker was not emitted"
                        if not action_seen
                        else "expected focused control was not reached"
                        if not focus_seen
                        else "no focused control was exposed"
                    ),
                    "evidence": {
                        "screenshot": (step_root / "screenshot.png").relative_to(root).as_posix(),
                        "xml": (step_root / "ui.xml").relative_to(root).as_posix(),
                        "logcat": (step_root / "logcat.txt").relative_to(root).as_posix(),
                    },
                }
            )
            return success

        try:
            restart("top-wifi")
            inspect("top-wifi-initial", expected_labels=("كل الشبكات", "WiFi فقط"))
            inspect("top-wifi-executes", key_code=23, expected_action="wifi")

            restart("top-schedule")
            inspect("top-schedule-focus", key_code=21, expected_labels=("الجدولة",))
            inspect("top-schedule-executes", key_code=23, expected_action="schedule")

            restart("top-concurrent")
            inspect("top-concurrent-schedule", key_code=21, expected_labels=("الجدولة",))
            inspect("top-concurrent-focus", key_code=21, expected_labels=("متزامنة",))
            inspect("top-concurrent-executes", key_code=23, expected_action="concurrent")

            restart("rows")
            inspect("row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-pause", key_code=23, expected_action="pause")
            inspect("row-1-priority", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("row-1-priority-executes", key_code=23, expected_action="priority")
            inspect("row-1-cancel", key_code=21, expected_labels=("الغاء",))
            inspect("row-2-cancel", key_code=20, expected_labels=("الغاء",))
            inspect("row-2-priority", key_code=22, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("row-2-primary", key_code=22, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-2-pause", key_code=23, expected_action="pause")

            restart("cancel")
            inspect("cancel-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("cancel-row-1-priority", key_code=21, expected_labels=("عالية", "عادية", "منخفضة"))
            inspect("cancel-row-1-focus", key_code=21, expected_labels=("الغاء",))
            inspect("cancel-row-1-executes", key_code=23, expected_action="cancel")
        except Exception as exc:
            return {
                "orientation": orientation,
                "page": "downloads",
                "success": False,
                "error": f"{type(exc).__name__}: {exc}",
                "checks": checks,
            }

        return {
            "orientation": orientation,
            "page": "downloads",
            "success": all(check.get("success") for check in checks),
            "error": None,
            "checks": checks,
        }

'''
    run_anchor = "    def run(self) -> int:\n"
    source = replace_once(source, run_anchor, method + run_anchor, "download action audit method")
    source = replace_once(
        source,
        '            "focus": [],\n            "harness_errors": [],\n',
        '            "focus": [],\n            "download_actions": [],\n            "harness_errors": [],\n',
        "manifest download action collection",
    )
    focus_loop = '''                if self.device["is_tv"]:
                    for page in self.pages:
                        try:
                            manifest["focus"].append(self.focus_audit(page, orientation, root))
                        except Exception as exc:
                            manifest["harness_errors"].append(
                                {"scope": f"focus:{orientation}:{page}", "message": f"{type(exc).__name__}: {exc}"}
                            )
'''
    focus_loop_with_actions = focus_loop + '''                    try:
                        manifest["download_actions"].append(
                            self.download_action_audit(orientation, root)
                        )
                    except Exception as exc:
                        manifest["harness_errors"].append(
                            {
                                "scope": f"download-actions:{orientation}",
                                "message": f"{type(exc).__name__}: {exc}",
                            }
                        )
'''
    source = replace_once(source, focus_loop, focus_loop_with_actions, "runner action audit call")
    required = (
        "def download_action_audit",
        '"download_actions": []',
        "expected_action=\"pause\"",
        "expected_action=\"priority\"",
        "expected_action=\"cancel\"",
    )
    missing = [item for item in required if item not in source]
    if missing:
        raise SystemExit(f"run-lab missing action contracts: {missing}")
    path.write_text(source, encoding="utf-8")


def patch_analyzer() -> None:
    path = ROOT / "qa/compatibility/analyze.py"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        'QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER = "qa-download-origin:bytes-positive"\n',
        'QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER = "qa-download-origin:bytes-positive"\n'
        'TV_FOCUS_MIN_UNIQUE_TARGETS = {\n'
        '    "home": 3,\n'
        '    "live": 4,\n'
        '    "movies": 3,\n'
        '    "series": 3,\n'
        '    "favorites": 3,\n'
        '    "search": 3,\n'
        '    "downloads": 6,\n'
        '    "settings": 3,\n'
        '}\n',
        "focus coverage policy",
    )
    old_focus = '''        elif len(signatures) < 2:
            item["status"] = "FAIL"
            findings.append(
                finding(
                    "critical",
                    "focus_trap",
                    f"{entry.get('orientation')} / {entry.get('page')}: focus never moved to a second target",
                    page=entry.get("page"),
                    evidence=evidence,
                )
            )
'''
    new_focus = '''        else:
            minimum_targets = TV_FOCUS_MIN_UNIQUE_TARGETS.get(entry.get("page"), 3)
            item["minimum_unique_focus_targets"] = minimum_targets
            if len(signatures) < minimum_targets:
                item["status"] = "FAIL"
                findings.append(
                    finding(
                        "critical",
                        "focus_coverage_incomplete",
                        f"{entry.get('orientation')} / {entry.get('page')}: focus reached "
                        f"{len(signatures)} unique target(s); expected at least {minimum_targets}",
                        page=entry.get("page"),
                        evidence=evidence,
                    )
                )
'''
    source = replace_once(source, old_focus, new_focus, "focus coverage analyzer")

    action_analyzer = r'''

def analyze_download_actions(
    root: Path,
    device: dict[str, Any],
    entries: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    if not device.get("is_tv"):
        return [], []
    normalized: list[dict[str, Any]] = []
    findings: list[dict[str, Any]] = []
    required_checks = {
        "top-wifi-executes",
        "top-schedule-executes",
        "top-concurrent-executes",
        "row-1-primary",
        "row-1-pause",
        "row-1-priority",
        "row-1-priority-executes",
        "row-1-cancel",
        "row-2-cancel",
        "row-2-priority",
        "row-2-primary",
        "row-2-pause",
        "cancel-row-1-executes",
    }
    for entry in entries:
        item = dict(entry)
        checks = [dict(check) for check in entry.get("checks", [])]
        item["checks"] = checks
        item["status"] = "PASS"
        if entry.get("error"):
            item["status"] = "BLOCKED"
            findings.append(
                finding(
                    "infrastructure",
                    "download_action_audit_error",
                    f"{entry.get('orientation')} / downloads: {entry['error']}",
                    page="downloads",
                )
            )
            normalized.append(item)
            continue
        observed_ids = {check.get("id") for check in checks}
        missing = sorted(required_checks - observed_ids)
        if missing:
            item["status"] = "BLOCKED"
            findings.append(
                finding(
                    "infrastructure",
                    "download_action_audit_incomplete",
                    f"{entry.get('orientation')} / downloads: missing checks {', '.join(missing)}",
                    page="downloads",
                )
            )
        for check in checks:
            if check.get("success"):
                continue
            evidence = {
                key: value
                for key, value in check.get("evidence", {}).items()
                if (root / value).is_file()
            }
            item["status"] = "FAIL" if item["status"] != "BLOCKED" else item["status"]
            expected_action = check.get("expected_action")
            code = (
                "tv_download_action_not_executed"
                if expected_action
                else "tv_download_action_unreachable"
            )
            findings.append(
                finding(
                    "critical",
                    code,
                    f"{entry.get('orientation')} / downloads / {check.get('id')}: "
                    f"{check.get('reason') or 'required control contract failed'}",
                    page="downloads",
                    evidence=evidence,
                )
            )
        row_two_ids = {"row-2-cancel", "row-2-priority", "row-2-primary", "row-2-pause"}
        if not row_two_ids.issubset({check.get("id") for check in checks if check.get("success")}):
            if item["status"] != "BLOCKED":
                item["status"] = "FAIL"
            findings.append(
                finding(
                    "critical",
                    "tv_download_row_navigation_incomplete",
                    f"{entry.get('orientation')} / downloads: D-pad did not prove all action columns on the second active row",
                    page="downloads",
                )
            )
        normalized.append(item)
    return normalized, findings
'''
    source = replace_once(
        source,
        "\ndef rail_logo_measurement(\n",
        action_analyzer + "\n\ndef rail_logo_measurement(\n",
        "download action analyzer",
    )
    source = replace_once(
        source,
        '''    rail_visual, rail_visual_findings = analyze_rail_visual(
        root,
        device,
        manifest.get("focus", []),
    )
    findings.extend(rail_visual_findings)
''',
        '''    download_actions, download_action_findings = analyze_download_actions(
        root,
        device,
        manifest.get("download_actions", []),
    )
    findings.extend(download_action_findings)
    expected_download_actions = len(orientations) if device["is_tv"] else 0
    if len(download_actions) != expected_download_actions:
        findings.append(
            finding(
                "infrastructure",
                "incomplete_download_action_audit",
                f"captured {len(download_actions)} download action audit(s); expected {expected_download_actions}",
                page="downloads",
            )
        )

    rail_visual, rail_visual_findings = analyze_rail_visual(
        root,
        device,
        manifest.get("focus", []),
    )
    findings.extend(rail_visual_findings)
''',
        "analyze run action integration",
    )
    source = replace_once(
        source,
        '        "focus": focus,\n        "rail_visual": rail_visual,\n',
        '        "focus": focus,\n        "download_actions": download_actions,\n        "rail_visual": rail_visual,\n',
        "summary download actions",
    )
    report_anchor = '''        for entry in summary["focus"]:
            lines.append(
                f"| {entry.get('page')} | **{entry.get('status')}** | "
                f"{entry.get('observed_focus_steps', 0)} | "
                f"{entry.get('unique_focus_targets', 0)} |"
            )

        lines += [
            "",
            "## Navigation rail logo",
'''
    report_replacement = '''        for entry in summary["focus"]:
            lines.append(
                f"| {entry.get('page')} | **{entry.get('status')}** | "
                f"{entry.get('observed_focus_steps', 0)} | "
                f"{entry.get('unique_focus_targets', 0)} |"
            )

        lines += [
            "",
            "## Download D-pad actions",
            "",
            "| Orientation | Status | Passed checks | Total checks |",
            "|---|---|---:|---:|",
        ]
        for entry in summary.get("download_actions", []):
            checks = entry.get("checks", [])
            passed = sum(1 for check in checks if check.get("success"))
            lines.append(
                f"| {entry.get('orientation')} | **{entry.get('status')}** | "
                f"{passed} | {len(checks)} |"
            )

        lines += [
            "",
            "## Navigation rail logo",
'''
    source = replace_once(source, report_anchor, report_replacement, "markdown action report")
    required = (
        "def analyze_download_actions",
        '"focus_coverage_incomplete"',
        '"tv_download_action_not_executed"',
        '"tv_download_row_navigation_incomplete"',
        '"download_actions": download_actions',
    )
    missing = [item for item in required if item not in source]
    if missing:
        raise SystemExit(f"analyzer missing action contracts: {missing}")
    path.write_text(source, encoding="utf-8")


def patch_tests() -> None:
    path = ROOT / "qa/compatibility/tests/test_lab.py"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "    download_layout_measurement,\n    live_action_measurement,\n",
        "    analyze_download_actions,\n    download_layout_measurement,\n    live_action_measurement,\n",
        "test analyzer import",
    )
    source = replace_once(
        source,
        "visible_package_names = RUN_LAB_MODULE.visible_package_names\n",
        "visible_package_names = RUN_LAB_MODULE.visible_package_names\n"
        "download_action_markers = RUN_LAB_MODULE.download_action_markers\n",
        "runner marker test import",
    )
    tests = r'''
    def test_download_fixture_uses_real_repository_actions_and_slow_active_transfer(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        for contract in (
            "repository.pause(item.downloadId)",
            "repository.resume(item.downloadId)",
            "repository.cyclePriority(item.downloadId)",
            "repository.setConcurrentDownloads(next)",
            "repository.remove(item.downloadId)",
            'private const val QA_DOWNLOAD_WRITE_DELAY_MS = 100L',
        ):
            self.assertIn(contract, source)
        self.assertNotIn("onRetryDownload = {},", source)
        self.assertNotIn("onCycleConcurrentDownloads = {},", source)
        self.assertNotIn("onCycleDownloadPriority = {},", source)

    def test_download_action_markers_are_versioned_and_parseable(self) -> None:
        xml = (
            '<hierarchy><node package="sa.hulksa.player.dev" '
            'content-desc="qa-page:downloads,qa-download-action:pause:1,'
            'qa-download-action:priority:2" bounds="[0,0][10,10]" /></hierarchy>'
        ).encode()
        self.assertEqual({("pause", 1), ("priority", 2)}, download_action_markers(xml))

'''
    insert_before = "\n\nclass AnalyzerTests(unittest.TestCase):"
    source = replace_once(source, insert_before, "\n" + tests + insert_before, "runner action tests")
    analyzer_test = r'''
    def test_download_action_audit_classifies_unreachable_and_unexecuted_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "focus/landscape/downloads-actions/01-step"
            evidence.mkdir(parents=True)
            for name in ("screenshot.png", "ui.xml", "logcat.txt"):
                (evidence / name).write_bytes(b"x")
            normalized, findings = analyze_download_actions(
                root,
                {"is_tv": True},
                [
                    {
                        "orientation": "landscape",
                        "checks": [
                            {
                                "id": "row-1-primary",
                                "success": False,
                                "expected_action": None,
                                "reason": "expected focused control was not reached",
                                "evidence": {
                                    "screenshot": "focus/landscape/downloads-actions/01-step/screenshot.png",
                                    "xml": "focus/landscape/downloads-actions/01-step/ui.xml",
                                    "logcat": "focus/landscape/downloads-actions/01-step/logcat.txt",
                                },
                            },
                            {
                                "id": "row-1-pause",
                                "success": False,
                                "expected_action": "pause",
                                "reason": "expected action marker was not emitted",
                                "evidence": {},
                            },
                        ],
                    }
                ],
            )
        self.assertEqual("BLOCKED", normalized[0]["status"])
        codes = {item["code"] for item in findings}
        self.assertIn("download_action_audit_incomplete", codes)
        self.assertIn("tv_download_action_unreachable", codes)
        self.assertIn("tv_download_action_not_executed", codes)
        self.assertIn("tv_download_row_navigation_incomplete", codes)

'''
    analyzer_anchor = "    def tv_gutter_xml(self, content_bounds: str, page: str = \"live\") -> ET.Element:\n"
    source = replace_once(source, analyzer_anchor, analyzer_test + analyzer_anchor, "action analyzer unit test")
    path.write_text(source, encoding="utf-8")


def patch_readme() -> None:
    path = ROOT / "qa/compatibility/README.md"
    source = path.read_text(encoding="utf-8")
    addition = """

## Full TV focus and download-action contract

The TV audit no longer treats two unique focus targets as sufficient. Every page has a minimum focus-coverage policy, and Downloads additionally runs a deterministic physical D-pad action audit. The audit proves that Wi-Fi mode, scheduling, concurrency, pause/resume, priority, cancel and movement across two active rows are both reachable and executable. Debug fixture callbacks call the real `DownloadRepository`; they are never no-ops. Missing evidence is infrastructure `BLOCKED`, while unreachable controls or missing action markers are critical product findings.
"""
    if "## Full TV focus and download-action contract" not in source:
        source = source.rstrip() + addition + "\n"
    path.write_text(source, encoding="utf-8")


def main() -> None:
    patch_activity()
    patch_runner()
    patch_analyzer()
    patch_tests()
    patch_readme()


if __name__ == "__main__":
    main()
