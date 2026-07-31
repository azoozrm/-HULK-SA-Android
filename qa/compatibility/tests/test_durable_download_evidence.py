from __future__ import annotations

import html
import json
from pathlib import Path
import sys
import tempfile
import unittest

from PIL import Image, ImageDraw


LAB_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LAB_ROOT))

from analyze import add_case_findings, durable_download_evidence  # noqa: E402


class DurableDownloadEvidenceTests(unittest.TestCase):
    def _write_state(
        self,
        path: Path,
        *,
        downloaded: int,
        total: int = 67_108_864,
        status: str = "DOWNLOADING",
    ) -> str:
        filename = "QA_DOWNLOAD_3_fixture_9003.mp4"
        payload = [
            {
                "downloadId": 9003,
                "historyKey": "QA_DOWNLOAD:3",
                "title": "QA_DOWNLOAD_3 fixture",
                "sourceCandidates": ["http://127.0.0.1:43210/fixture-3.mp4"],
                "fileName": filename,
                "status": status,
                "bytesDownloaded": downloaded,
                "totalBytes": total,
                "integrityVerified": status == "COMPLETED",
            }
        ]
        path.write_text(
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
            "<map><string name=\"downloads\">"
            + html.escape(json.dumps(payload, separators=(",", ":")))
            + "</string></map>\n",
            encoding="utf-8",
        )
        return filename

    def test_correlates_loopback_repository_bytes_with_partial_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            state = root / "download-state.xml"
            listing = root / "download-files.txt"
            filename = self._write_state(state, downloaded=8_388_608)
            listing.write_text(
                "/sdcard/Movies:\n"
                f"-rw-rw---- 1 10086 1015 9437184 2026-07-31 17:01 {filename}.part\n",
                encoding="utf-8",
            )

            evidence = durable_download_evidence(state, listing)

        self.assertTrue(evidence["valid"])
        self.assertTrue(evidence["repository_progress"])
        self.assertTrue(evidence["origin_progress"])
        self.assertEqual(1, evidence["matching_file_count"])
        self.assertEqual(1, evidence["active_positive_count"])

    def test_api28_stale_tree_does_not_become_transport_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            case_root = root / "raw/portrait/font-100/downloads"
            case_root.mkdir(parents=True)
            image = Image.new("RGB", (1080, 1920), "#090a07")
            ImageDraw.Draw(image).rectangle((80, 120, 1000, 1800), fill="#80661f")
            image.save(case_root / "screenshot.png")
            (case_root / "ui.xml").write_text(
                '<hierarchy><node package="sa.hulksa.player.dev" '
                'content-desc="qa-page:search" bounds="[0,0][1080,1920]" />'
                "</hierarchy>",
                encoding="utf-8",
            )
            (case_root / "logcat.txt").write_text("", encoding="utf-8")
            (case_root / "activity.txt").write_text(
                "topResumedActivity=ActivityRecord{abc u0 "
                "sa.hulksa.player.dev/.qa.QaActivity t1}",
                encoding="utf-8",
            )
            filename = self._write_state(
                case_root / "download-state.xml",
                downloaded=12_582_912,
            )
            (case_root / "download-files.txt").write_text(
                "/sdcard/Movies:\n"
                f"-rw-rw---- 1 10086 1015 13107200 2026-07-31 17:01 {filename}.part\n",
                encoding="utf-8",
            )
            result, findings = add_case_findings(
                root,
                {
                    "api": 28,
                    "is_tv": False,
                    "requested_width": 1080,
                    "requested_height": 1920,
                    "requested_density": 420,
                },
                {
                    "id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "orientation": "portrait",
                    "font_scale": 1.0,
                    "marker": "qa-page:downloads",
                    "marker_found": False,
                    "files": {
                        "screenshot": "raw/portrait/font-100/downloads/screenshot.png",
                        "xml": "raw/portrait/font-100/downloads/ui.xml",
                        "logcat": "raw/portrait/font-100/downloads/logcat.txt",
                        "activity": "raw/portrait/font-100/downloads/activity.txt",
                        "download_state": "raw/portrait/font-100/downloads/download-state.xml",
                        "download_files": "raw/portrait/font-100/downloads/download-files.txt",
                    },
                },
            )

        codes = {item["code"] for item in findings}
        self.assertNotIn("page_marker_missing", codes)
        self.assertNotIn("download_transfer_no_byte_progress", codes)
        self.assertNotIn("download_transfer_evidence_mismatch", codes)
        self.assertIn("stale_accessibility_hierarchy", codes)
        self.assertTrue(result["download_evidence"]["valid"])
        self.assertEqual("WARN", result["status"])

    def test_zero_byte_state_remains_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            state = root / "download-state.xml"
            listing = root / "download-files.txt"
            self._write_state(state, downloaded=0)
            listing.write_text("/sdcard/Movies:\ntotal 0\n", encoding="utf-8")

            evidence = durable_download_evidence(state, listing)

        self.assertFalse(evidence["valid"])
        self.assertFalse(evidence["repository_progress"])
        self.assertFalse(evidence["origin_progress"])


if __name__ == "__main__":
    unittest.main()
