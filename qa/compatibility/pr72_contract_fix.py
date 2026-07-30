#!/usr/bin/env python3
from pathlib import Path

runner_path = Path('qa/compatibility/run-lab.py')
tests_path = Path('qa/compatibility/tests/test_lab.py')

runner = runner_path.read_text(encoding='utf-8')
old_pause = '''            restart("pause-action")
            inspect("pause-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-pause-executes", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="pause")
'''
new_pause = '''            restart("pause-action")
            inspect("pause-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-1-pause", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="pause")

            restart("row-2-pause-action")
            inspect("row-2-pause-row-1-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-2-pause-row-2-primary", key_code=20, expected_labels=("ايقاف مؤقت", "استئناف"))
            inspect("row-2-pause", key_code=23, expected_labels=("ايقاف مؤقت", "استئناف"), expected_action="pause")
'''
if runner.count(old_pause) != 1:
    raise SystemExit('expected exactly one pause-action block')
runner = runner.replace(old_pause, new_pause)
runner_path.write_text(runner, encoding='utf-8')

tests = tests_path.read_text(encoding='utf-8')
class_marker = '\nclass AnalyzerTests(unittest.TestCase):\n'
contract_test = '''
    def test_download_action_runner_matches_analyzer_required_ids(self) -> None:
        source = (LAB_ROOT / "run-lab.py").read_text(encoding="utf-8")
        required_ids = (
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
        )
        for check_id in required_ids:
            self.assertIn(f'inspect("{check_id}"', source)
        self.assertNotIn('inspect("row-1-pause-executes"', source)

'''
if tests.count(class_marker) != 1:
    raise SystemExit('expected exactly one AnalyzerTests class marker')
if 'test_download_action_runner_matches_analyzer_required_ids' in tests:
    raise SystemExit('contract test already present')
tests = tests.replace(class_marker, contract_test + class_marker)
tests_path.write_text(tests, encoding='utf-8')

print('PASS: PR72 runner/analyzer action IDs aligned')
