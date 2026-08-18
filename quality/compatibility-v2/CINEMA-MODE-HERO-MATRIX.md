# v1.7 Cinema Mode — Hero Curation Foundation

This batch starts v1.7 Cinema Mode without adding new Home rows or duplicating existing recommendation sections.

## Product boundary

The current Home structure stays intact:

- متابعة المشاهدة
- لانك شاهدت
- مقترح لك
- قائمتي / المفضلة
- احدث الاضافات
- الاعلى تقييما
- Live rows already present

No Smart Collections rows are introduced by this batch.

## Cinema Hero curation contract

The rotating Home hero must prefer real provider metadata and cinematic artwork:

- Prefer `backdropUrl` when at least eight backdrop-capable candidates exist.
- Keep poster artwork only as a fail-safe for small or incomplete catalogs.
- Prefer titles with useful plot, genre, year and rating metadata.
- If at least eight unwatched candidates exist, recently watched titles do not occupy the discovery hero.
- Diversify hero candidates across categories when alternatives exist.
- Preserve Movie / Series balance when both content types are available.
- Do not invent popularity, genre, artwork, cast, franchise or editorial metadata.
- Do not call a new backend or change Xtream / reseller APIs.

## Regression requirements

- `لانك شاهدت` remains driven by profile watch signals.
- `مقترح لك` remains the existing personalized recommendation row.
- `قائمتي` remains user-controlled and is not mutated by Cinema Mode.
- Continue Watching grouping remains unchanged.
- Live personalization remains unchanged.
- Hero selection remains deterministic for the same catalog/profile inputs.

## Next Cinema Mode batch

After this curation foundation is qualified, the UI batch may improve the existing hero presentation only: adaptive hero height, stronger backdrop treatment, typography/metadata hierarchy, button sizing, subtle transitions and TV/4K safe composition. It must not add duplicate Home sections.

## Fixed parameters

No change to package, Application ID, version name, version code, signing, endpoint, reseller API/auth, brand, logo, colors, app name or ABI policy.