# Visual baselines

No visual baseline is approved in this directory yet.

A baseline may be added only after a human reviews the complete uncropped window image, device profile, density, font scale, locale, window metrics, insets and checksum. CI must never copy an `actual` image into this directory and must never update a baseline after a comparison failure.

Until approved baselines exist, the full visual-regression gate reports:

`BLOCKED: HUMAN-APPROVED FULL-WINDOW BASELINE REQUIRED`

This is not a passing visual result.
