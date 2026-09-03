# Compatibility V2 static result

| Status | Count |
|---|---:|
| PASS | 27 |
| FAIL | 0 |
| BLOCKED | 0 |
| SKIPPED | 0 |

## Checks

- **PASS** `package-identity` — Application ID is sa.hulksa.player
- **PASS** `namespace-identity` — Namespace is sa.hulksa.player
- **PASS** `version-name` — versionName is 0.9.3.20
- **PASS** `version-code` — versionCode is 64
- **PASS** `reseller-api-runtime-config` — Production uses only the configurable HTTPS reseller API
- **PASS** `abi-policy` — Qualified ABI set is present
- **PASS** `reseller-access-login-order` — Login fields are ordered access code, username, then password
- **PASS** `reseller-access-resolution` — Android resolves the access code through the HULK API before IPTV login
- **PASS** `legacy-iptv-host-absent` — Legacy IPTV host is absent from Android production source
- **PASS** `manifest-leanback-optional` — Leanback is optional
- **PASS** `manifest-touch-optional` — Touchscreen is optional
- **PASS** `manifest-launchers` — Phone and TV launchers are declared
- **PASS** `manifest-rtl` — RTL support is enabled
- **PASS** `manifest-tv-banner` — Phone and TV use distinct launcher resources and the density-aware TV banner
- **PASS** `approved-logo-sha256` — Approved brand asset bytes are unchanged: app/src/main/res/drawable-nodpi/hulk_sa_logo.png
- **PASS** `approved-banner-sha256` — Approved brand asset bytes are unchanged: app/src/main/res/mipmap-xhdpi/tv_banner.png
- **PASS** `approved-tv-launcher-sha256` — Approved brand asset bytes are unchanged: app/src/main/res/mipmap-xhdpi/ic_launcher_tv.png
- **PASS** `android-icon-density-matrix` — Phone, round, TV, banner, and notification assets cover every required density at exact dimensions
- **PASS** `legacy-tv-banner-resource-absent` — The obsolete single-density TV banner resource is absent
- **PASS** `logo-content-scale` — Logo rendering uses ContentScale.Fit
- **PASS** `legacy-lab-removed` — Legacy compatibility/quality lab paths are absent
- **PASS** `production-test-hooks-absent` — No legacy QA marker or overlay is present in production sources
- **PASS** `hardcoded-pixel-layout-absent` — No fixed 720p/1080p pixel layout literals were found
- **PASS** `player-surface-dpad-seek-direction` — TV/remote player surface maps RTL D-pad Left to forward and Right to rewind while preserving non-TV mapping
- **PASS** `player-seekbar-dpad-direction` — TV/remote seek bar maps RTL D-pad Left to forward and Right to rewind while preserving non-TV mapping
- **PASS** `player-focus-race-policy` — Player focus ownership contains no timing retries
- **PASS** `v2-workflow-fail-closed` — V2 workflows contain no continue-on-error or automatic baseline update
