# NEO 4K Football — R04 competition allowlist and free-only architecture

Status: LOCAL_ROUND R04. The strict allowlist filter is authorized for the existing production
football endpoint after focused checks and backup. Broader R04 provider/broadcast work is local and
not deployed in this round. No Git commit, push, merge, signing, device update or customer release.

## 1. Strict match allowlist

The server filters normalized events, `matches`, `kickoffHistory`, `reminderCandidates`, feed teams
and stale cached payloads by this owner-approved list before truncation or presentation.

The verified default allowlist contains **27 available competition IDs**:

| Group | IDs |
| --- | --- |
| Saudi Pro League | 17 |
| UEFA | 7, 8, 83, 90 |
| Major leagues | 1, 3, 4, 5, 6 |
| Domestic cups | 39, 40, 41, 42, 43, 44 |
| CAF Champions League | 29 |
| International | 27, 68, 66, 30, 61, 58, 59, 60, 62, 63 |

The following approved competitions are **not present** in the verified Bzzoiro catalog and are
reported unavailable, never invented: Saudi King's Cup, Saudi Super Cup, AFC Champions League
Elite/Two, Egyptian Premier League, UAE Pro League, Qatar Stars League, Spanish Super Cup, Italian
Super Cup, FIFA Club World Cup, FIFA Arab Cup, Arabian Gulf Cup.

Moroccan (53) and Tunisian (47) domestic leagues remain optional and disabled unless
`include_optional_arab_domestic=true`. All other competitions are excluded.

The allowlist applies to the team catalog by keeping only teams whose verified competition
membership intersects the approved catalog sources. Catalog sources are bounded to major leagues,
continental/national competitions to keep the response below the installed app's 512 KiB parser
limit; cup-only lower-division entrants are not part of the catalog but their matches remain
allowlisted when they appear in a feed. The catalog is invalidated by an allowlist hash so an old
cache cannot bypass a policy change.

## 2. Free-only data architecture

- Existing `neo4ksa.com` PHP hosting remains the central provider/cache owner; Android does not call
  providers directly and does not depend on the development VPS being online.
- Bzzoiro remains primary under the existing 500/day configured feed cap, below the authorized
  2,500/day ceiling. Catalog is capped at 200/day.
- Day cache, stale last-good, quota/Retry-After and the persistent Android cache remain in use.
- A central season index (`season-YYYY.json`) stores published last-good fixtures; no permanent
  worker, WebSocket daemon or paid service is introduced.
- SportAPI.ai (180/day, 5,500/month), Highlightly (80/day) and API-Football cannot be qualified in
  this round because no authorized free credentials are present. They remain disabled and no IDs
  are invented. AFC/other missing competitions remain explicitly unavailable.
- Live polling remains bounded by the existing foreground/server refresh owner; no per-customer
  provider requests.

## 3. Broadcast channels

Qualified Bzzoiro aggregate: `GET /api/v2/broadcasts/?country_code=SA` (sample count 9, next null).
Confirmed channels returned in the sample include `MBC Shahid`, `beIN SPORTS 1`, and `Apple TV`
(the latter for a non-allowlisted MLS row and therefore discarded). The server caches confirmed
assignments centrally for six hours, fetches at most five pages, counts every page in the shared
request budget, reserves the next-day reminder slot, and never invents names or streaming links.
The optional `broadcasters` array is attached to feed match objects. Match details show
`القناة الناقلة` and confirmed channel names before the notification controls. R04 itself did not change Home card
geometry, colors, typography or focus behavior; the separate owner-adopted V5 neutral-surface
implementation in R05 governs those surfaces. There is no automatic channel playback.
