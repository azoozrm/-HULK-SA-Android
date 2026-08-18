# v1.7 Smart Collections Foundation

This is the first v1.7 Collections batch after PR #190.

## Scope

- Add a deterministic Smart Collections engine for Movies and Series.
- Use only real catalog metadata already available in `ContentItem`.
- Use active-profile history and favorites as personalization signals.
- Resolve series episode history through `parentContentId` / series title before scoring.
- Build separate movie and series collections.
- Build up to two genre collections using only genres that actually exist in the catalog.
- Exclude already watched and favorited items from discovery collections.
- Ignore Live items/history for VOD collections.
- Provide a cold-start curated fallback when a profile has no history/favorites.

## Data integrity rules

- No invented genres, actors, franchises, rankings, popularity data, or server metadata.
- No network/API/backend changes.
- No cross-profile persistence: the engine only consumes the active profile state supplied by the app.
- No changes to package, Application ID, version, versionCode, signing, endpoint, reseller API/auth, brand, logo, colors, App Name, or ABI policy.

## Stage boundary

This batch is the v1.7 Smart Collections **foundation**. It intentionally does not add new Home rows yet. UI integration, grouped lists / Watch Later improvements, and Cinema Mode remain later v1.7 batches after this foundation is qualified.
