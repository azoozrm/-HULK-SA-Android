# HULK SA Android — Shared UI Design Contract

Version 3 · consolidated after Movies acceptance on 2026-10-06.

This is the single shared UI specification for future section work. It preserves the accepted
Movies result; Series and Live inherit its visual language while keeping their real functions.
It consolidates Shared UI Version 2, the approved player reference and the subsequent owner
decisions. Its adoption is documentation work, not permission to redesign another section.

## 1. Authority, baseline and adoption

Current owner instructions control scope. Applicable live repository engineering, execution,
physical and evidence contracts control implementation. This file controls shared appearance
and interaction requirements; it does not grant merge, release, capture or device-data authority.

The preserved Movies implementation is PR [#335](https://github.com/azoozrm/-HULK-SA-Android/pull/335):
- Accepted candidate: `7cc19ff7d4c6eff071b7f07ca1db523ebf969f8e`, including R25–R28.
- Merge: `90f854bcae2f56b7a1cc185438dbc41f43d8ad54`.
- Owner decision on 2026-10-06: «انتهى اخر تعديل كذا خلاص تمام قسم الافلام».
- The coordinator reviewed R28 source/hash, archive/manifest, recorded runtime and final CI.
  The TV Movies Recent route and phone Home route were exercised; TV Home Continue Watching
  remains NOT RUN in the executor evidence. Owner acceptance does not turn that limit into PASS.

These revisions are historical baseline identities, not instructions to reset newer source or
install old APKs. Re-query the live source before work. Movies is closed as an accepted UI section;
its acceptance does not certify every device/window or production release.

Use this file as the canonical contract after repository adoption. The former VPS reference at
`/srv/hulk-android/references/shared-ui/HULK-SHARED-UI-DESIGN-CONTRACT.md` is a historical copy
unless synchronized to this version. `../player/APPROVED-PLAYER-REFERENCE.md` indexes the original
boards and points here; it is not a competing specification.

## 2. Shared foundations

Use `LocalHulkColors` and `HulkTheme.kt`. Preserve IBM Plex Sans Arabic regular, medium, semibold
and bold, existing typography roles, original HS assets, RTL policy, shapes and measured spacing.
Colors and dimensions come from accepted source, not photographed displays or generated boards.

| Token | Exact value | Role |
| --- | --- | --- |
| gold | #E6C352 | Warm accent, semantic icons, primary fill, committed category, specified progress |
| goldBright | #FFF0A8 | Accepted pale focus/highlight and scoped player/Resume accents |
| goldDeep | #9A7A23 | Existing deeper accent where used |
| background | #030402 | Main background |
| surface | #111108 | Base dark surface |
| surfaceRaised | #1B1A0E | Existing raised surface |
| text | #FFF9EB | Ivory wording, titles, metadata values and Details tab labels |
| textMuted | #B8B3A4 | Secondary explanation and values where specified |
| line | ARGB 0x47E6C352 | Existing translucent warm-gold line |
| danger | #FF746C | Existing semantic danger role; not the old pink Movies error banner |
| onPrimary | #100E04 | Dark foreground on theme primary gold |

Preserve accepted component surfaces/alpha rather than flattening every surface to one hex.
Source examples: Movie card #10110C, footer #12130E, notice #11120D, idle download #151711,
focused download #2A281B, player decision surface ARGB 0xF2141510, More ARGB 0xF20A0B08.
These are scoped roles, not authorization to recolor all callers.

### State matrix

| Component/state | Required treatment |
| --- | --- |
| Unselected category | Dark surface, ivory wording, warm-gold star/clock |
| Committed category | Gold fill, dark wording/simple icon; persists after focus leaves |
| Catalog/Details metadata | Gold semantic icon/field label, ivory real value |
| Details tabs | All labels ivory; selected short ivory underline |
| Primary action | Gold fill, accepted dark caption/icon |
| Secondary action | Accepted dark surface, ivory caption, scoped gold semantic icon |
| Compact Movies transport | Normal ivory; selected favorite gold; focused pale accent; primary Play/Pause gold circle with dark glyph |
| VOD More/option focus | Gold 14% backing and 1.5dp gold edge; ivory label, goldBright glyph |
| Selected unfocused VOD option | Gold 16% backing and persistent gold check; reserved check slot |
| Disabled compact transport | textMuted at accepted 45% alpha; ineligible input |
| Resume decision | Accepted dark card, pale-gold history/title/progress accents, real title and muted saved-time wording |
| Movie blocking error | Same bounded dark-card family; warm-gold status glyph, ivory title/body, muted context |

Normal, focus, selection, favorite, disabled and loading are separate states. Focus never commits
an option or changes playback. Preserve `goldFocusEdge` inside existing bounds: 2dp goldBright
edge with the adjacent dark separator. No focus scale, size jump or position change.

Version 2's solid-gold/dark-text player menu focus is superseded for the shared target by the
accepted Movies VOD row treatment above. Current Live/legacy Series differences are migration
work for later authorized tasks; this contract does not claim they are already aligned.

## 3. Text and icon registry

App-authored Arabic follows the existing owner rule: `أ/إ/آ → ا` only; preserve `ئ/ؤ/ء`.
Keep `ملائم`, `استئناف`, `ملء الشاشة` and `مؤقت` correct. No added diacritics or terminal
full stops; when used, Arabic comma spacing is ` ، `. Server text remains unchanged.
Numeric media time, percentages and speeds keep stable LTR order inside the Arabic UI.

Action captions must remain complete. Do not shrink type or ellipsize buttons to force fit.
Card titles may use the accepted two-line slot/ellipsis. Long content remains reachable.

| Meaning/context | Accepted icon source |
| --- | --- |
| Catalog Favorites / Recent | Existing star / Schedule clock, warm gold |
| Details/player favorite | Favorite / FavoriteBorder heart from observed favorite state |
| Resume history | Rounded.History |
| Movies rewind / forward 10 seconds | MovieRewind10Icon / MovieForward10Icon in MovieTransportIcons.kt |
| Play / Pause / More | Rounded.PlayArrow / Pause / MoreHoriz |
| Go to time / speed / picture menu | Rounded.Schedule / Speed / current-mode vodPictureSizeGlyph |
| Restart / lock | Rounded.Replay / Lock |
| Picture options Fit / Zoom / Fill | FitScreen / ZoomIn / CropFree via vodPictureSizeGlyph |
| Time increment / decrement | KeyboardArrowUp / KeyboardArrowDown |
| Open child / selected option | ChevronLeft / Check |
| Confirmed network failure / other failure | WifiOff / ErrorOutline via moviesErrorIcon |
| Retry | Existing Refresh action glyph |

Use existing vector/assets and mappings. Do not substitute a visually different icon by name,
draw the number 10 twice, mirror numerals/media direction, or generate a replacement logo.
Download glyphs follow `movieDownloadControlIcon` and the actual status, not a fixed generic icon.

### Physical RTL placement

| Context | Physical RIGHT | Physical LEFT |
| --- | --- | --- |
| Catalog/Details/Resume/error simple action | Caption | Its single simple icon beside wording |
| Server category | Original framed HS badge | Caption following badge |
| Player child/value/option row | Semantic glyph immediately beside Arabic label | Value, left-facing opening chevron and/or reserved check |
| Player direct action | Arabic caption | Single action glyph |
| Numeric option | Value | Reserved selected-check column |
| Player main tool | Icon ABOVE caption | Same compact slot, not a menu-row layout |

Back navigation chevrons face physically right; child-opening chevrons at the left edge face left.
Do not duplicate glyphs in both columns. The accepted Favorites/Recent simple-icon gap is 6dp;
do not add hidden icon-box spacing or apply it to every unrelated component.

## 4. Catalog, categories and cards

Use the accepted Movies toolbar/category hierarchy: real section title/count, Search, Refresh and
Category Management; same heights, type, shapes and content gutters. Category width follows real
wording. Physical RTL order is All, Favorites, Recent, then actual ordered server categories.
Movies labels include `الافلام`, `الكل`, `المفضلة`, `اخر مشاهدة` and `ادارة الفئات`.

Preserve category selection separately from focus, profile-scoped hide/show/reorder, stable server
identity, long press, search, restoration and management-trigger return. Notices stay within the
content gutter and do not cover the navigation rail.

Movies catalog and Recent use accepted compact square artwork plus fixed footer slots. Neighboring
cards reserve the same two title lines and metadata area even when data is missing or arrives later.
Keep actual artwork/crop, gold metadata glyphs and ivory values. Missing data never becomes a fake
rating, duration or progress. Recent shows actual saved time/total/progress without a seek thumb;
preserve its existing footer progress orientation independently from the physical-LTR player bar.

Series catalog inherits this card family. Season/episode cards and Live channel geometry may differ
when their content requires it, but typography, palette roles, corners, gutters and focus treatment
remain shared. Record that functional variant in its task; do not independently redesign it.

Card, footer and focus edge must fit the measured usable viewport including notice/header and
left/bottom TV safe insets. Preserve accepted column/adaptive artwork policies, not a photographed
column count. Short windows may use the existing bounded artwork reduction or scroll.

## 5. TV navigation and adaptive behavior

Preserve the accepted R10 Movies single scroll owner and scoped BringIntoView policy.
Fully visible LEFT/RIGHT moves in any row cause no vertical movement or card resizing.
UP/DOWN reveals only the required amount; rapid repeats/reversal cancel obsolete targets.
Entry/return restoration is separate from ordinary navigation. Acquire focus only after the
real target is attached; no arbitrary sleeps, competing relocation or second settling owner.

The identity is common across phone portrait/landscape, tablet, foldable and TV 720p/1080p/4K.
Adapt to actual width/height, density, font scale, safe insets and resizing. Do not force identical
fixed dimensions across devices. Full captions, readable text and reachable controls take priority.
Review relevant source constraints; runtime claims cover only the surfaces actually exercised.

## 6. Details, tabs and downloads

Retain accepted compact hero, original artwork/title, truthful metadata and centered actions.
Fitting wide order is Watch/Resume at physical RIGHT, Favorite middle, Download LEFT.
All have equal compact outer height and vertically centered contents. The accepted
`movieActionHeightDp` floor is 46dp normally, 42dp for compact-height non-TV; it is not a cap on
larger text. Narrow layout measures full captions: Watch first/full width, secondary pair when
it fits, otherwise stacked. Do not reintroduce oversized controls or blank telemetry space.

Inline Resume retains actual saved position/progress, ivory wording and gold clock.
Movies tabs remain `القصة` / `معلومات الفلم` / `افلام مشابهة`, all ivory with an ivory
selected underline. Information labels are gold, real values ivory. Selecting a tab reveals its
usable section and preserves the page's scrolling owner. Series retains its own real tabs,
season selection and episodes; do not globally rename Movie copy.

One permanent download control changes with observed state:

| State | Caption |
| --- | --- |
| No job | تحميل الفلم |
| QUEUED | في انتظار التحميل |
| CHECKING | جاري تجهيز التحميل |
| DOWNLOADING | جاري التحميل |
| PAUSED | استئناف التحميل |
| WAITING_SCHEDULE | في انتظار الموعد |
| WAITING_NETWORK | في انتظار الشبكة |
| WAITING_STORAGE | في انتظار المساحة |
| FAILED | اعادة التحميل |
| COMPLETED | تم التحميل |

The active source is `movieDownloadStatusCaption` / `movieDownloadControlCaption`, not the older
`movieDownloadActionLabel` helper. Idle has no reserved telemetry line/track. Active known-total
percentage is compact inline; the thin track does not grow the button. Unknown totals do not
produce fake 0%, total or ETA. Speed/bytes/ETA belong in the centered management dialog.
Opening management never mutates a job. Existing valid Pause/Resume/Retry/Cancel callbacks run
once and restore the download trigger. Preserve scheduler, storage and persistence ownership.

## 7. Resume and errors

Resume is one centered bounded dark dialog containing History, `اكمل المشاهدة`, real title,
`توقفت عند` plus saved time and truthful progress when duration is known. Measure complete
captions: fitting three-action row, otherwise Resume above a secondary pair/stack as accepted.
Initial focus is Resume; the directional graph is closed on all actions.

While a Resume decision is pending, header, timeline, transport and background input are hidden
or ineligible. BACK exits on the FIRST press through one existing owner; it must not merely clear
focus. OK executes only the focused action once. Held/repeated BACK must not navigate twice.
Cancel exits without a player-chrome flash and restores the originating card/context.

R27 preservation is mandatory: preparation, readiness, periodic save, lifecycle/disposal and
late callbacks must not overwrite or remove saved position/duration while the choice is pending
or after cancellation. Only explicit Resume/From Beginning permits the corresponding persistence.
Check immediate cancellation and cancellation after readiness/several save ticks.

Errors use one dark bounded gold/ivory family, correct status glyph and reachable Retry/Back.
Catalog/Details notices are centered within their intended slot. Cached cards stay usable with
category, item and scroll identity preserved; without cache show one in-content empty/error state.
No old pink banner, duplicate toast/card, or simultaneous Resume/error.

| Remote playback state | Accepted copy |
| --- | --- |
| Internet lost during started playback | انقطع اتصال الانترنت / سيعود التشغيل تلقائيا عند عودة الاتصال / مكان توقفك محفوظ |
| Offline entry with pending Resume | لا يوجد اتصال بالانترنت / اتصل بالانترنت لاكمال المشاهدة / توقفت عند + saved time |

Use actual connectivity/failure classification. Do not call a server/media failure “offline”.
Retry uses the correct catalog/details/player owner. Offline retry stays stable; reconnection
returns an undecided Resume to the decision without autoplay. Preserve paused intent, request
cancellation, stale-result protection and valid local/downloaded-media exemption.

## 8. Player, timeline, More and time picker

The accepted Movies player is the implementation baseline for future shared players:
dominant video, restrained gradients, original top-bar assets/context, thin timeline and compact
icon-above-caption tools. No tall outlined dock or oversized play control.

Physical LEFT-to-RIGHT VOD slots are always:
`المزيد → رجوع 10 ث → تشغيل / ايقاف مؤقت → تقديم 10 ث → المفضلة`.
Retain that order and stable slots on touch and TV. Focus/selection/caption changes, timeline
movement and hide/show must not reflow the tools. Preserve measured fit/spacing and navigation
inset/TV safe-area ownership from `VodCompactControlStrip` and its policy helpers.

Timeline is physical LTR: start/past LEFT, end/future RIGHT. Left rewinds, right advances;
signed seek and media-key ownership remain unchanged and clamped to actual duration.
During a drag/remote target, show the accepted circle/thumb and real track state.

**R25 supersedes preview boards and all earlier preview instructions: no seek image preview,
floating timestamp bubble, preview pointer/card or Movies decoder/warm-up session on phone/TV.**
Keep normal elapsed/remaining labels. Internal seek intent is not a second saved playback position.
Legacy preview code remaining for other callers is not authority to reactivate Movies previews;
Series alignment is a later bounded task.

| VOD More row | Required arrangement |
| --- | --- |
| الانتقال الى وقت | Clock + label right, opening ChevronLeft at far left |
| السرعة | Speed glyph + label right, real value and chevron left |
| حجم الصورة | Picture glyph + label right, real value and chevron left |
| من البداية | Label right, one restart glyph left |
| قفل التحكم | Label right, one lock glyph left |

Speed options retain actual supported 0.75x, 1x, 1.25x, 1.5x and 2x; selection/check differs from focus.
Fit/Zoom/Fill use distinct glyphs and existing mode mappings. More is bounded above its trigger
inside the safe window; constrain/scroll its body when needed. Panels own navigation exclusively.
Child BACK returns to its originating More row; closing More restores its trigger through existing
touch/TV policy. Background seek/play/channel actions cannot run behind a panel.

**R26 time entry:** physical-LTR HH:MM:SS with non-editable values and up/down arrows for every
unit. Remote UP/DOWN changes the focused unit; phone taps the arrows. No TextField/editable
semantics, soft keyboard, cursor or IME entry. Use actual duration bounds, valid unit stepping,
disabled unknown-duration path and complete `انتقال` / `رجوع` actions. Cancel does not seek;
confirm commits once. BACK restores the Go-to-time More row.

## 9. Section-specific variants

| Section | Shared appearance | Functions that remain specific |
| --- | --- | --- |
| Movies | Accepted catalog, Details, player, Resume, error and download baseline | Film identity, film progress/favorite/end behavior |
| Series | Same palette/type/icons/state language, catalog family and five VOD slots | Actual seasons/episodes, episode progress, authoritative neighbors and existing countdown/cancel/autoplay |
| Live | Same visual identity, row anatomy, error/focus language | Actual channel next/previous/last/list and mute/reload/source/live recovery |

Series previous/next may be direct More actions only when authoritative neighbors/callbacks
exist. Do not restore Version 2's seven-tool Series strip. Preserve stable season/episode identity,
selected season, resume per episode, series favorite identity and once-only transitions.
No episode changes behind Resume/error/panels/lock/end decisions or duplicate UI countdown.

Live retains existing channel-control order and callbacks until its later authorized alignment.
Show Source only when real alternatives exist. Non-seekable Live does not gain VOD duration,
Resume, Go to time or a fabricated timeline. Current legacy Live appearance is not proof of
completed migration. Future adoption order is Series then Live, under separate task scope.

## 10. Component reuse and change control

Reuse accepted theme/components rather than copying colors, vectors and focus styles into each
section. Resolve the active router/caller, not a dead fallback/helper. A necessary shared extension
must preserve unrelated callers' defaults; no broad refactor is authorized by this document.

An implementation packet identifies component, accepted source reference, target section,
relevant states and protected neighbors. A functional variant changes only what that function
requires. Changing an accepted shared role requires an explicit owner decision and updates this
contract/acceptance record; do not silently revise Movies to accommodate Series.

Protected regression conditions include complete captions, fixed card/footer geometry, smooth
R10 navigation, category selection, one compact download control, centered dialogs, exclusive
foreground/input, first-press Back, saved history on cancelled Resume, stable mobile tools,
no preview/bubble and keyboard-free time entry.

## 11. Evidence and source map

Evidence follows `../../android-engineering-lab/DEEPSEEK-EVIDENCE-CONTRACT.md`. Each later task
records normal/focused/selected/disabled and loading/empty/error/content states as applicable,
with before/reference/after source expressions and actual candidate/APK/device evidence.
Build/source checks are not visual acceptance. Capture remains OFF unless currently authorized;
owner review is separate and tied to exact installed candidate/scope.

Primary source owners, relative to repository:
- `app/src/main/java/sa/hulksa/player/ui/theme/HulkTheme.kt`
- `app/src/main/java/sa/hulksa/player/ui/components/GoldFocusEdge.kt`
- `app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt`
- `app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt` and `TvCatalogGrid.kt`
- `MoviesApprovedUi.kt`, `DetailsProScreens.kt` and `DetailsProTvPolishScreens.kt` in that screens directory
- `PlayerScreen.kt`, `VodPlayerControlsPolicy.kt` and `MovieTransportIcons.kt`
- `PlayerProEpisodeNavigation.kt` and `LivePlayerControlsPolicy.kt` for specific owners

Read the active source and live contracts. Old mockups and handoff snapshots illustrate history;
they do not override the accepted Movies implementation and later owner decisions recorded here.
