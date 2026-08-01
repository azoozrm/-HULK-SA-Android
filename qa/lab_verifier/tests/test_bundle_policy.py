from __future__ import annotations

import json
from pathlib import Path
import unittest

from qa.lab_verifier import verifier

ROOT = Path(__file__).resolve().parents[2]


def known_pass() -> dict:
    payload = json.loads(
        (ROOT / "lab_verifier/corpus/golden-corpus.json").read_text(encoding="utf-8")
    )
    bundle = next(
        item for item in verifier._expand_compact_corpus(payload)
        if item["case_id"] == "known-pass"
    )
    return json.loads(json.dumps(bundle))


class BundlePolicyTests(unittest.TestCase):
    def test_permitted_external_system_retry_remains_pass(self) -> None:
        bundle = known_pass()
        bundle["files"]["retry-evidence.json"] = json.dumps({
            "attempts": 2,
            "retried": True,
            "first_status": 75,
            "second_status": 0,
            "retry_reason": "SYSTEM_SERVICE_UNAVAILABLE",
            "retry_allowed": True,
            "final_success": True,
            "final_attempt": "attempt-2",
        }, sort_keys=True)
        bundle["files"]["retry-failure-classification.json"] = json.dumps({
            "classification": "infrastructure",
            "code": "SYSTEM_SERVICE_UNAVAILABLE",
            "retry_allowed": True,
            "title": "Pixel Launcher isn't responding",
            "dismiss_center": [400, 360],
        }, sort_keys=True)
        bundle["artifact_checksum"] = verifier.artifact_checksum(bundle["files"])
        self.assertEqual("PASS", verifier.verify_bundle(bundle)["outcome"])

    def test_assertion_retry_is_rejected(self) -> None:
        bundle = known_pass()
        bundle["files"]["retry-evidence.json"] = json.dumps({
            "attempts": 2,
            "retried": True,
            "first_status": 75,
            "second_status": 0,
            "retry_reason": "ACTION_CALLBACK_NOT_EXECUTED",
            "retry_allowed": True,
            "final_success": True,
            "final_attempt": "attempt-2",
        }, sort_keys=True)
        bundle["files"]["retry-failure-classification.json"] = json.dumps({
            "classification": "product",
            "code": "ACTION_CALLBACK_NOT_EXECUTED",
            "retry_allowed": True,
        }, sort_keys=True)
        bundle["artifact_checksum"] = verifier.artifact_checksum(bundle["files"])
        report = verifier.verify_bundle(bundle)
        self.assertEqual("FAIL_LAB", report["outcome"])
        self.assertEqual({"RETRY_POLICY_VIOLATION"}, {item["code"] for item in report["findings"]})

    def test_missing_retry_root_is_rejected(self) -> None:
        bundle = known_pass()
        bundle["files"]["retry-evidence.json"] = json.dumps({
            "attempts": 2,
            "retried": True,
            "first_status": 75,
            "second_status": 0,
            "retry_reason": "SYSTEM_SERVICE_UNAVAILABLE",
            "retry_allowed": True,
            "final_success": True,
            "final_attempt": "attempt-2",
        }, sort_keys=True)
        bundle["artifact_checksum"] = verifier.artifact_checksum(bundle["files"])
        report = verifier.verify_bundle(bundle)
        self.assertEqual("FAIL_LAB", report["outcome"])
        self.assertEqual({"RETRY_ROOT_EVIDENCE_MISSING"}, {item["code"] for item in report["findings"]})

    def test_missing_device_contract_is_rejected(self) -> None:
        bundle = known_pass()
        del bundle["files"]["device-contract.json"]
        bundle["artifact_checksum"] = verifier.artifact_checksum(bundle["files"])
        report = verifier.verify_bundle(bundle)
        self.assertEqual("FAIL_LAB", report["outcome"])
        self.assertEqual({"DEVICE_CONTRACT_EVIDENCE_MISSING"}, {item["code"] for item in report["findings"]})


if __name__ == "__main__":
    unittest.main()
