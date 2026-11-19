# NEO 4K Football — R02.1 correction

Status: LOCAL_ROUND R02.1 source candidate on top of R02. This supersedes only the conflicting
R02 merge, kickoff-start, catalog-search and catalog-completeness wording. No production, device,
capture, Git, signing or release authority follows.

## 1. Full catalog search

- The app no longer applies the former 500-team display cap to `knownTeams`.
- `mergeFootballTeamLists` keeps the complete distinct catalog; the picker searches that full
  source (and today's match teams). `LazyColumn` remains the bounded display owner.
- `FootballTeamCatalogStore` reloads the full parsed catalog; a 700-team test proves the 699th
  team remains searchable after encode/decode reload.
- One favorite team per profile is unchanged.

## 2. Duplicate merge and final-result protection

- PHP and Android rank `finished` above `live` above `upcoming`.
- A confirmed final row wins over an older live row in either input order.
- Two live rows use the higher provider minute; equal-minute rows use the later observation
  (the live endpoint is appended after the schedule list) and its fresh score.
- The first row's fixture identity/metadata (competition, teams, kickoff, replacement link,
  aliases) is preserved; only status/score/minute come from the fresher row.
- Tests: live 0-0 -> live 1-0; final 2-1 + older live 0-0 in both orders (PHP and JVM).

## 3. Reliable kickoff evidence

- Removed `now - current_minute*60` from `ops_football_started_at` and from app generation.
  The minute freezes at halftime and cannot establish kickoff.
- Provider `event_date`/`startedAt` is the only timestamp evidence for an unobserved start.
- A recorded foreground transition from not-started to started uses the observation time as the
  confirmed transition. The previous snapshot must have been observed by the running app in the
  current foreground session (within five minutes), after the customer selected the team. A
  snapshot reloaded from cache after a cold start is not a transition, and app reopen time alone
  is never a kickoff time.
- Eligibility compares the confirmed start (or stable provider timestamp) with the customer's
  team-selection time. Following after kickoff therefore cannot generate a start item from
  reopen alone.
- The 24-hour `kickoffHistory` remains separate from today's `matches`; profile isolation and
  read/delete/clear dedupe remain.
- Tests: follow-after-kickoff, halftime frozen minute + recorded transition, genuine delayed
  kickoff after selection, next-morning history, stale history, 24h pruning and profile key.

## 4. Catalog completeness, Retry-After, quota

- A catalog refresh is cached only when every configured league and page succeeds.
- A short page whose provider count still has rows behind it is incomplete.
- Before every page the server checks the remaining daily quota and an active `retry.json`
  window; no provider call is made while Retry-After is active.
- A successful first page followed by 429, or a one-request quota stop, returns the last complete
  catalog with `stale=true`/`complete=true` and does not overwrite `teams.json`.
- The app parser rejects `complete=false`; the store cannot save an incomplete catalog.
- Recovery after cooldown writes a new complete cache. The catalog cap is 200/day and the feed
  budget remains 6,000/day at four requests per refresh.

## 5. Build and deployment wording

- Local `:app:assembleRelease` is an **unsigned** build proof only. No APK was signed, installed
  or deployed in R02.1.
- Production discovery/Git/device remain outside this round.
