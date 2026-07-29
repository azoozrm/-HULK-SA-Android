from __future__ import annotations

from dataclasses import asdict, dataclass, field
import hashlib
import json
from typing import Any


SEVERITIES = ("P0", "P1", "P2", "P3")
FINDING_TYPES = (
    "Product",
    "Infrastructure",
    "Test harness",
    "Flaky",
    "False positive",
    "Needs human review",
)
RECOMMENDATIONS = ("PASS", "PASS WITH WARNINGS", "FAIL", "BLOCKED", "NOT VERIFIED")


def stable_fingerprint(*parts: object) -> str:
    normalized = "\x1f".join(str(part).strip().lower() for part in parts)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:24]


@dataclass(frozen=True)
class Evidence:
    screenshot: str | None = None
    xml: str | None = None
    logcat: str | None = None
    trace: str | None = None
    extra: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class Finding:
    code: str
    severity: str
    finding_type: str
    message: str
    expected: str
    actual: str
    device: str
    api: int | None
    orientation: str
    density: int | None
    font_scale: float | None
    screen: str
    journey: str
    build_sha: str
    reproduction: tuple[str, ...]
    suggested_owner: str
    regression_test_id: str | None = None
    root_cause: str | None = None
    evidence: Evidence = field(default_factory=Evidence)
    fingerprint: str = ""

    def __post_init__(self) -> None:
        if self.severity not in SEVERITIES:
            raise ValueError(f"invalid severity: {self.severity}")
        if self.finding_type not in FINDING_TYPES:
            raise ValueError(f"invalid finding type: {self.finding_type}")
        if not self.fingerprint:
            object.__setattr__(
                self,
                "fingerprint",
                stable_fingerprint(
                    self.code,
                    self.device,
                    self.screen,
                    self.journey,
                    self.expected,
                    self.actual,
                ),
            )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def canonical_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

