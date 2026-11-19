# NEO 4K Football Feed — R02 correction

Status: LOCAL_ROUND R02 source candidate on top of R01. This document supersedes R01's
provider matrix, request-budget wording and response shape where they conflict. It grants no
deployment, device, capture, merge, signing or release authority.

## 1. Provider schema verified against the authorized Bzzoiro account

Verified on 2026-11-19 from the live authenticated API:

- `GET /api/v2/events/` returns `{"count", "results": [...]}`.
- A list event uses flat team fields: `home_team_id` + `home_team` (string),
  `away_team_id` + `away_team`, not nested team objects.
- A list event has `league_id` but not `league_name`; league names come from
  `GET /api/v2/leagues/?limit=200` (`results[].id/name/country`).
- `GET /api/v2/events/live/` returns `{"count", "events": [...]}` (different envelope).
- Live status values observed: `inprogress` and `finished`; list status uses `notstarted`.
  The server maps `notstarted` -> `upcoming`, `inprogress` -> `live`.
- Season endpoint: `GET /api/v2/leagues/{id}/season/` returns
  `{"league_id", "season": {"id", "name", "year", "start_date", "end_date", "stages"}}`.
- Team catalog: `GET /api/v2/teams/?league_id={id}&limit=200&offset={n}` returns
  `{"count", "next", "previous", "results": [{"id","name","short_name","country",...}]}`.
- Pagination is maximum 200 rows per page. The server reads at most two pages per events query
  (400 rows) and two pages per catalog competition.
- Provider free allowance: 7,500 requests/day per account; per-address burst limit 25 rps.
  `429` carries `Retry-After`.

## 2. Verified competition and season IDs

| Region | Competition | League ID | Current season ID | Status |
| --- | --- | ---: | ---: | --- |
| Saudi | Saudi Pro League | 17 | 1631 | COVERED |
| Morocco | Botola Pro | 53 | 1962 | COVERED |
| Tunisia | Tunisian Ligue Professionnelle 1 | 47 | 1634 | COVERED |
| England | Premier League | 1 | 1058 | COVERED |
| Spain | La Liga | 3 | 1307 | COVERED |
| Italy | Serie A | 4 | 1375 | COVERED |
| Germany | Bundesliga | 5 | 1091 | COVERED |
| France | Ligue 1 | 6 | 1311 | COVERED |
| Europe | UEFA Champions League | 7 | 1112 | COVERED |
| Europe | UEFA Europa League | 8 | 1269 | COVERED |
| Europe | UEFA Conference League | 83 | 1606 | COVERED |
| Gulf | UAE Pro League / Qatar Stars League / Kuwait / Bahrain / Oman | — | — | NOT PRESENT in the 103-league catalog |
| Arab | Egyptian Premier League / Ligue 1 Algeria / Jordanian Pro League / Iraqi Stars League | — | — | NOT PRESENT in the 103-league catalog |
| AFC | AFC Champions League Elite / Two | — | — | NOT PRESENT in the catalog |

The catalog contains 103 competitions total. It has no domestic Gulf league other than Saudi,
no Egyptian/Algerian/Jordanian/Iraqi league, and no AFC Champions League. API-Football remains
unused: no authorized credential in this task and its documentation/status endpoint returned
HTTP 403. The gap is recorded, not filled with invented IDs or fake fixtures.

Live sample on 2026-11-19: five `events` rows, four `inprogress` and one `finished`, all
Brasileirão Série A. The feed endpoint returned 8 displayed matches and 17 separate 24-hour
history rows for the day, including the live rows with their provider minute/score.

## 3. Request budget

- Feed refresh: maximum 4 provider requests per refresh (league-name cache, up to two event
  pages, live list). Default `max_requests_per_day` is **6,000**, below the provider's 7,500.
- At the 60-second TTL, 24 hours of continuous foreground refresh is 1,440 refreshes; at the
  4-request ceiling that is 5,760 provider requests, within 6,000. The server cache still
  aggregates all devices.
- Team catalog: daily cache; default 11 competitions, 14 provider requests on the first refresh
  (some competitions need a second page), then zero for 24 hours. Cap 200/day, separate ledger.
- R01's 500/day default was insufficient for 250 two-call refreshes; R02 raises the configured
  budget to 6,000 and documents the four-call worst case.
- A provider `Retry-After` remains authoritative over both feed and catalog scheduling.

## 4. Feed response additions

```json
{
  "matches": [{"id":"evt:...", "startedAt": 1795046004, "...": "same R01 fields"}],
  "kickoffHistory": [{"id":"evt:...", "startedAt": 1795046004, "...": "same match shape"}],
  "teams": [{"id":"266", "name":"Al-Taawoun", "country":"Saudi Arabia"}]
}
```

- `matches` is the Riyadh-day rail: same-day fixtures plus a live match carried across midnight.
- `kickoffHistory` is a separate 24-hour list of provider-started events. It is used only for
  kickoff eligibility/catch-up and never becomes the displayed day.
- `startedAt` is the provider kickoff for finished rows, or `now - current_minute*60` clamped to
  the provider kickoff for live rows. It is an evidence-backed start estimate, not a fabricated
  clock.
- Schedule/live duplicates merge by `providerEventId`: schedule metadata (competition, teams,
  kickoff, replacement link) is preserved; the started row supplies status/score/minute.
- Team catalog endpoint: `GET /hulk-operations/api/app/v1/sports/teams/`, daily cached, returns
  stable provider team IDs/names/country. The app calls it on picker open; an empty match day can
  still search the verified 948-team catalog.

## 5. App behavior corrections

- Foreground refresh loop reuses `onAppForegroundChanged` lifecycle ownership. It does not run in
  the background, for Kids, without an owner/config, or while a request is active. It waits for
  the server TTL or `Retry-After`, then calls the existing refresh path. Home and the inbox are
  published by the same state/notification owners, so a foreground refresh updates both without
  navigation.
- Kickoff eligibility uses the separate history and `startedAt`; clock passage with `upcoming`
  never creates an item. A stale snapshot can retain existing items but cannot create/regress
  one. Notifications older than 24 hours are pruned. Delayed starts use the provider elapsed
  minute; next-morning catch-up uses history even when today is empty.
- Profile isolation, read/delete/clear tombstones/watermark and one favorite team per profile are
  unchanged. The catalog is global public data; the selected team is account/profile scoped.

## 6. R02 deployment boundary

The R02 packet states production discovery remains disabled. The executor observed the R01
owner-authorized production deployment still enabled and made **no R02 production, Git, signing,
device or capture changes**. The R02 source candidate is local only; production remains at the
R01 state until a separate owner decision.
