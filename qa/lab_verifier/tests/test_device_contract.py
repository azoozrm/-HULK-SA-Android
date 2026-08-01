from __future__ import annotations

import struct
import unittest

from qa.lab_verifier.device_contract import (
    PNG_HEADER,
    evaluate_contract,
    parse_wm_density,
    parse_wm_size,
)


def png(width: int, height: int) -> bytes:
    return PNG_HEADER + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", width, height) + b"\x00" * 20


class DeviceContractTests(unittest.TestCase):
    def test_prefers_override_as_effective_size(self) -> None:
        parsed = parse_wm_size(
            "Physical size: 3840x2160\nOverride size: 1920x1080\n"
        )
        self.assertEqual([3840, 2160], parsed["physical_size"])
        self.assertEqual([1920, 1080], parsed["effective_size"])

    def test_prefers_override_as_effective_density(self) -> None:
        parsed = parse_wm_density(
            "Physical density: 320\nOverride density: 640\n"
        )
        self.assertEqual(320, parsed["physical_density"])
        self.assertEqual(640, parsed["effective_density"])

    def test_valid_contract_requires_physical_effective_and_screenshot_agreement(self) -> None:
        report = evaluate_contract(
            "Physical size: 3840x2160\nOverride size: 3840x2160\n",
            "Physical density: 320\nOverride density: 640\n",
            png(3840, 2160),
            width=3840,
            height=2160,
            density=640,
        )
        self.assertTrue(report["valid"])
        self.assertEqual([], report["errors"])

    def test_4k_profile_with_1080_override_is_rejected(self) -> None:
        report = evaluate_contract(
            "Physical size: 3840x2160\nOverride size: 1920x1080\n",
            "Physical density: 320\nOverride density: 640\n",
            png(1920, 1080),
            width=3840,
            height=2160,
            density=640,
        )
        self.assertFalse(report["valid"])
        self.assertEqual(
            ["effective_size", "screenshot_size"],
            report["errors"],
        )

    def test_wrong_physical_skin_is_rejected_even_if_override_matches(self) -> None:
        report = evaluate_contract(
            "Physical size: 1920x1080\nOverride size: 3840x2160\n",
            "Physical density: 320\nOverride density: 640\n",
            png(3840, 2160),
            width=3840,
            height=2160,
            density=640,
        )
        self.assertFalse(report["valid"])
        self.assertEqual(["physical_size"], report["errors"])


if __name__ == "__main__":
    unittest.main()
