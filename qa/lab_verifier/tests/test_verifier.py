from __future__ import annotations
from copy import deepcopy
import json
import tempfile
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from verifier import Outcome, artifact_checksum, canonical_bytes, gate, load_corpus, parse_markers, retry_allowed, validate_matrix, verify_bundle


class VerifierTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundles = load_corpus(ROOT / "corpus")


    def test_corpus_loader_accepts_file_and_rejects_empty_directory(self):
        corpus_file = ROOT / "corpus" / "golden-corpus.json"
        self.assertEqual(len(load_corpus(corpus_file)), 12)
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "no JSON cases"):
                load_corpus(Path(temp))

    def test_corpus_expected_outcomes(self):
        self.assertEqual(12, len(self.bundles))
        for bundle in self.bundles:
            with self.subTest(bundle["case_id"]):
                report = verify_bundle(bundle)
                self.assertEqual(bundle["expected"]["outcome"], report["outcome"])

    def test_replay_is_byte_for_byte_deterministic(self):
        outputs = [canonical_bytes([verify_bundle(bundle) for bundle in self.bundles]) for _ in range(8)]
        self.assertEqual(1, len(set(outputs)))

    def test_missing_evidence_is_blocked_not_pass(self):
        bundle = deepcopy(self.bundles[0]); del bundle["files"]["activity.txt"]
        self.assertEqual("BLOCKED", verify_bundle(bundle)["outcome"])

    def test_precondition_failure_has_one_root_and_downstream(self):
        bundle = next(b for b in self.bundles if b["case_id"] == "known-downstream-only")
        report = verify_bundle(bundle)
        self.assertEqual(1, report["root_count"])
        self.assertEqual(1, report["downstream_count"])

    def test_report_only_ignores_product_but_not_lab_invalidity(self):
        product = verify_bundle(next(b for b in self.bundles if b["case_id"] == "known-product-failure"))
        lab = verify_bundle(next(b for b in self.bundles if b["case_id"] == "known-lab-failure-stale-token"))
        self.assertTrue(gate([product], False)["passed"])
        self.assertFalse(gate([lab], False)["passed"])
        self.assertFalse(gate([product], True)["passed"])

    def test_retry_policy_only_allows_transient_infrastructure(self):
        for code in ("ADB_DISCONNECTED", "EMULATOR_BOOT_FAILURE", "SYSTEM_SERVICE_UNAVAILABLE", "ARTIFACT_DOWNLOAD_FAILURE"):
            self.assertTrue(retry_allowed(code))
        for code in ("CALLBACK_NOT_EXECUTED", "START_STATE_NOT_ESTABLISHED", "NAVIGATION_TARGET_MISMATCH", "ORIGIN_REPOSITORY_BOUNDARY_MISMATCH"):
            self.assertFalse(retry_allowed(code))

    def test_marker_parser_rejects_malformed_data(self):
        with self.assertRaises(ValueError):
            parse_markers("not-a-valid-marker")

    def test_checksum_detects_mutation(self):
        bundle = deepcopy(self.bundles[0])
        bundle["files"]["logcat.txt"] += "mutation\n"
        self.assertEqual("FAIL_LAB", verify_bundle(bundle)["outcome"])

    def test_fault_injection_table(self):
        base = deepcopy(self.bundles[0])
        mutations = {
            "delete_xml": (lambda b: b["files"].pop("ui.xml"), "BLOCKED"),
            "old_launch": (lambda b: b["files"].__setitem__("ui.xml", b["files"]["ui.xml"].replace("launch-001", "launch-old")), "FAIL_LAB"),
            "wrong_apk": (lambda b: b["expected"].__setitem__("lab_apk_sha256", "0"*64), "FAIL_LAB"),
            "old_process_marker": (lambda b: b["files"].__setitem__("markers.log", "999 launch-001 pause 1\n"), "FAIL_LAB"),
            "stop_loopback": (lambda b: b["files"].__setitem__("origin.log", "fixture_server=stopped\nbytes_served=0\n"), "FAIL_FIXTURE"),
            "origin_only": (lambda b: (b["files"].__setitem__("origin.log", "fixture_server=running\nbytes_served=64\n"), b["files"].__setitem__("repository.log", "bytes_persisted=0\n")), "FAIL_PRODUCT"),
            "repository_only": (lambda b: (b["files"].__setitem__("origin.log", "fixture_server=running\nbytes_served=0\n"), b["files"].__setitem__("repository.log", "bytes_persisted=64\n")), "FAIL_FIXTURE"),
            "wrong_size": (lambda b: b["files"].__setitem__("screenshot.json", json.dumps({"width":1280,"height":720,"density":320})), "FAIL_LAB"),
            "focus_race": (lambda b: b["files"].__setitem__("focus-events.log", "focused=row-1-pause\nfocused=row-1-cancel\n"), "FAIL_LAB"),
            "target_absent": (lambda b: b["files"].__setitem__("focus-events.log", "focused=toolbar-wifi\nfocused=toolbar-wifi\n"), "FAIL_LAB"),
            "wrong_action": (lambda b: b["files"].__setitem__("markers.log", "4242 launch-001 cancel 1\n"), "FAIL_LAB"),
            "adb_cut": (lambda b: b["files"].__setitem__("logcat.txt", "device offline\n"), "FAIL_INFRASTRUCTURE"),
        }
        for name,(mutate, expected) in mutations.items():
            with self.subTest(name):
                bundle=deepcopy(base); mutate(bundle); bundle["artifact_checksum"]=artifact_checksum(bundle["files"])
                self.assertEqual(expected, verify_bundle(bundle)["outcome"])

    def test_matrix_contracts(self):
        ids = ["pixel-4a-api29","pixel-6-api31","pixel-8-pro-api35","galaxy-s24-ultra-api35","pixel-tablet-api35","nexus-9-api28"]
        devices=[{"id":x} for x in ids]
        devices += [
            {"id":"android-tv-720p-api36","physical_width":1280,"physical_height":720,"density":213},
            {"id":"android-tv-1080p-api36","physical_width":1920,"physical_height":1080,"density":320},
            {"id":"android-tv-4k-api36","physical_width":3840,"physical_height":2160,"density":640},
        ]
        self.assertEqual([], validate_matrix(devices))
        devices[-1]["physical_width"] = 1920
        self.assertEqual("DEVICE_CONTRACT_MISMATCH", validate_matrix(devices)[0].code)


if __name__ == "__main__":
    unittest.main()
