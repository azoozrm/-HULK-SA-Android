# v1.8 HULK AI — Testable Search Interaction

## Scope

This batch turns the existing HULK AI ranking foundation into a user-testable feature without changing the accepted Home screen.

## Entry behavior

The normal Search experience remains unchanged for ordinary title/channel searches.
HULK AI activates only when the Search query starts as a recommendation request, for example:

- `رشح لي فيلم اكشن جديد`
- `اقترح لي مسلسل جريمة عالي التقييم`
- `ابي فيلم 2025`
- `HULK AI movie action`

Clearing the query or choosing **بحث عادي** returns to the existing Profile Smart Search.

## Data integrity

HULK AI uses only:

- real Movie / Series catalog items,
- real catalog genre, year, rating, poster/backdrop and added timestamps,
- the active profile's real history,
- the active profile's real favorites.

It does not invent cast, title, genre, year, rating or availability. If a requested constraint has no real match, the UI states that the results are approximate instead of pretending a match exists.

## Behavior

- Explicit Movie requests return Movies only when available.
- Explicit Series requests return Series only when available.
- Recognized genres are matched against actual provider genre metadata.
- Year requests use the actual item year.
- "high rating" requests rank by the actual numeric rating when present.
- "recent/latest" requests use actual added timestamps.
- Active-profile history/favorites personalize ties and ranking.
- Watched/favorited items are excluded from discovery when alternatives exist.
- Live channels are never returned by HULK AI VOD recommendations.
- Every displayed reason is derived from a real query/profile/catalog signal.

## Adaptive / input boundary

- Phone keeps the interaction inside the Search destination and provides explicit return to normal Search.
- TV gets explicit **بحث عادي** and **الرئيسية** exits.
- TV search field supports OK-to-edit, Down/Search-to-results, and Back keyboard dismissal.
- First result can return Up to the AI query field.
- Voice input remains available through the existing voice-search launcher.

## Non-scope

This batch does not:

- redesign Home,
- add a Home row,
- replace ordinary Search,
- call an external LLM/service,
- implement X-Ray,
- invent provider metadata.
