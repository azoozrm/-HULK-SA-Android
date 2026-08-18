# v1.8 — HULK AI Foundation

This batch starts v1.8 from the canonical branch after the user explicitly rejected PR #193 and chose to keep the previous Home design.

## Scope

This PR adds the first HULK AI foundation only. It does not redesign Home and does not add a new Home row.

The engine uses only data already available to the active profile:

- Movie and series catalog items.
- Real provider metadata already present on those items.
- Active-profile watch history.
- Active-profile favorites / My List.
- Real rating and added-at metadata when available.

## Safety and data-integrity contract

- No external AI service is called in this foundation.
- No cast, actor, genre, rating, title, plot, franchise, popularity or editorial data is invented.
- Live history never influences VOD preferences.
- Watched/favorited titles are excluded from discovery when alternatives exist.
- Series episode history resolves the parent series when `parentContentId` is available.
- Results are deterministic for the same catalog and profile signals.
- Movie and series representation is preserved when both are sufficiently available.
- Cold start uses real rating/freshness/metadata only.

## Explainability

Each suggestion carries typed evidence rather than fabricated prose:

- favorite genre
- recent genre
- favorite category
- recent category
- high real rating
- fresh catalog content

A later UI batch can translate these evidence types into user-facing Arabic reasons without changing the ranking contract.

## v1.8 stage boundary

This PR is HULK AI Foundation only.

Next v1.8 batches are intentionally separate:

1. HULK AI UI / query interaction using this real-data context.
2. X-Ray metadata foundation using only real `ContentDetails` data such as cast/director when the provider actually returns it.
3. X-Ray UI qualification on movie/series details and playback where technically supported.

## Fixed parameters

No change to package, Application ID, version name, version code, signing, endpoint, reseller API/auth, brand, logo, colors, app name or ABI policy.
