# Physical-device qualification runbook

## Required targets

1. Galaxy ARM64 phone.
2. Xiaomi Android TV or TV box.
3. TCL Android TV.
4. Low-spec ARM Android device.
5. ARM64 tablet.

## Preparation

- Use the exact candidate APK SHA-256 and approved production certificate.
- Record manufacturer, model, Android/API, firmware/build, ABI list, display resolution/density,
  font scale, locale, available storage, and network type.
- Never place credentials in shell history, screenshots, logs, or the artifact.
- Capture current package/version before install.

## Required journeys

- Clean install and launch.
- In-place upgrade over the signed production baseline; confirm one package and retained safe data.
- Login through the protected procedure.
- Open every shell destination with collapsed and expanded rail.
- D-pad focus loop, long press, Back, IME entry/exit, and focus restoration.
- Real playback and channel change.
- Real download: record bytes becoming positive, part-file growth, pause/resume, final size/checksum,
  offline playback, two concurrent cards, and restart recovery.
- TV: title-safe edges, overscan, rail gutters, logo optical ratio, bottom clearance, and card fit.

## Evidence bundle

The artifact `HULK-SA-PHYSICAL-DEVICE-EVIDENCE` must contain:

- `run-manifest.json` with `status: PASS` and `device_type: physical`;
- candidate APK SHA-256, package/version, certificate digest, and install/upgrade result;
- timestamped screenshots and a video;
- redacted logcat and crash/ANR buffer;
- focus/navigation trace;
- playback result;
- download byte samples, final size, and checksum;
- tester/date and unresolved findings.

If any mandatory item is absent, the release gate reports `BLOCKED`. A density simulation cannot
replace this runbook.

