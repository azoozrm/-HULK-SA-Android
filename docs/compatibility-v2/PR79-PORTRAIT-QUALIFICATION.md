# PR #79 Portrait qualification

## Source identity

- Base SHA: `7a28b217bae94cb22ee01ea1a9d381c79c019d83`
- Verified permanent source commit: `a30ebbda48e2075b98062ed7020d371987065198`
- Branch: `fix/v09320-galaxy-landscape-clean`
- Package: `sa.hulksa.player`
- Version: `0.9.3.20` (`versionCode 64`)
- Production endpoint: `http://3162356.xyz:8080`
- Qualified ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Legacy `x86`: excluded

## Root cause history

### Run `30817683596`

- Result: `FAIL`
- Permanent source commit: none
- Root cause: the portrait recovery workflow reached runtime infrastructure instability and did not complete the evidence/commit path. Runner-only changes were not accepted as a permanent repair.

### Run `30818656585`

- Result: `FAIL`
- Instrumentation typing assertion: `PASS`
- Runtime evidence gate: `FAIL`
- Root cause: the final stable evidence still contained an active IME that covered the lower Login actions.

### Run `30820299730`

- Result: `FAIL`
- Login action reachability with active IME: `PASS`
- Root cause: the test sent an unconditional Back key after programmatic IME dismissal. Because the IME was already hidden, Back left the activity and the final foreground assertion failed.

## Permanent correction

- Stabilized adaptive dimensions against temporary Compose-container shrinkage while the IME is visible.
- Preserved Login field state with `rememberSaveable`.
- Kept the compact portrait Login container vertically scrollable and proved that both primary actions can be reached while the IME is active.
- Removed the portrait-only oversized container and negative horizontal offset workaround.
- Replaced per-destination fake edge expansion with the shared adaptive gutter policy.
- Added stable IME dismissal checks that do not navigate away when the keyboard is already hidden.
- Added conditional collection of the dedicated portrait PNG/XML evidence from app external storage.
- Made missing portrait evidence a runtime `FAIL`.
- Removed `.github/workflows/pr79-portrait-recovery.yml` and all `tools/pr79_portrait_*.py` temporary files after proof and permanent commit.

## Final evidence

### Portrait repair run

- Run: `30821749460`
- Result: `PASS`
- Artifact: `HULK-SA-PR79-PORTRAIT-DIAGNOSTIC-30821749460`
- Artifact ID: `8859482598`
- Artifact digest: `sha256:ba4fc2608dbd2188c75b4443c0b81e50a9f6c6bf5e89d430161e82c9d30b27eb`
- Requested/effective physical geometry: `1080×2340`
- Density: `420 dpi`
- Effective logical window: `411×891 dp`
- Orientation: portrait
- Locale: `ar-SA`
- Input mode exercised: touch plus software keyboard
- Python Lab tests: `PASS`
- Static validation: `PASS`
- JVM unit tests: `PASS`
- Lint: `PASS`
- Debug APK: `PASS`
- AndroidTest APK: `PASS`
- Instrumentation: `1 test`, `0 failures`
- IME stable-state gate: `PASS`
- Runtime foreground gate: `PASS`
- Mandatory checksums: `PASS`

### Preserved screenshots and hierarchies

- `portrait-login-ime-stable.png`: typed username/password with the IME active.
- `portrait-login-ime-stable.xml`: matching application hierarchy.
- `portrait-login-ime-actions-reachable.png`: Login and Subscribe actions fully reachable while the IME remains active.
- `portrait-login-ime-actions-reachable.xml`: matching action-reachability hierarchy.
- `full-window.png`: final stable Login window with no IME, launcher, permission dialog, clipping, or off-window controls.
- `window.xml`: final application hierarchy.

## Identity and safety

- Approved logo asset and branding: `PASS` — unchanged by the permanent commit.
- Package/version/endpoint: `PASS` — unchanged.
- PR state: `PASS` — open, Draft, and unmerged.
- Release: `NOT TESTED` in this portrait-specific run; no release was published.
- Physical Galaxy/small-phone/tablet/Xiaomi/TCL/720p/1080p/4K checks: `PENDING PHYSICAL DEVICE`.

This document records only the completed portrait repair. It does not claim completion of the remaining Bottom Navigation, phone/keyboard focus separation, expanded matrix, 4K runtime, tablet resize, or full-screen application audit work.
