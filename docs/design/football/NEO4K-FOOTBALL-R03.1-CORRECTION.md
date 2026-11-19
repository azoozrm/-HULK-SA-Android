# NEO 4K Football — R03.1 final pre-device corrections

Status: LOCAL_ROUND R03.1 source correction on the frozen R03 candidate. No Git publication,
signing, production deployment, device installation, capture or owner visual acceptance follows.

R03 remains preserved as its own evidence round. This document records only the integration
defects corrected after independent R03 review.

## 1. Alert correctness

- Typed tombstones now live in `suppressedAlerts` with keys bound to account, profile, canonical
  fixture alias and alert type. Deleting/clearing a reminder no longer suppresses the independent
  kickoff alert, and vice versa. Legacy `suppressedEventIds` still suppress kickoff only.
- Followed-team eligibility uses the earliest qualifying involved selection. A later selection of
  the opposing team cannot invalidate an earlier subscription.
- `upsertFootballAlertPreferences` removes obsolete alias rows, keeps the canonical fixture row,
  preserves the timestamp for an unchanged save and assigns a new timestamp only after a real
  change.
- Preference resolution chooses the latest explicit alias row. An old event ID cannot override a
  newer all-off choice.
- Delivered kickoff/reminder items survive rescheduling, cancellation, disappearance and
  reappearance with one canonical item per fixture/type; read state is preserved. New generation
  stops for cancelled or non-pending fixtures.
- Reminder text calculates the real remaining time and never claims fifteen minutes before a
  rescheduled kickoff.
- Tomorrow's bounded upcoming fixtures are carried in `reminderCandidates`, so a 00:05 Riyadh
  fixture can produce its 23:50 reminder before midnight.
- Alert evidence requires a fresh provider/server observation (`cachedAt`/provider `last_updated`),
  not app receipt time alone. Started provider rows additionally require a provider observation
  after the scheduled event date. Expired cached snapshots cannot create new starts or reminders.
- The server now exposes `last_updated` as `providerUpdatedAt`; the app uses it for alert gating,
  latest-detail resolution and duplicate-row freshness.

## 2. Live state and profile ownership

- A feed refresh captured before a hide-scores toggle cannot republish the old visibility; the
  current state remains authoritative once the profile is available.
- Open details resolve the latest fixture by stable identity/aliases, so score, status and
  schedule update while the surface remains open.
- Selected-day refresh uses the real cache TTL instead of the former non-stale shortcut, honors
  Retry-After, prevents same-day overlap, rejects obsolete generation/profile/discovery results,
  marks failed refreshes stale and preserves the independent Home snapshot and foreground poll.
- Match Center recomputes its Riyadh day on resume and at the next midnight boundary.

## 3. Arabic metadata and completeness

- Unknown teams now classify as `OTHER`, never `ARAB`.
- Team-list merging enriches a stable provider ID with later Arabic/crest/country/alias/
  competition/national-team metadata instead of keeping the first sparse record.
- Every exposed catalog team receives an Arabic display value: verified Arabic names first, then
  deterministic ID-bound transliteration. Original provider names remain in `name`/aliases for
  English search. Competition names use the same verified-map/transliteration policy.
- Newly created football notifications use Arabic competition/team display names.
- Additional R03 league/national-team IDs were checked against live sanitized provider evidence.
  The catalog now pages up to eight pages per league; the verified 16-league catalog is 1,355
  teams, 289,259 compact JSON bytes, all with Arabic display values and crest URLs, using 23
  catalog requests.
- Newer provider cancellation/postponement wins when its `last_updated` is newer, while verified
  final scores are preserved if the newer row omits them. Truncated fixture pagination is never
  cached or published as a complete fresh feed.

## 4. Approved boards

- Match Center now renders inside the existing shell content area; the TV navigation rail remains
  visible and no global destination was added.
- Wide layouts place day and filter groups on one compact toolbar; narrow layouts split them.
- Home/Match Center show kickoff time once with `لم تبدا` as the upcoming status.
- Team-manager and details footer actions are centered with `حفظ` physically right of the
  secondary action.
- Selected gold checks use the committed gold role while focus uses the pale `goldBright` edge.
- Focus returns to the card/row or management trigger after child surfaces close; loading, empty
  and error football cards all expose a reachable refresh/primary action.
- Shared theme, typography, hero, surrounding Home sections and accepted product behavior remain.

## 5. Verification boundary

Focused JVM regressions cover typed suppression, delete/clear independence, profile scoping,
multi-team eligibility, unchanged preference saves, alias preference precedence, reschedule/
cancel/disappear/reappear read ownership, reminder text, midnight reminders, expired observations,
provider timestamp gating, enriched team merging, region classification, detail resolution, TTL
and hide-scores race helpers. PHP tests cover provider `last_updated`, newer cancellation/final
protection, truncated pagination, reminder candidates and transliteration fallback.

Full JVM, unsigned release, Compatibility V2, PHP football/catalog and backend contracts are run
for the frozen R03.1 candidate. Device refresh and capture remain NOT RUN / NOT AUTHORIZED.
