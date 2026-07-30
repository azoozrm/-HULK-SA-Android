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
    initialDestination: MainDestination,
    isTv: Boolean,
    downloadHarness: QaDownloadHarness?,
) {
    val downloadRepository = downloadHarness?.repository
    var favorites by remember {
        mutableStateOf(setOf("MOVIE:101", "SERIES:301", "LIVE:501"))
    }
    var state by remember(initialDestination) {
        mutableStateOf(fixtureState(initialDestination).copy(favorites = favorites))
    }
    var actionRevision by remember { mutableStateOf(0) }
    var lastDownloadAction by remember { mutableStateOf<String?>(null) }
    val navigationMemory = remember { NavigationMemoryStore() }
    val pageMarker = "qa-page:${state.destination.name.lowercase(Locale.ROOT)}"
    var originBytesServed by remember(downloadHarness) {
        mutableStateOf(downloadHarness?.origin?.bytesServed() ?: 0L)
    }
    val hasRealDownloadProgress =
        downloadRepository != null && state.downloads.any { it.bytesDownloaded > 0L }
    val hasOriginByteProgress = originBytesServed > 0L

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

    LaunchedEffect(downloadHarness) {
        while (downloadHarness != null) {
            refreshDownloads(downloadHarness.repository)
            originBytesServed = downloadHarness.origin.bytesServed()
            delay(QA_DOWNLOAD_POLL_MS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = false) {
                contentDescription = buildList {
                    add(pageMarker)
                    if (hasOriginByteProgress) {
                        add(QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER)
                    }
                    if (hasRealDownloadProgress) {
                        add(QA_DOWNLOAD_PROGRESS_MARKER)
                    }
                    lastDownloadAction?.let { marker ->
                        if (state.destination == MainDestination.DOWNLOADS) {
                            add("qa-download-action:$marker")
                        }
                    }
                }
                    .joinToString(",")
            },
    ) {
        MainShellScreen(
            state = state.copy(favorites = favorites),
            isTv = isTv,
            navigationMemory = navigationMemory,
            isFavorite = { "${it.type.name}:${it.id}" in favorites },
            onSelectDestination = { destination ->
                val next = fixtureState(destination).copy(favorites = favorites)
                state = if (destination == MainDestination.DOWNLOADS && downloadRepository != null) {
                    next.copy(
                        downloads = downloadRepository.downloads(),
                        downloadSettings = downloadRepository.settings(),
                    )
                } else {
                    next
                }
            },
            onSelectCategory = { state = state.copy(selectedCategoryId = it) },
            onSearch = { state = state.copy(searchQuery = it) },
            onOpen = {},
            onOpenHistory = {},
            onToggleFavorite = { item ->
                val key = "${item.type.name}:${item.id}"
                favorites = if (key in favorites) favorites - key else favorites + key
            },
            onRefresh = {},
            onClearHistory = { state = state.copy(history = emptyList()) },
            onPlayDownload = {},
            onDeleteDownload = { item ->
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloads = state.downloads.filterNot { it.downloadId == item.downloadId },
                    )
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
                val next =
                    if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NOW) {
                        DownloadScheduleMode.NIGHT
                    } else {
                        DownloadScheduleMode.NOW
                    }
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloadSettings = state.downloadSettings.copy(scheduleMode = next),
                    )
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
        "private fun String.toDestination",
        fixture_main,
        "FixtureMain",
    )
    source = replace_once(
        source,
        "private const val QA_DOWNLOAD_WRITE_DELAY_MS = 10L",
        "private const val QA_DOWNLOAD_WRITE_DELAY_MS = 40L",
        "fixture transfer delay",
    )
    for contract in (
        "repository.pause(item.downloadId)",
        "repository.resume(item.downloadId)",
        "repository.cyclePriority(item.downloadId)",
        "repository.setConcurrentDownloads(next)",
        "repository.remove(item.downloadId)",
        'add("qa-download-action:$marker")',
    ):
        if contract not in source:
            raise SystemExit(f"QaActivity missing contract: {contract}")
    path.write_text(source, encoding="utf-8")


def patch_runner() -> None:
    path = ROOT / "qa/compatibility/run-lab.py"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        'DOWNLOAD_PROGRESS_MARKER = "qa-download-transfer:bytes-positive"\n',
        'DOWNLOAD_PROGRESS_MARKER = "qa-download-transfer:bytes-positive"\n'
        'DOWNLOAD_ACTION_RE = re.compile(r"qa-download-action:([a-z]+):(\\d+)")\n',
        "download action regex",
    )
    source = replace_once(
        source,
        "\n\ndef focused_node(xml_bytes: bytes) -> dict[str, Any] | None:\n",
        '''\n\ndef download_action_markers(xml_bytes: bytes) -> set[tuple[str, int]]:\n    text = xml_bytes.decode("utf-8", errors="ignore")\n    return {\n        (match.group(1), int(match.group(2)))\n        for match in DOWNLOAD_ACTION_RE.finditer(text)\n    }\n\n\ndef focused_node(xml_bytes: bytes) -> dict[str, Any] | None:\n''',
        "download action marker helper",
    )
    source = replace_once(
        source,
        '            "focus": [],\n            "harness_errors": [],\n',
        '            "focus": [],\n            "download_actions": [],\n            "harness_errors": [],\n',
        "manifest action field",
    )
    method = r'''
    def download_action_audit(self, orientation: str) -> None:
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
            known_markers = download_action_markers(
                dump_xml(self.adb, case_dir / ".initial.xml", attempts=2)
            )
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
            if key_code is not None:
                self.adb.shell(["input", "keyevent", str(key_code)])
            deadline = time.monotonic() + (5.0 if expected_action else 1.5)
            xml = b""
            markers: set[tuple[str, int]] = set()
            focused: dict[str, Any] | None = None
            action_seen = expected_action is None
            while time.monotonic() < deadline:
                time.sleep(0.20)
                xml = dump_xml(self.adb, step_root / "ui.xml", attempts=2)
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
            label = str(
                (focused or {}).get("text")
                or (focused or {}).get("content_description")
                or ""
            )
            focus_seen = not expected_labels or any(expected in label for expected in expected_labels)
            success = bool(focused) and focus_seen and action_seen
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
                    "focused": focused,
                    "action_markers": [
                        f"{action}:{revision}" for action, revision in sorted(markers)
                    ],
                    "reason": None if success else (
                        "expected action marker was not emitted"
                        if not action_seen
                        else "expected focused control was not reached"
                        if not focus_seen
                        else "no focused control was exposed"
                    ),
                    "evidence": {
                        "screenshot": (step_root / "screenshot.png").relative_to(self.out).as_posix(),
                        "xml": (step_root / "ui.xml").relative_to(self.out).as_posix(),
                        "logcat": (step_root / "logcat.txt").relative_to(self.out).as_posix(),
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
    source = replace_once(source, "    def run(self) -> None:\n", method + "    def run(self) -> None:\n", "action audit method")
    source = replace_once(
        source,
        '''            if self.args.is_tv:
                for page in PAGES:
                    self.focus_audit(page["id"], orientation)
''',
        '''            if self.args.is_tv:
                for page in PAGES:
                    self.focus_audit(page["id"], orientation)
                self.download_action_audit(orientation)
''',
        "action audit invocation",
    )
    for contract in (
        "def download_action_audit",
        '"download_actions": []',
        'expected_action="pause"',
        'expected_action="priority"',
        'expected_action="cancel"',
    ):
        if contract not in source:
            raise SystemExit(f"run-lab missing contract: {contract}")
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
    source = replace_once(
        source,
        '''        elif len(signatures) < 2:
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
''',
        '''        else:
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
''',
        "focus coverage analyzer",
    )
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
            if item["status"] != "BLOCKED":
                item["status"] = "FAIL"
            code = (
                "tv_download_action_not_executed"
                if check.get("expected_action")
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
        successful_ids = {check.get("id") for check in checks if check.get("success")}
        if not row_two_ids.issubset(successful_ids):
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
    source = replace_once(source, "\ndef rail_logo_measurement(\n", action_analyzer + "\n\ndef rail_logo_measurement(\n", "action analyzer")
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
        "analyze run actions",
    )
    source = replace_once(
        source,
        '        "focus": focus,\n        "rail_visual": rail_visual,\n',
        '        "focus": focus,\n        "download_actions": download_actions,\n        "rail_visual": rail_visual,\n',
        "summary actions",
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
    source = replace_once(source, report_anchor, report_replacement, "markdown actions")
    for contract in (
        "def analyze_download_actions",
        '"focus_coverage_incomplete"',
        '"tv_download_action_not_executed"',
        '"tv_download_row_navigation_incomplete"',
        '"download_actions": download_actions',
    ):
        if contract not in source:
            raise SystemExit(f"analyzer missing contract: {contract}")
    path.write_text(source, encoding="utf-8")


def patch_tests() -> None:
    path = ROOT / "qa/compatibility/tests/test_lab.py"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "    analyze_run,\n    download_layout_measurement,\n",
        "    analyze_run,\n    analyze_download_actions,\n    download_layout_measurement,\n",
        "analyzer test import",
    )
    source = replace_once(
        source,
        "visible_package_names = RUN_LAB_MODULE.visible_package_names\n",
        "visible_package_names = RUN_LAB_MODULE.visible_package_names\n"
        "download_action_markers = RUN_LAB_MODULE.download_action_markers\n",
        "runner test import",
    )
    config_tests = r'''
    def test_download_fixture_uses_real_repository_actions_and_slow_active_transfer(self) -> None:
        source = (LAB_ROOT / "QaActivity.kt").read_text(encoding="utf-8")
        for contract in (
            "repository.pause(item.downloadId)",
            "repository.resume(item.downloadId)",
            "repository.cyclePriority(item.downloadId)",
            "repository.setConcurrentDownloads(next)",
            "repository.remove(item.downloadId)",
            'private const val QA_DOWNLOAD_WRITE_DELAY_MS = 40L',
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
    source = replace_once(source, "\n\nclass AnalyzerTests(unittest.TestCase):", "\n" + config_tests + "\n\nclass AnalyzerTests(unittest.TestCase):", "config action tests")
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
    source = replace_once(
        source,
        "    def tv_gutter_xml(self, content_bounds: str, page: str = \"live\") -> ET.Element:\n",
        analyzer_test + "    def tv_gutter_xml(self, content_bounds: str, page: str = \"live\") -> ET.Element:\n",
        "analyzer action test",
    )
    path.write_text(source, encoding="utf-8")


def patch_readme() -> None:
    path = ROOT / "qa/compatibility/README.md"
    source = path.read_text(encoding="utf-8")
    heading = "## Full TV focus and download-action contract"
    if heading not in source:
        source = source.rstrip() + """

## Full TV focus and download-action contract

The TV audit no longer treats two unique focus targets as sufficient. Every page has a minimum focus-coverage policy, and Downloads additionally runs a deterministic physical D-pad action audit. The audit proves that Wi-Fi mode, scheduling, concurrency, pause/resume, priority, cancel and movement across two active rows are both reachable and executable. Debug fixture callbacks call the real `DownloadRepository`; they are never no-ops. Missing evidence is infrastructure `BLOCKED`, while unreachable controls or missing action markers are critical product findings.
""" + "\n"
    path.write_text(source, encoding="utf-8")


def main() -> None:
    patch_activity()
    patch_runner()
    patch_analyzer()
    patch_tests()
    patch_readme()


if __name__ == "__main__":
    main()
