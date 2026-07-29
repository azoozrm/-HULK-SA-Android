# Compatibility Lab Baseline Before Upgrade

## Identity

- Official product head: `55ee9a136d3557a97daa9b9c2a4821de75108652`
- Executed build SHA: `ca4c86b561d7bd3f99de0ff6f11b322231c7a01b`
- Workflow: HULK SA Compatibility Lab
- Run: [#55 / 30383727238](https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30383727238)
- Event: push to `phase-3-v0.9.3.0-adaptive-foundation`
- Result shown by GitHub: success

The later commits from the executed SHA to the official head modify only
historical/signing workflows, signing documentation, and an AAB signing helper.
`app/`, `qa/compatibility/`, and Gradle product inputs are unchanged, so this is
the reproducible pre-upgrade baseline for the selected source.

## Actual execution

| Metric | Result |
|---|---:|
| Devices planned | 9 |
| Devices with complete evidence | 8 |
| Devices infrastructure-blocked | 1 |
| Test cases planned | 133 |
| Test cases executed | 126 |
| Test cases not executed | 7 |
| Case PASS | 0 |
| Case WARN | 126 |
| Product critical findings | 0 reported |
| Warnings | 222 |
| Infrastructure errors | 1 |
| Emulator retry attempts | 1 on Android TV 1080p |

The workflow did not emit an aggregate artifact. It uploaded nine independent
device artifacts and one debug APK artifact.

## Device matrix

| Device label | API | Geometry / density | Cases | Result |
|---|---:|---|---:|---|
| Pixel 4a simulation | 29 | 1080×2340 / 440 | 28 | WARN |
| Pixel 6 simulation | 31 | 1080×2400 / 420 | 14 | WARN |
| Pixel 8 Pro simulation | 35 | 1344×2992 / 480 | 14 | WARN |
| Galaxy S24 Ultra simulation | 35 | 1440×3120 / 560 | 14 | WARN |
| Pixel Tablet simulation | 35 | 1600×2560 / 320 | 28 | WARN |
| Nexus 9 simulation | 28 | 1536×2048 / 320 | 14 | WARN |
| Android TV 720p emulator | 36 | 1280×720 / 213 | 7 | WARN |
| Android TV 1080p emulator | 36 | 1920×1080 / 320 | 0 | BLOCKED |
| Android TV 4K emulator | 36 | 3840×2160 / 640 | 7 | WARN |

These are x86_64 emulator/simulation results. None is physical OEM or ARM proof.

## Finding distribution

| Finding | Count | Baseline interpretation |
|---|---:|---|
| `high_emulator_jank` | 126 | Emulator advisory; not a release blocker |
| `text_at_display_edge` | 34 | Needs human/geometry review |
| `slow_page_start` | 28 | Emulator advisory; not a physical budget |
| `interactive_overlap` | 20 findings / 24 pairs | Confirmed harness false positives |
| `possible_text_clipping` | 12 | Needs human/geometry review |
| `tv_safe_area` | 2 | Needs human review |
| Android TV 1080p blocked | 1 device | Infrastructure BLOCKED |

All 24 overlap pairs were verified in their XML hierarchies as ancestor/child
relationships between scroll containers and descendants. They are confirmed
false positives, not 24 product defects.

## Known false negatives

Opening the artifacts, rather than trusting the green workflow, proves these
pre-upgrade gaps:

1. Android TV 1080p produced only a 280-byte blocked summary after an ADB
   transport failure. The job and workflow still concluded success.
2. No final enforcement job checked missing reports or incomplete artifacts.
3. Favorites was absent from the seven-page matrix.
4. The Pixel 4a Home screenshot visibly contains overlapping hero texts, but no
   text-to-text geometry finding was emitted.
5. TV rail/content gutters were not measured, so large empty frames could pass.
6. Collapsed and expanded rail-logo geometry was not captured.
7. Downloads used static fixture records and did not prove positive transferred
   bytes, file growth, resume offset, or final integrity.
8. Every executed case was WARN. The 126 emulator-jank advisories dominate the
   signal and make regressions difficult to distinguish.
9. There was no approved visual-baseline comparison, Before/After/Diff bundle,
   accessibility gate, Compose behavior matrix, or release evidence aggregate.

## Baseline disposition

Overall baseline status: **BLOCKED**.

The baseline is suitable as historical evidence and as the “before” side of the
upgrade. It is not an accepted product-quality baseline and must never be used
to auto-approve visual changes.

