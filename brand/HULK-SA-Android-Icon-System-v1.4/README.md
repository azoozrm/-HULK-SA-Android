# HULK SA Android Icon System v1.4

This corrected package preserves the **same visual composition as the supplied official references** for the square badge and horizontal lockup. The square badge master uses the exact shield, wordmark, border, and spacing from the provided badge reference. The TV banner and lockup masters use the exact shield, wordmark, and spacing from the provided horizontal reference, so the banner is no longer cropped.

## Included

- SVG / PDF / EPS / PNG masters
- Adaptive icon foreground / background / monochrome
- Legacy launcher icons mdpi through xxxhdpi
- Round launcher icons mdpi through xxxhdpi
- Android TV / Google TV launcher icons
- TV banners 160x90 through 640x360
- 1080p and 4K TV banner masters
- Play Store 512x512 icon
- White notification icons
- Android Studio resource structure under `android-res/`
- Visual previews and QA reports

## Important design note

- **Badge master, Play Store icon, and TV banner** follow the supplied references closely.
- **Adaptive / launcher / notification icons** remain shield-only for runtime clarity and mask safety.

## Android integration note

This package is staged for review only. Integrate only after approval and repository verification.

## Final v1.4 technical audit

- Genuine vector masters: SVG/PDF/EPS contain no embedded raster artwork.
- Badge reference geometry fidelity IoU: **0.9545**.
- Horizontal lockup reference geometry fidelity IoU: **0.9531**.
- Full-background PNG exports are opaque RGB with an embedded sRGB profile.
- TV banner content margins are validated at every density and at 1080p/4K master sizes.
- Android SDK/aapt2/Gradle compilation was not available in this environment and is therefore not claimed.
