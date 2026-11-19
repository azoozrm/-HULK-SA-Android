# NEO 4K Football — R03 complete section

Status: LOCAL_ROUND R03 source candidate. Superseded for implementation details by
`NEO4K-FOOTBALL-R03.1-CORRECTION.md`; this file remains the R03 section record. No production
deployment, device
refresh, signing, capture, Git commit/push or release authority follows from this document.

## 1. Scope

R03 completes the owner-approved football section as one coherent change:

- Home match rail from the approved Home board.
- Match Center with Riyadh-day navigation, filters and competition groups.
- Multiple favorite clubs and national teams per account/profile.
- Match details with per-fixture kickoff and 15-minute reminder preferences.
- Profile-scoped hide-scores applied to every football surface and notification presentation.
- Normalized provider metadata: Arabic display names where verified, original names, aliases,
  stable provider IDs, crest URLs, country, competition membership and national-team flags.

## 2. Verified provider coverage

Only the following Bzzoiro league IDs are enabled for the qualified catalog and metadata mapping:

| Group | League ID | Provider competition |
| --- | --- | --- |
| Saudi | 17 | Saudi Pro League |
| Arab | 53 | Botola Pro (Morocco) |
| Arab | 47 | Tunisian Ligue Professionnelle 1 |
| Europe | 1 | Premier League |
| Europe | 3 | La Liga |
| Europe | 4 | Serie A |
| Europe | 5 | Bundesliga |
| Europe | 6 | Ligue 1 |
| Europe | 7 | UEFA Champions League |
| Europe | 8 | UEFA Europa League |
| Europe | 83 | UEFA Conference League |
| National | 31 | International Friendly Games |
| National | 27 | World Cup |
| National | 61 | World Cup Qualification AFC |
| National | 64 | UEFA Nations League |
| National | 68 | AFC Asian Cup |

The provider returned no domestic league rows for the UAE, Qatar, Kuwait, Bahrain, Oman, Egypt,
Algeria, Jordan or Iraq in the qualification samples; AFC Champions League was also absent.
Those competitions remain explicitly unavailable. No fixture, league ID or national team is
invented. API-Football remains outside R03.

Arabic team names are mapped only from verified provider IDs: the full Saudi catalog (for
example `262` الهلال, `267` النصر, `271` الاتحاد, `258` الاهلي) plus a bounded set of major
European clubs used by the approved boards (`44` برشلونة, `57` ريال مدريد, `1` ليفربول,
`12` مانشستر سيتي and the other mapped IDs in `football-feed.php`), plus the verified World Cup
national-team IDs (for example `479` السعودية, `478` مصر, `490` الجزائر). Remaining teams keep
the provider's original name and are searchable by that name and its aliases. Competition Arabic
names are mapped for every enabled league ID listed above. Crests use
`https://sports.bzzoiro.com/img/team/{id}/` and
`https://sports.bzzoiro.com/img/league/{id}/`; a neutral themed shield/trophy fallback is shown
when an image is absent or fails to load.

## 3. Data and state contracts

### Normalized feed

`FootballTeam` extends the old `{id,name}` wire shape with optional `arabicName`, `logoUrl`,
`country`, `competitionIds`, `nationalTeam` and `aliases`. `FootballCompetition` adds optional
`arabicName`, `logoUrl` and `priority`. Older servers that omit these fields still parse; the app
falls back to the original names and neutral artwork.

### Favorites

`favorite_teams` stores the complete list per account/profile with each team's original
`selectedAtEpochMs`. The legacy `favorite_team` value is adopted exactly once when the plural key
is absent, preserving its timestamp. Removing and re-adding a team in one save retains the
retained team's timestamp; a genuinely new selection receives the current time. Kids profiles
remain fail-closed for the feed and picker.

### Hide scores

`hide_scores` is a per-profile boolean, default off. It masks score text only. Team names,
competition, kickoff time, live minute, final/postponed/cancelled status, crests and inbox
read/delete/clear behavior remain available. The same mask is applied to `footballKickoffDetail`
for newly rendered and already stored inbox items.

### Alerts

`match_alerts` stores per-fixture alert preferences keyed by canonical provider event ID:
kickoff, 15-minute reminder, manual tracking and the tracking timestamp. Following a team enables
the kickoff alert by default; a saved fixture preference overrides that default, including an
explicit all-off choice. Reminder entries are updated in place when a confirmed reschedule changes
the kickoff, are deduplicated by canonical identity/profile/alias, and are removed when the fixture
is cancelled, starts or otherwise stops being pending. Delivery remains foreground/resume
scheduling into the existing in-app inbox only. No closed-app or system-push behavior is claimed.

### Kickoff provenance

A start transition is accepted only from a non-stale current snapshot and either:

- a recorded foreground not-started → started transition whose previous observation was made by
  the running app after the relevant team selection, or
- a provider `event_date`/`startedAt` that is already confirmed by the server clock and selection
  time.

A cached or stale last-good response never becomes a transition source. The app deliberately
clears the previous observation when a response is stale.

### Catalog completion and Retry-After

The server caches a catalog only when every enabled league and page succeeds. A short page with
counted rows behind it is incomplete, and two full pages that still leave provider-counted rows
are also incomplete. The Android parser rejects `complete=false`. A stale complete catalog keeps
the provider `cachedAt` value, so re-fetching stale data does not restart the 24-hour freshness
window. Retry-After from the league list, fixture pages, live rows and catalog requests is stored
server-side; the app also honors the returned header and body countdown before another call.

## 4. Screens

### Home

The rail appears before `متابعة المشاهدة`, uses compact crest/name/score cards, and exposes
`ادارة فرقي` (heart) and `عرض الكل`. Followed fixtures lead; the rest use competition priority and
then live/upcoming/final order. Directory and row identity remain stable across refresh, and
details return to the same card.

### Match Center

`الامس / اليوم / غدا` in Riyadh time, `الكل / مباريات فرقي / مباشر`, competition groups ordered
Saudi → Gulf/Arab → Europe → other, one focusable row per match with a left chevron, and
`ادارة فرقي` / `اخفاء النتائج` actions. Day changes publish into a separate day snapshot and never
replace the Home snapshot or generate kickoff alerts. Details restore the selected row and scroll
position.

### Favorite teams

`فرقي المفضلة` opens a draft picker with Arabic/English search over the complete catalog,
region controls, `الكل` / `المختارة (n)`, real crests and names, a persistent gold check, and a
pale in-bounds focus edge. `حفظ` commits; `الغاء`, Back and outside dismissal discard. Teams with
no fixture today remain searchable. Gulf regions show an explicit provider-unavailable message
when empty.

### Match details and reminders

`تفاصيل المباراة` shows both crests, Arabic display names, competition, confirmed status and
Riyadh time. The two checkboxes are `عند بداية المباراة` and `قبل المباراة بـ 15 دقيقة`; they
work for a single fixture even when neither team is followed.

## 5. Verification

Focused JVM coverage includes merge score preservation, final protection, competition priority,
bilingual search aliases, multiple favorites/migration timestamps, catalog stale metadata and
incomplete rejection, score masking, reminder creation/reschedule/cancellation, explicit
all-off fixture override, Match Center filters/groups and day helpers. PHP coverage includes
score preservation, catalog two-page completeness, Arabic/crest metadata, league Retry-After
stopping further provider calls and catalog Retry-After recovery.

Owner visual acceptance is **PENDING**. Screen capture is not authorized in R03. Physical TV
remote/focus and phone/tablet layouts therefore remain a bounded owner-review item even though
the source follows the shared focus/adaptive contracts.
