# HULK SA — Original Player Boards and Current UI Contract

Reference ID: HULK-PLAYER-20261004.
The owner approved these three corrected TV concept boards on 2026-10-04.
Subsequent Movies corrections and section acceptance are consolidated in
[HULK-SHARED-UI-DESIGN-CONTRACT.md](../shared-ui/HULK-SHARED-UI-DESIGN-CONTRACT.md),
the single current shared UI specification. Read that contract and active accepted source before work.

## Original unchanged boards

| Board | Historical states | SHA-256 |
| --- | --- | --- |
| [01-player-more-preview.png](reference/20261004/01-player-more-preview.png) | Playback, More, original preview concept, clean viewing | d26302109b13abbd2068ee5e6f350f0cf8134aa9132046043c4106f6b2fac3a0 |
| [02-player-options.png](reference/20261004/02-player-options.png) | Speed, picture size, original time-entry concept, lock | fdb925d46abc989982b28915a067728baeb1679ace8e8af9daf4f1337d2e67ae |
| [03-player-dialogs-states.png](reference/20261004/03-player-dialogs-states.png) | Resume, offline, error, loading, unlock, ended | 6d4aae03365a3012fbeb9e539a5e07fbcaf03176a525000d4c81a0a4c79c244a |

Source archive: HULK-Movies-Player-TV-Designs-20261004.zip;
SHA-256 `67ae543703ce9b0caa3e77daa2daf48f2a6408c5bedb54f1d05977a6da61f7e6`.
No board bytes are changed by the contract update. These are fourteen illustrative TV states,
not APK screenshots or phone qualification. Titles, frames and sample times are not shipping data.

## Later decisions supersede conflicting board details

| Record | Current rule |
| --- | --- |
| R25 | Remove Movies seek image preview and floating timestamp bubble on phone/TV; keep timeline circle/thumb and normal times; no Movies preview decoder/warm-up |
| R26 | Go-to-time has a left opening chevron and non-editable HH:MM:SS with up/down arrows; no soft keyboard/IME |
| R27 | Pending/cancelled Movie Resume cannot overwrite saved position/duration during preparation, timers, lifecycle or disposal |
| R28 | FIRST remote BACK exits pending Resume once, preserving history and originating context; no focus-only first press |
| 2026-10-06 | Owner accepted the completed Movies section; PR #335 merged |

Accepted Movies candidate: `7cc19ff7d4c6eff071b7f07ca1db523ebf969f8e`.
Merge: `90f854bcae2f56b7a1cc185438dbc41f43d8ad54`.
These are historical acceptance identities, not permanently current HEAD values.

Preserve the shared contract's actual Movies palette/state roles, icon registry, physical RTL anatomy,
five VOD slots, stable touch/TV geometry, exclusive modal input and section-specific capabilities.
It supersedes this file's former duplicated normative sections and Shared UI Version 2 conflicts,
including seven-tool Series strips and full-gold VOD menu focus.

Product design/Movies acceptance does not certify all layouts or release readiness. Series and Live
inherit the target under later bounded tasks; they are not declared migrated by this index.
Capture remains OFF unless currently authorized. No Android changes, capture, signing or release
are authorized by the reference.
