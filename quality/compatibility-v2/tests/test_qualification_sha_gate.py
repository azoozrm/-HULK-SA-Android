from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "qualification_sha_gate.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_qualification_sha", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

TARGET = "1" * 40
OTHER = "2" * 40
REPO = "azoozrm/-HULK-SA-Android"
BRANCH = MODULE.OFFICIAL_BRANCH


class QualificationShaGateTest(unittest.TestCase):
    def run_payload(self, run_id: int, workflow: str, *, sha: str = TARGET, branch: str = BRANCH) -> dict:
        return {
            "id": run_id,
            "repository": {"full_name": REPO},
            "head_branch": branch,
            "head_sha": sha,
            "path": workflow,
            "status": "completed",
            "conclusion": "success",
        }

    def artifact(self, name: str, run_id: int, *, sha: str = TARGET, expired: bool = False) -> dict:
        return {
            "name": name,
            "expired": expired,
            "workflow_run": {"id": run_id, "head_sha": sha},
        }

    def valid_contract(self, root: Path) -> dict:
        matrix = root / "device-matrix.json"
        matrix.write_text(json.dumps({"profiles": [{"id": "phone"}, {"id": "tv-4k"}]}), encoding="utf-8")
        canonical_id, full_id, production_id = 10, 20, 30
        return {
            "expected_repo": REPO,
            "expected_branch": BRANCH,
            "target_sha": TARGET,
            "checkout_sha": TARGET,
            "execution_sha": TARGET,
            "execution_ref": f"refs/heads/{BRANCH}",
            "official_head_start": TARGET,
            "official_head_end": TARGET,
            "canonical_run": self.run_payload(canonical_id, MODULE.EXPECTED_WORKFLOWS["canonical"]),
            "canonical_artifacts": {
                "artifacts": [self.artifact(f"HULK-SA-BUILD-EVIDENCE-{canonical_id}", canonical_id)]
            },
            "full_run": self.run_payload(full_id, MODULE.EXPECTED_WORKFLOWS["full"]),
            "full_artifacts": {
                "artifacts": [
                    self.artifact(f"HULK-SA-COMPATIBILITY-V2-BINARIES-{full_id}", full_id),
                    self.artifact(f"HULK-SA-COMPATIBILITY-V2-RUNTIME-phone-{full_id}", full_id),
                    self.artifact(f"HULK-SA-COMPATIBILITY-V2-RUNTIME-tv-4k-{full_id}", full_id),
                ]
            },
            "production_run": self.run_payload(production_id, MODULE.EXPECTED_WORKFLOWS["production"]),
            "production_artifacts": {
                "artifacts": [
                    self.artifact(f"HULK-SA-PRODUCTION-AUTHENTICATED-SMOKE-{production_id}", production_id)
                ]
            },
            "device_matrix": matrix,
        }

    def test_exact_official_sha_and_same_sha_evidence_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            MODULE.verify_exact_qualification(**self.valid_contract(Path(temp)))

    def test_signed_workflow_execution_sha_must_equal_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["execution_sha"] = OTHER
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_signed_workflow_execution_ref_must_be_official_branch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["execution_ref"] = "refs/heads/feature/modified-qualification"
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_stale_evidence_sha_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["full_run"]["head_sha"] = OTHER
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_non_official_evidence_ref_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["production_run"]["head_branch"] = "feature/not-official"
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_official_head_change_during_qualification_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["official_head_end"] = OTHER
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_missing_or_expired_full_matrix_evidence_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["full_artifacts"]["artifacts"][1]["expired"] = True
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_artifact_without_run_provenance_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            del contract["canonical_artifacts"]["artifacts"][0]["workflow_run"]
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_wrong_workflow_cannot_impersonate_required_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            contract = self.valid_contract(Path(temp))
            contract["canonical_run"]["path"] = ".github/workflows/other.yml"
            with self.assertRaises(MODULE.QualificationError):
                MODULE.verify_exact_qualification(**contract)

    def test_signed_metadata_keeps_physical_qualification_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp) / "SIGNED-QUALIFICATION.json"
            MODULE.write_signed_metadata(
                out=out,
                source_sha=TARGET,
                canonical_run_id="10",
                full_run_id="20",
                production_run_id="30",
            )
            payload = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(TARGET, payload["source_sha"])
            self.assertEqual("BLOCKED", payload["evidence"]["physical_devices"]["status"])
            self.assertEqual("BLOCKED", payload["release_readiness_status"])

    def test_current_head_must_still_equal_signed_source(self) -> None:
        with self.assertRaises(MODULE.QualificationError):
            MODULE.verify_current_head(target_sha=TARGET, checkout_sha=TARGET, official_head=OTHER)


if __name__ == "__main__":
    unittest.main()
