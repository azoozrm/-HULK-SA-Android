# NEO 4K Football Feed Contract — R01 qualification

Status: LOCAL_ROUND R01 source candidate. This document is retained as R01 history; for R02+
provider schema, IDs, budget and response-shape corrections read
`NEO4K-FOOTBALL-FEED-R02-CORRECTION.md`, `NEO4K-FOOTBALL-FEED-R02.1-CORRECTION.md` and
`NEO4K-FOOTBALL-R03-SECTION.md`, `NEO4K-FOOTBALL-R03.1-CORRECTION.md` and
`NEO4K-FOOTBALL-R04-ALLOWLIST.md`. This
document records provider qualification and the first-party feed contract and the honest gaps.
It grants no purchase, credential, deployment, capture or release authority.

## 1. Authority and scope

- Task: `neo4k-football-home-r01`, phase LOCAL_ROUND R01.
- Base: `9c41b25104dd52e544938cbc9a1cfc83bd8e21ec`.
- The app receives a normalized first-party feed. Provider API keys stay in the PHP server
  configuration (`hosting/hulk-operations/config.php`, never committed).
- Deployment is outside this round. The Operations `football` discovery object is disabled by
  default; the app hides the rail until the owner deploys and enables it.
- No provider registration, paid plan, trial, key creation or production endpoint change was
  performed.

## 2. Bzzoiro Football v2 qualification

Facts verified from the live public documentation and public coverage endpoint on
2026-11-18 (UTC):

- Base URL: `https://sports.bzzoiro.com/api/v2/`.
- Authentication: `Authorization: Token YOUR_API_KEY`.
- Events list: `GET /events/` with `league_id`, `season_id`, `team_id`, `team_name`, `status`,
  `date_from`, `date_to`, `stage`, `round`, `limit` (max 200), `offset`.
- Live list: `GET /events/live/`; server-cached 10–30 seconds; provider asks for polling no
  faster than 10 seconds.
- Competition/season discovery: `GET /leagues/`, `GET /leagues/{id}/season/`,
  `GET /leagues/{id}/seasons/`; current season must be resolved, never hardcoded.
- `replaced_by` is documented on the postponed row and points to the rescheduled fixture. A
  `season_id` list leaves out a postponed fixture whose replacement is present.
- Statuses: `upcoming`, `live`, `finished`, `cancelled`, `postponed`, `unresolved`. Scores stay
  `null` when not confirmed; `unresolved` never infers a result.
- Free football allowance: 7,500 requests/day per account, reset at midnight UTC; the
  per-address burst limit for cached read endpoints is 25 requests/second (burst 110). Both
  limits use `429` with a `Retry-After` header and `RateLimit`/`RateLimit-Policy` headers.
- Public coverage call at `2026-11-18T21:57:40Z` returned HTTP 200:
  `football`, `in_season`, 555 events next 7 days, 2,218 next 30 days, 0 live at that instant,
  next event `2026-11-18T22:30:00Z`.

Live access gap (BLOCKED, not inferred):

- No authorized Bzzoiro token exists in the task worktree, the lab environment or an approved
  secret store available to this executor.
- `GET /api/v2/events/?date_from=2026-11-18&date_to=2026-11-18&limit=1` returned HTTP 401.
- `GET /api/v2/leagues/?country=Saudi%20Arabia` returned HTTP 401.
- Therefore **competition IDs, season IDs, actual fixture rows, live-status rows and a real
  live sample are not recorded**. They must be resolved with an owner-supplied token before
  production deployment.

Qualification matrix (documented target; IDs pending the 401 gap):

| Region / group | Competitions the feed is expected to cover | IDs / seasons | Fixture access |
| --- | --- | --- | --- |
| Saudi | Saudi Pro League (Roshn Saudi League), King Cup, Saudi Super Cup | PENDING — 401 | BLOCKED |
| Gulf | UAE Pro League / President's Cup, Qatar Stars League / Amir Cup, Kuwait Premier League, Bahraini Premier League, Oman Professional League | PENDING — 401 | BLOCKED |
| Major Arab | Egyptian Premier League, Botola Pro (Morocco), Ligue 1 Algeria, Ligue 1 Tunisia, Jordanian Pro League, Iraqi Stars League | PENDING — 401 | BLOCKED |
| European | Premier League, La Liga, Serie A, Bundesliga, Ligue 1, UEFA Champions League, UEFA Europa League, UEFA Conference League | PENDING — 401 | BLOCKED |
| AFC | AFC Champions League Elite, AFC Champions League Two | PENDING — 401 | BLOCKED |

This is a coverage target, not a claim. Documentation says 88 active competitions; it does not
prove each target competition is present in the current plan or that every season has fixtures.

## 3. API-Football gap check

- Provider family: API-Sports / API-Football v3 (`https://v3.football.api-sports.io/`), key
  header family `x-apisports-key`.
- The public documentation and pricing pages returned HTTP 403 from this lab on 2026-11-18, so
  the current free-plan quota, endpoint behaviour and competition coverage could not be
  independently verified from the provider pages.
- No authorized API-Football credential or live sample exists in the task scope.
- The API endpoint itself returned HTTP 403 with
  `errors.token = "Missing application key..."` for `/status` and `/leagues?search=saudi`
  at `2026-11-18T23:20:59Z`.
- Result: API-Football qualification is **BLOCKED — no credential and provider documentation
  inaccessible (403)**. It is not enabled as a fallback and no quota or coverage claim is made.
- The server configuration contains an optional, empty API-Football key block so an owner can
  add a path later without changing the app. It is intentionally unused in R01.

## 4. First-party feed endpoint

Route: `GET /hulk-operations/api/app/v1/sports/?day=YYYY-MM-DD`.

Server source:

- `hosting/hulk-operations/api/app/v1/sports/index.php`
- `hosting/hulk-operations/lib/football-feed.php`
- discovery advertisement in `hosting/hulk-operations/lib/operations.php` under optional
  `football`.

Behaviour:

- `day` defaults to the current `Asia/Riyadh` calendar day. A provided day must be a valid
  `YYYY-MM-DD`; otherwise HTTP 400.
- One refresh is shared by all devices. The refresh lock prevents parallel provider calls.
- Cache is per Riyadh day. A successful empty day is cached and returned as `empty=true`,
  `stale=false`. A provider failure/quota/Retry-After returns the last-good payload with
  `stale=true`, never as an empty day.
- The refresh requests the previous + current day (`date_from`/`date_to`) and, only for the
  current day, the live list. That is at most two provider requests per refresh.
- Riyadh day selection keeps the requested day plus:
  - a match already `live` at midnight (up to 6 hours after kickoff);
  - a `finished` result that kicked off in the previous 3 hours, marked `carriedOver`.
- `replaced_by` chains resolve to the replacement row. The old provider ID is retained as an
  alias. A postponed row whose replacement is outside the requested window is not shown on the
  old day.
- Scores are nullable integers. Unconfirmed scores are never converted to zero.
- The feed sorts live, then upcoming, then finished/other; the app re-sorts the favorite team's
  fixtures first without hiding other matches.
- `Retry-After` from a provider `429/503` is stored server-side and served as a bounded
  countdown; the next refresh is skipped until it expires. A numeric `Retry-After` starts when the
  provider response actually arrives, not when the refresh started, so a late response cannot
  leave a cooldown that has already expired. Both routes share that one deadline.
- Quota ledger: default 500 feed requests/day and 200 catalog requests/day, both sharing a
  2,500-request/day aggregate hard ceiling; refresh TTL 30–300 seconds (default 60), maximum
  four requests per refresh. Each attempted request (feed, catalog, reminder or broadcaster) is
  durably reserved in the route quota and the shared aggregate under the existing `provider.lock`
  before transport. A present ledger must contain an explicit non-negative integer `count`; a
  missing/invalid count, an unreadable/corrupt file or a failed ledger write fails closed and
  performs no provider call. Each reservation reads the UTC day of that attempt, so a refresh that
  crosses midnight charges every request to its actual day. Route usage is compared only with its
  own route ceiling, and the shared ceiling only with the aggregate ledger; one is never subtracted
  from the other. A batch that cannot reserve its next attempt fails closed instead of publishing a
  fresh empty result.
- Broadcaster assignments are keyed by the exact provider event ID plus the provider `event_date`
  kickoff instant through fetch, cache and attachment. Both an old and a new kickoff for one
  provider ID are retained regardless of row order; each fixture receives only its confirmed
  assignment. A legacy ID-only cache cannot confirm a kickoff and is ignored.
- Response headers: `Content-Type: application/json`, `Cache-Control: public, max-age=<TTL>`,
  `ETag`, `Retry-After` when applicable.
- Server errors never include provider keys, raw provider bodies or account data.

Response shape (schema version 1):

```json
{
  "schemaVersion": 1,
  "day": "2026-11-19",
  "timezone": "Asia/Riyadh",
  "generatedAt": 1795037400,
  "cachedAt": 1795037400,
  "stale": false,
  "empty": false,
  "retryAfterSeconds": null,
  "refreshSeconds": 60,
  "provider": "bzzoiro",
  "matches": [
    {
      "id": "evt:202",
      "providerEventId": "202",
      "aliases": ["101"],
      "competition": {"id": "85", "name": "Saudi Pro League", "country": "Saudi Arabia"},
      "homeTeam": {"id": "10", "name": "Al Hilal"},
      "awayTeam": {"id": "20", "name": "Al Nassr"},
      "kickoffAt": 1795037400,
      "status": "live",
      "homeScore": null,
      "awayScore": null,
      "minute": 63,
      "carriedOver": false
    }
  ],
  "teams": [{"id": "10", "name": "Al Hilal"}]
}
```

## 5. App discovery and security

- Optional Operations object:
  `{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":60}`.
- Missing/false/malformed/invalid discovery disables the football rail only; the rest of the
  Operations payload remains valid.
- Accepted URL: HTTPS, host `neo4ksa.com` or `hulksa.com`, port 443/absent, no credentials,
  query or fragment, exact path `/hulk-operations/api/app/v1/sports/`.
- The app does not follow redirects, bounds the response body to 512 KiB and never receives a
  provider key.
- Request cadence is at most one first-party refresh per discovery TTL per app instance, plus
  explicit retry/resume checks. The server cache absorbs multiple devices.

## 6. Local last-good, favorite team and kickoff inbox

- Feed cache: account-independent, three recent Riyadh days; the last-good day remains
  available when the new day has no successful fetch. A stale snapshot is labelled as stale,
  separately from an empty day.
- Team catalog: the on-disk cache is bootstrap-only for the active account/profile + discovery
  URL and is rejected when its stored source differs. An established verified catalog, including a
  valid empty one, survives older feed, day and cache publications. Only the latest catalog request
  for the current owner, non-stale over an existing authority and with a provider cache time not
  older than the authority, may replace it; stale or obsolete responses are dropped before
  persistence and publication. Provider `cachedAt` remains distinct from the device receipt time;
  an unknown provider timestamp is never replaced by the local clock. The device receipt time is
  freshness metadata only; durable write ordering uses the accepted request/owner generation, which
  stays monotonic across owner resets and discovery-URL changes. One process-wide lock serializes
  the generation comparison, coherent record read and commit for every store instance bound to the
  catalog preferences file, so a phone and TV store cannot interleave an older write over a newer
  one.
- Favorite team: one team per resolved account/profile, stored through the existing
  `AccountProfileStateStore` scope. Selecting a team replaces the previous choice; removal is
  explicit. The team picker searches the loaded feed plus a bounded known-team registry so an
  empty day can still change the favorite. Kids profiles fail closed: the football rail and
  kickoff inbox are not published there, preserving the existing Kids isolation contract.
- Kickoff inbox:
  - A typed `LocalNotificationItem.Kickoff` merges into the existing center and bell count.
  - Start is confirmed only by a fresh provider transition to `live`/`finished` or by a
    provider-confirmed started status whose actual kickoff timestamp is at least 90 seconds in
    the past. Clock passage with `upcoming` never creates an item.
  - Dedupe is by canonical event plus account/profile. `replaced_by` aliases collapse into one
    notification.
  - Catch-up on resume covers the selected team, events confirmed started after the selection
    timestamp, within the last 24 hours.
  - Profile/team generation checks discard stale responses. Read, delete, mark-all-read and
    clear-all survive resync; delete uses event tombstones and clear uses a kickoff watermark.
  - In-app only: no system notification, external push, deep link or provider call.

## 7. Checks and residual gaps

Executed locally:

- PHP fixture tests: normalization, nullable scores, `replaced_by`, Riyadh bounds, midnight
  carry-over, empty vs stale, two-request aggregate, cache reuse, 503 last-good,
  `Retry-After`, missing credential, invalid day, discovery allowlist, receipt-anchored shared
  cooldown across UTC midnight in both route orders, and per-attempt quota rollover at
  499/500 feed, 199/200 catalog and 2,499/2,500 aggregate.
- JVM tests: Riyadh day/time, replacement aliases, carry-over, nullable scores, favorite
  ordering, Arabic search normalization, feed parse/stale, kickoff transitions/catch-up/dedupe,
  delete/clear resync, codec round-trip, optional discovery parsing, and canceled catalog saves
  that must not replace a newer same-source or switched-source catalog (including a valid empty
  one) when the receipt clock is equal or rolled back.
- `:app:compileDebugKotlin` and focused unit tests.

Not run / blocked:

- Real Bzzoiro competition/season IDs and fixture/live samples: BLOCKED — 401, no authorized
  token.
- API-Football coverage/quota/sample: BLOCKED — no credential, provider docs 403.
- Device/install/runtime/visual validation: NOT RUN — device surfaces NONE and capture OFF.
- Deployment/Operations `football` enablement: outside LOCAL_ROUND; no production change.
