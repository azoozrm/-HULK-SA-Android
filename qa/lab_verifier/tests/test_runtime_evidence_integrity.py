from __future__ import annotations

import json
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

from qa.lab_verifier import verifier
from qa.lab_verifier.write_sha256_manifest import verify_manifest, write_manifest


class RuntimeEvidenceIntegrityTests(unittest.TestCase):
    def test_focus_trace_cannot_override_conflicting_ui_xml(self) -> None:
        payload = json.loads(
            (ROOT / "lab_verifier/corpus/golden-corpus.json").read_text(
                encoding="utf-8"
            )
        )
        bundles = verifier._expand_compact_corpus(payload)
        bundle = next(
            item for item in bundles if item.get("case_id") == "known-pass"
        )
        bundle = json.loads(json.dumps(bundle))
        xml_root = ET.fromstring(bundle["files"]["ui.xml"])
        changed = False
        for node in xml_root.iter("node"):
            if node.attrib.get("focused") == "true":
                node.attrib["text"] = "wrong-target"
                node.attrib["content-desc"] = "wrong-target"
                changed = True
        self.assertTrue(changed)
        bundle["files"]["ui.xml"] = ET.tostring(
            xml_root,
            encoding="unicode",
        )
        bundle["artifact_checksum"] = verifier.artifact_checksum(bundle["files"])

        report = verifier.verify_bundle(bundle)

        self.assertEqual("FAIL_LAB", report["outcome"])
        self.assertEqual(
            {"FOCUS_EVIDENCE_MISMATCH"},
            {item["code"] for item in report["findings"]},
        )

    def test_checksum_manifest_survives_artifact_directory_relocation(self) -> None:
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            source = Path(first)
            destination = Path(second)
            (source / "a.txt").write_text("alpha\n", encoding="utf-8")
            (source / "b.bin").write_bytes(b"beta")
            manifest = write_manifest(source)
            shutil.copy2(source / "a.txt", destination / "a.txt")
            shutil.copy2(source / "b.bin", destination / "b.bin")
            relocated = destination / manifest.name
            shutil.copy2(manifest, relocated)

            verify_manifest(destination, relocated)
            names = [line.split("  ", 1)[1] for line in relocated.read_text().splitlines()]
            self.assertEqual(["a.txt", "b.bin"], names)
            self.assertTrue(all("/" not in name and "\\" not in name for name in names))

    def test_checksum_manifest_detects_mutated_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "evidence.txt").write_text("before\n", encoding="utf-8")
            manifest = write_manifest(root)
            (root / "evidence.txt").write_text("after\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                verify_manifest(root, manifest)


if __name__ == "__main__":
    unittest.main()
