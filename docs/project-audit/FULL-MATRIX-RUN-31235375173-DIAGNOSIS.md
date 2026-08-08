# Full Matrix run 31235375173 — exact-head diagnosis

- Official head: `410c122bb37eb6d76ec89ecd0d417fc1713f8ced`
- Workflow: HULK SA Compatibility V2 - Full Matrix
- Result: **FAIL — 1 runtime profile only**
- Build/static/unit/test binaries: PASS
- TV 960x540 / 720p / 1080p / 4K: PASS
- Foldable: PASS
- Tablet profiles: PASS
- Other phone profiles: PASS
- Failing profile: `phone-landscape-font150-api35`
- Runtime evidence gate: `27 PASS / 1 FAIL / 0 BLOCKED / 0 SKIPPED`
- Failing gate: `runtime-junit`
- Instrumentation: `10 tests / 1 failure / 0 errors / 4 skipped`
- Exact assertion: `Subscribe or renew action is not reachable after scrolling`
- Test: `shortLandscapePhoneCanScrollToPrimaryLoginActions`

## Root-cause evidence

The current Login wide-mobile layout uses a compact landscape panel, but PR #101 removed the panel's `verticalScroll` modifier while narrowing the compact landscape panel to a maximum of 344dp. At 150% font scale, the signed-in controls expand vertically and the `اشترك او جدد` action falls below the visible window. The full-window evidence confirms the primary login button is visible while the renew action is below the lower edge.

The previously qualified Login source had a dedicated `ScrollState` and enabled vertical scrolling for `landscapePhone`. This is therefore a narrow reachability regression, not a general adaptive failure.

## Required correction

One isolated product PR only:

- restore vertical scrolling for the compact landscape Login panel;
- make the compact landscape panel width accessibility-aware at large font scale so the scroll gesture surface remains reachable in the current split layout;
- do not alter TV Login, portrait phone, tablet, branding, package/version/endpoint/signing, or the Compatibility V2 assertion;
- validate Fast V2 first, then rerun the exact failing Full Matrix profile/full matrix as required.
