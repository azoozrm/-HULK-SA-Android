# HULK SA Android — Release Signing Qualification

## Scope

This change prepares a fail-closed release-signing path for the canonical v0.9.3.17 project. It does not create, rotate, replace, or commit a signing key.

The production signing identity must remain the identity already used by the installable stable application. A newly generated key must not be substituted because Android would reject an in-place upgrade from an APK signed by a different certificate.

## Current status

| Area | Status | Evidence required |
|---|---|---|
| Canonical unsigned Release build | Complete | Governing canonical CI |
| Secret-gated Gradle signing configuration | Complete | PR preflight |
| Partial signing input rejection | Complete | PR preflight must fail closed |
| APK signature and certificate verification | Complete as tooling | Signed workflow run required |
| AAB signature and certificate verification | Complete as tooling | Signed workflow run required |
| Production signer identity | Not verified | Real production keystore and approved SHA-256 certificate digest |
| Clean signed installation | Not executed | Signed APK plus Android device/emulator evidence |
| Upgrade from stable APK | Not executed | Real stable baseline APK signed with the same certificate |

## Required GitHub Actions secrets

- `HULK_RELEASE_KEYSTORE_BASE64`: base64 encoding of the approved production keystore.
- `HULK_RELEASE_KEY_ALIAS`: approved key alias.
- `HULK_RELEASE_STORE_PASSWORD`: keystore password.
- `HULK_RELEASE_KEY_PASSWORD`: private-key password.
- `HULK_RELEASE_CERT_SHA256`: expected signer certificate SHA-256 digest.
- `HULK_PORTAL_URL`: optional production portal URL already supported by the project.

No secret value, keystore, private key, or password belongs in Git, a PR body, logs, reports, or uploaded source artifacts.

## Fail-closed behavior

- With no signing inputs, normal CI still produces an explicitly unsigned verification Release.
- If any signing property is supplied, all four signing properties become mandatory.
- A missing keystore file aborts Gradle configuration.
- Signed qualification compares the generated APK and AAB signer certificate with `HULK_RELEASE_CERT_SHA256`.
- APK verification requires v1 signing for API 23 and v2 signing for modern Android.
- Artifacts are uploaded only after package identity, version, signature, certificate, ABI, and checksum checks pass.

## Signed qualification command path

The `HULK SA Signed Release Qualification` workflow is started manually after the approved secrets are installed. Its signed job must remain blocked when any required secret is absent.

Expected package identity for this release:

- Application ID: `sa.hulksa.player`
- Version code: `61`
- Version name: `0.9.3.17`

## Upgrade qualification

`tools/verify-upgrade-compatibility.sh` performs static eligibility checks between a baseline APK and a candidate APK:

- identical application ID;
- identical signer certificate SHA-256;
- candidate `versionCode` greater than baseline;
- both APK signatures cryptographically valid.

This static result is not an installation result. Final acceptance still requires:

1. install the real stable baseline APK;
2. launch it successfully;
3. install the signed candidate using Android package replacement;
4. confirm the upgrade succeeds without uninstalling or clearing data;
5. launch the upgraded application;
6. record package version and signer evidence.

## Prohibited shortcuts

- Do not generate a new production key merely to make CI green.
- Do not sign Release with the debug key.
- Do not report unsigned, debug-signed, or test-key artifacts as Production.
- Do not claim upgrade compatibility from package name alone.
- Do not expose signing material in troubleshooting output.
