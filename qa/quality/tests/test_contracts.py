from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from qa.quality.config.schema_validator import (
    SchemaError,
    validate,
    validate_file,
    validate_matrix_contract,
)
from qa.quality.core.models import Evidence, Finding, stable_fingerprint
from qa.quality.release.logo_integrity import load_manifest, verify


ROOT = Path(__file__).resolve().parents[3]


class SchemaContractTest(unittest.TestCase):
    def test_matrix_matches_schema_and_cross_field_contract(self) -> None:
        data = validate_file(
            ROOT / "qa/quality/config/matrix.json",
            ROOT / "qa/quality/schemas/matrix.schema.json",
        )
        validate_matrix_contract(data)
        self.assertEqual(data["minimum_api"], 23)
        self.assertTrue(any(item["label"] == "Xiaomi-density simulation" for item in data["profiles"]))

    def test_unknown_matrix_property_fails_closed(self) -> None:
        schema = {
            "type": "object",
            "additionalProperties": False,
            "properties": {"known": {"type": "string"}},
        }
        with self.assertRaises(SchemaError):
            validate({"known": "ok", "silent_bypass": True}, schema)

    def test_malformed_report_contract_is_rejected(self) -> None:
        finding_schema = json.loads(
            (ROOT / "qa/quality/schemas/finding.schema.json").read_text(encoding="utf-8")
        )
        with self.assertRaises(SchemaError):
            validate({"severity": "P1"}, finding_schema)


class FindingContractTest(unittest.TestCase):
    def test_fingerprint_is_stable_and_sensitive_to_actual_result(self) -> None:
        first = stable_fingerprint("bounds", "tv", "home", "inside", "outside")
        second = stable_fingerprint("BOUNDS", "TV", "HOME", "inside", "outside")
        changed = stable_fingerprint("bounds", "tv", "home", "inside", "clipped")
        self.assertEqual(first, second)
        self.assertNotEqual(first, changed)

    def test_arabic_finding_round_trips_without_ascii_escaping(self) -> None:
        finding = Finding(
            code="download_stalled",
            severity="P1",
            finding_type="Product",
            message="التنزيل لا ينقل أي بايت",
            expected="bytes > 0",
            actual="bytes = 0",
            device="fixture",
            api=35,
            orientation="landscape",
            density=320,
            font_scale=1.0,
            screen="downloads",
            journey="download-enqueue",
            build_sha="1234567890abcdef",
            reproduction=("enqueue", "observe"),
            suggested_owner="downloads",
            evidence=Evidence(logcat="logcat.txt"),
        )
        encoded = json.dumps(finding.to_dict(), ensure_ascii=False)
        self.assertIn("التنزيل", encoded)
        self.assertEqual(json.loads(encoded)["fingerprint"], finding.fingerprint)


class LogoIntegrityTest(unittest.TestCase):
    def test_approved_logo_manifest_matches_repository(self) -> None:
        manifest = ROOT / "qa/quality/release/approved-logo-assets.sha256"
        self.assertGreaterEqual(len(load_manifest(manifest)), 9)
        self.assertEqual(verify(ROOT, manifest), [])

    def test_changed_logo_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "logo.bin").write_bytes(b"changed")
            manifest = root / "hashes.txt"
            manifest.write_text(f"{'0' * 64}  logo.bin\n", encoding="utf-8")
            self.assertEqual(len(verify(root, manifest)), 1)


if __name__ == "__main__":
    unittest.main()
