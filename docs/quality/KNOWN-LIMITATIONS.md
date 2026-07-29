# Known limitations

As of this branch, the following are not release-qualified:

| Capability | State | Reason / closure action |
|---|---|---|
| Physical Galaxy ARM64 runtime | NOT EXECUTED | run physical-device workflow/runbook |
| Xiaomi OEM firmware | PARTIAL HUMAN EVIDENCE | prior photos/login exist, but no structured logcat/artifact for this build |
| TCL OEM firmware | PARTIAL HUMAN EVIDENCE | prior photos only; no structured artifact for this build |
| ARM native runtime | NOT EXECUTED | APK ABI presence is not execution proof |
| Production authentication | NOT EXECUTED for Quality Lab branch | protected credentials unavailable to PR jobs |
| Real production playback | NOT EXECUTED | protected smoke artifact required |
| Real production download | NOT EXECUTED for this build | loopback fixture is not production |
| Download reboot/process death | NOT EXECUTED | managed reboot-capable device required |
| Media3 HLS/live/subtitle matrix | NOT EXECUTED | legal deterministic media fixture not wired |
| TalkBack traversal | NEEDS HUMAN REVIEW | XML semantics audit is not TalkBack |
| Contrast | NEEDS HUMAN REVIEW | no approved rendered-color scanner |
| Macrobenchmark | NOT EXECUTED | no approved physical performance runner |
| Baseline Profile packaging | NOT VERIFIED | no baseline-profile module exists |
| Vulnerability scan | NOT EXECUTED | dependency tree retained; no approved scanner configured |
| Expanded 15-profile matrix | DEFINED, NOT FULLY EXECUTED | run upgraded nightly and inspect all artifacts |
| Visual approved baseline | BLOCKED | pre-upgrade run is not acceptable due missing TV evidence and known false negatives |

These states must not be rewritten as PASS in reports or release notes without an artifact.

