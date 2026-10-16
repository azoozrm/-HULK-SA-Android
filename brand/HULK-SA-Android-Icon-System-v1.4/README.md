# HULK SA Icon System — Flat Gold R03

Based on official PR #351 merge 4b54dfd91bde501ba545245e4690f08fe0881eff.
This is a local Codex candidate, not a published commit or installed APK.
R02 is retained in the earlier archive. R03 preserves its original HS/wordmark paths,
flat gold, banner spacing, legacy densities and master artwork.

## Colour authority

HulkTheme.kt primary gold: #E6C352. Opaque brand background: #030402.
Every coloured brand asset uses this flat gold, without gradients or substitute tints.
Notification and themed-icon masks are white/transparent: Android supplies their colour.
Antialiased edges use alpha coverage; this is not an artwork gradient.
Third-party channel logos, real posters, semantic UI states and decorative watermarks
retain their existing rendering rules; this is not a global theme recolour.

## Android and in-app anatomy

- Adaptive foreground/monochrome: 108dp canvas, centered 65.5dp-high original shield,
  entirely inside the central radius-33dp safe circle. Transparent foreground, solid background.
- Explicit television-anydpi-v26 adaptive resource wins over the v21 legacy vector;
  oversized television foreground override removed. Existing TV application aliases retained.
- API 33 phone/round resources include monochrome. TV does not require themed icons.
- TV banner remains a density-complete static 16:9 asset, not an adaptive banner.
- In-app framed marks reuse hulk_sa_mark_reference with Fit, no launcher Crop or recolour.
  Runtime loading placeholders fit the shortest side independently of real poster Crop.
- Real successful remote channel logos retain their pale backing and Fit.
- Launcher 48/72/96/144/192px; TV 80/120/160/240/320px; banner
  160x90/240x135/320x180/480x270/640x360; notification 24/36/48/72/96px;
  Store 512px; SVG/PDF/EPS/4096 masters and 1080p/4K banner exports.

## Reproduction

From repository root: python3 brand/HULK-SA-Android-Icon-System-v1.4/export_assets.py
Authoring dependencies: Inkscape, Pillow, pypdf. No new Android dependency.
Then run validate_assets.py --repo-root . --output /tmp/hulk-icon-checks.json.
The generated android-res directory mirrors real runtime resource names/qualifiers.
Use the complete source patch for integration; see INTEGRATION.md.

## Qualification

Asset/source checks are separate from Android compilation, device behaviour and owner acceptance.
Current evidence is in the accompanying R03 delivery packet. No universal runtime PASS is claimed.
Owner review PENDING; device capture OFF. DeepSeek is limited to exact-candidate build/install.
