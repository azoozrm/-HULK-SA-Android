# HULK SA Android Icon Pack — V3

Official adopted icon identity for `sa.hulksa.player`.

- Source archive: `HULK_SA_Android_Icon_Pack_FINAL_FIXED_VERIFIED_V3.zip`
- Source archive SHA-256: `04abb97bad59a1210dd2fccab36c50d246f5f3d502df7a5b1feb69a01fd50d13`
- Package QA: 88 files, 88 inventory rows, 87/87 checksum entries PASS (checksum manifest excludes itself).
- Active Android resources are installed under `app/src/main/res`.
- `BrandLogo()` continues to load `R.drawable.hulk_sa_logo`, so Login, Home/navigation branding, and content placeholders use the adopted V3 logo without duplicated UI code.
- Android launcher resources include legacy, round, adaptive v26, and themed monochrome v33 mappings.
- Android TV continues to use `@drawable/tv_banner`; `@drawable/ic_tv_launcher` is also included.

The old conflicting `drawable-nodpi/hulk_sa_logo.webp` and obsolete `drawable-nodpi/ic_banner.webp` are removed by the adoption commit.
