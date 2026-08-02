# HULK SA — final Galaxy landscape and cutout qualification

Date: `2026-08-02`

## Qualified application source

- Application source head: `2b4b9704bd6ee367a320968bb425be74ffa32ef9`
- Final qualification workflow head: `e2d798554ff4d6165126b4f9d78470a563a693e9`
- Post-evidence cleanup head: `2c02ca23dc4710d5cfd63945da737c7127c76029`
- Package: `sa.hulksa.player`
- Version: `0.9.3.20` (`versionCode 64`)

The workflow-only and cleanup commits after the application source do not change the `app/` tree.

## Physical-device findings corrected

Evidence supplied from a real Galaxy exposed four gaps that earlier emulator gates did not close:

1. The short-landscape Login composition was oversized and clipped its primary actions.
2. Live, Movies and Series allowed only their inner item list to move while the page header and category bar stayed fixed.
3. Display cutouts could overlap foreground controls.
4. Opaque system-window/background areas appeared as black borders around normal mobile pages.

Permanent corrections:

- The short-landscape Login uses a full-height, internally scrollable compact panel.
- Compact Login fields, buttons and option rows use reduced real dimensions and padding rather than only a maximum-height constraint.
- Movies and Series use one short-landscape `LazyVerticalGrid` containing header, category controls and content.
- Live uses one short-landscape `LazyColumn` containing header, categories and channels.
- Safe foreground content reserves complete `WindowInsets.safeDrawing`, including display cutouts.
- The app background continues behind transparent system bars and cutout areas while normal pages remain non-immersive.
- `ShortLandscapeMainShellTest` independently verifies page scrolling for Movies, Series and Live.

## Independent landscape retest — PASS

- Run: `30769624152`
- Artifact: `HULK-SA-GALAXY-LANDSCAPE-V3-30769624152` (`8840181534`)
- Artifact digest: `sha256:50bb92ad2d03fb15bd1a9a8c72dffd6560fbadcc3ce0b75bf88f0d17749b0b53`
- API: `35`
- Geometry: `2340×1080`
- Density: `420`
- Font scale: `1.5`
- Locale: `ar-SA`
- Emulated cutout: `com.android.internal.display.cutout.emulation.corner`
- General runtime instrumentation: `PASS`
- Login primary-action reachability: `PASS`
- `ShortLandscapeMainShellTest`: `3 tests`, `0 failures`, `0 errors`
- Movies page scroll contract: `PASS`
- Series page scroll contract: `PASS`
- Live page scroll contract: `PASS`
- PNG and XML geometry: `2340×1080` `PASS`
- Runtime Evidence Gate and checksums: `PASS`

## Final Portrait + Landscape + production-signing qualification — 3/3 PASS

Run: `30770039149`

### Galaxy Landscape with cutout

- Job: `galaxy-landscape-cutout`
- Result: `PASS`
- Artifact: `HULK-SA-GALAXY-V2-landscape-30770039149` (`8840309690`)
- Artifact digest: `sha256:5ffe09c14832e8d6c041bc053bb15201b71407eba5fed299f3bdfac61729fafc`
- Login reachability, cutout safety, page scrolling, geometry, evidence gate and checksums: `PASS`

### Galaxy Portrait with cutout

- Job: `galaxy-portrait-cutout`
- Result: `PASS`
- Artifact: `HULK-SA-GALAXY-V2-portrait-30770039149` (`8840306704`)
- Artifact digest: `sha256:4a36324d1003397847ac9ab5a62a82fc90e8011501de5361f10e266644c17fdc`
- Safe drawing, transparent system bars, geometry, evidence gate and checksums: `PASS`

### Production-signed APK and AAB

- Job: `production-signing`
- Result: `PASS`
- Artifact: `HULK-SA-GALAXY-V2-SIGNED-30770039149` (`8840267489`)
- Artifact digest: `sha256:c8773780f4cc3b5571f9a19027cde339eb1ad47bd29c5d96658b3f3c62b3305a`
- APK SHA-256: `2efbeff7817fc23359962b2e808a29ea0313a8f36f471d9c74db65dee975eb3a`
- AAB SHA-256: `59b467d70f43b005c5822f0b46b8b238281b4d9398b66bb12fe0e2a8c95ee642`
- Production certificate SHA-256: `144E0548DA502AA8BD060A9A280A98BB9DC6763387BF8B20EA100B5FED652FE0`
- APK signing schemes `v1` and `v2`: `PASS`
- Package/version identity: `PASS`
- APK/AAB architecture policy: `PASS`
- Release Evidence Gate and all artifact checksums: `PASS`

No secret or keystore material was printed, committed or changed.

## Repository cleanup — PASS

- The accidental generated `app/build/**` commit was reverted without force-push.
- The compact Login correction was reapplied as a clean source commit.
- Temporary patch, recovery, locator and one-time physical-qualification workflows were removed after collecting evidence.
- Generated Python `__pycache__` files were removed.
- No application source file changed during evidence cleanup.

## Remaining human/physical confirmation

The automated defect contracts now pass. The exact signed APK from run `30770039149` still requires installation on the user's real Galaxy and visual confirmation against the original photos, especially Samsung OEM system-navigation behavior. Xiaomi receiver, TCL television and real-service download behavior remain separate physical-device/service checks unless already reconfirmed with this exact binary.

PR `#78` remains Draft, open and unmerged. No release, auto-merge, force-push, package/version change, endpoint change, branding change or signing-identity change was performed.
