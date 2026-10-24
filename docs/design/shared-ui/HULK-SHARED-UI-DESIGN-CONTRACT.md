# HULK SA Android — Shared UI Design Contract

Version 4 · Movies foundation retained; Live categories adopted by the owner on 2026-10-08.

This is the single shared UI specification for future section work. It preserves the accepted
Movies result; Series and Live inherit its visual language while keeping their real functions.
Category selectors and Category Management use the later Live reference in section 4;
that scoped supersession does not replace the accepted Movies cards, Details or player.
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
the owner's later category-only alignment is an explicit exception, not a whole-section restart.
Its acceptance does not certify every device/window or production release.

The category reference is the merged Live work in PR
[#337](https://github.com/azoozrm/-HULK-SA-Android/pull/337):

- Reviewed source: `dfe18e8985b30fc10a710951814c6689d583097c` (R9).
- Merge: `19f857534323e6a6688daa27f33d2a15d684883c`.
- Owner direction on 2026-10-08: adopt Live Category Management for ordering/hiding and
  the Live category-strip appearance when returning to Movies, then Series.
- The reference is `LiveCategoryBar`, `rememberLiveCategoryStripMetrics`, `LiveCategoryChip`
  and the reorder-enabled, `liveStyle = true` `CategoryManagerDialog` / `LiveCategoryManagerRow`
  in `MainShellScreen.kt`, with the committed state in `LiveCategoryVisibilityStore.kt`.
- Technical source/artifact/CI review and bounded Mi Box 4 / Galaxy A06 evidence do not
  establish universal window coverage or complete migration of every Live player surface.

These are immutable reference identities. Resolve the active caller at the task's verified
live revision; do not reset source or install a historical reference APK.

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
| Category manager hidden row | Dark surface, muted label/VisibilityOff; remains manageable and reorderable |
| Category manager moving row | Restrained gold backing/drag indication; draft movement is separate from focus and visibility |

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
do not add hidden icon-box spacing or apply it to every unrelated component. Live fixed category
buttons retain that 6dp internal gap; server chips and spacing between chips use their own
accepted expressions in section 4.1.

## 4. Catalog, categories and cards

Keep the accepted toolbar hierarchy: real section title/count, Search, Refresh and Category
Management, with its existing type, shapes and content gutters. Category appearance and
management follow the later Live reference below. Category width follows real wording.
Physical RTL order is All, Favorites, Recent, then actual ordered server categories.
Movies labels include `الافلام`, `الكل`, `المفضلة`, `اخر مشاهدة` and `ادارة الفئات`.

Preserve category selection separately from focus, account/profile-scoped state, stable server
identity, search, restoration and management-trigger return. Selectors are selection-only;
long-press reorder belongs exclusively to Category Management. Notices stay within the content
gutter and do not cover the navigation rail.

### 4.1 Category strip and selector appearance — Live reference

Use the accepted Live strip on phone and TV: `LiveCategoryBar`, its fixed-category `FocusButton`
call sites and its actual `rememberLiveCategoryStripMetrics` / `LiveCategoryChip` expressions.
Match each component's compact height, padding, corner radius and text/icon roles; real server
chips use the framed original HS badge. All, Favorites, Recent and server chips keep compatible
outer heights for the same window; focus/selection never enlarges or shifts a chip. Replace the
competing Movies-only category skin. Other Movies components retain their accepted appearance.

| Accepted Live spacing | Source expression |
| --- | --- |
| Fixed button label to its simple icon | `FocusButton`: 6dp |
| Server chip label to its framed HS badge | `LiveCategoryChip`: 7dp |
| Between category chips | `LiveCategoryBar`: 7dp |

These are separate roles, not one global gap. Preserve the respective source expressions and
adaptive outer-height calculation; do not substitute photo measurements or resize other controls.

| Category state | Appearance/input |
| --- | --- |
| Unselected | Accepted Live dark surface, ivory label; semantic star/clock and HS badge in their accepted gold roles |
| Selected | Persistent gold fill and dark label/simple icon; HS badge keeps its legible dark inset |
| Focused | Accepted pale edge inside existing bounds; no scale and no implicit selection |
| Hidden server category | Absent from selectors; still present in Category Management |

Keep complete server wording and distinguishing suffixes. Use content-dependent chip widths and
the existing scrolling/adaptive viewport; do not force equal widths, shrink text or add ellipsis
to fit an invented fixed width. Preserve physical RTL anatomy: simple caption RIGHT with its single
glyph LEFT, server HS badge RIGHT with its caption following LEFT. Keep each section's real fixed
shortcuts and their route semantics; reuse appearance, not Live category IDs or channel callbacks.

The Live main strip and channel browser read the same committed order/hidden set. A vertical
browser retains its functional list geometry while sharing category anatomy/state; this does not
turn it into a horizontal strip. No selector or browser may enter reorder mode, mutate order or
show reorder instructions after a long press. Channel/media-item long-press favorites remain
separate existing behavior.

### 4.2 Category Management — sole ordering and hide/show surface

Use the accepted Live manager composition: centered bounded dark dialog, fixed title/scope line,
one scrolling category list and the fixed centered `تم` footer. Adapt within actual safe window
constraints; keep header/footer and all controls reachable at relevant font scales and sizes.
Scope wording describes the actual affected section/consumers; do not copy channel-specific text
into Movies or Series.

Each real server category has one row: full name at physical RIGHT, visibility glyph and drag handle
at physical LEFT. Preserve the Live row's dark/gold/ivory treatment and in-bounds focus edge.
Hidden rows use muted wording and VisibilityOff; visible rows use Visibility. Keep `مخفية` /
`ظاهرة` state semantics and meaningful hide/show activation. Do not add separate focus stops for
the eye and handle or competing pointer/click handlers. Names may wrap in the manager; do not
silently truncate them. All/Favorites/Recent and other synthetic fixed shortcuts stay outside
server-category ordering/hiding.

| Input/state | Required behavior |
| --- | --- |
| TV short OK / phone ordinary tap | Toggle hide/show once through the section's existing state owner |
| TV long OK | Enter a draft move for that stable category; its held/release sequence does not also hide/show or save |
| TV moving UP/DOWN | Move the draft within the list bounds and keep that same category revealed/focused |
| TV subsequent OK | Commit the order once and leave move mode; no extra visibility toggle |
| TV BACK while moving | Cancel the draft, keep the manager open and consume that whole BACK sequence; a later ordinary BACK closes |
| Phone long press then continuous drag | Move the draft under the finger; bounded edge scrolling follows current laid-out row geometry |
| Phone valid unconsumed drop | Commit once; consume completion so a tap/hide action cannot follow |
| Pointer cancellation/consumed release, disposal or obsolete scope/catalog | Discard the draft; no order save from that cancelled move |

An ordinary swipe before long-press belongs to list scrolling. Focus never toggles visibility.
Hide/show commits independently of order drafting: cancelling a later reorder does not undo an
earlier intentional visibility toggle. Closing/`تم` must not implicitly save an unfinished move.
Modal input remains exclusive and ordinary closure restores the management trigger/context.

### 4.3 State ownership, adoption and regression boundary

Keep one committed order/hidden-state owner per section and account/profile scope; every selector
in that section consumes it. Movies, Series and Live retain independent category namespaces,
catalog IDs, favorites/history and business callbacks. Sharing a visual component is not sharing
their persisted data or copying Live preferences into another section.

Persist stable real category IDs, not positions or names. Hidden categories remain in management
and keep their order; show restores their committed position. Unknown/unavailable IDs cannot
produce phantom rows, duplicates or resets; genuinely new categories append in catalog order.
If a selected category becomes hidden/unavailable, use the existing deterministic selection/focus
fallback. Hiding a category does not delete media, favorites or history.

Bind manager writes to the scope it opened for; stale callbacks may not write into a different
account/profile. Preserve existing customization and verified legacy adoption/migration rules.
Resolve each destination's live persistence owners before a bounded implementation; do not reset
data, introduce a second mutable order or perform a cross-section migration to match appearance.

This contract update changes documentation only. At the reference revision Movies still has its
earlier strip reorder and visibility-only manager; Series also needs a separately authorized
alignment. The owner-directed next work is Movies categories, then Series Category Management,
without reopening their unrelated accepted layout or player work.

Applicable later regression evidence covers normal hide/show, hidden-row reorder, commit versus
cancel, consumed-release cancellation, scrolling versus dragging, close/reopen persistence,
selected-category fallback, focus/trigger restoration, refresh/new categories and account/profile
isolation. Verify main-selector/browser agreement where both exist, and no reorder mutation from
any selector. Technical checks and owner visual acceptance retain their distinct evidence scopes.

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
| Live | Category-strip/manager reference in section 4; same shared visual identity and error/focus language | Actual channel next/previous/last/list and mute/reload/source/live recovery |

Series previous/next may be direct More actions only when authoritative neighbors/callbacks
exist. Do not restore Version 2's seven-tool Series strip. Preserve stable season/episode identity,
selected season, resume per episode, series favorite identity and once-only transitions.
No episode changes behind Resume/error/panels/lock/end decisions or duplicate UI countdown.

Live retains its existing channel-control order and callbacks outside the exact adopted scope.
Show Source only when real alternatives exist. Non-seekable Live does not gain VOD duration,
Resume, Go to time or a fabricated timeline. Current legacy Live appearance is not proof of
completed migration of every Live player surface. PR #337 supplies the bounded Live reference;
the latest owner-directed category alignment order is Movies then Series. Further player/section
work follows its own current task scope, not an automatic sequence imposed by this document.

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
no preview/bubble and keyboard-free time entry. Categories additionally preserve manager-only
reorder, cancellation without order save, hide/show independence, stable scoped IDs, selector
agreement and management-trigger restoration.

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
- `LiveCategoryVisibilityStore.kt`, `MovieCategoryVisibilityStore.kt` and `LiveChannelBrowser.kt`
  for category state/consumers; section 4's named `MainShellScreen.kt` components own the reference
- `PlayerScreen.kt`, `VodPlayerControlsPolicy.kt` and `MovieTransportIcons.kt`
- `PlayerProEpisodeNavigation.kt` and `LivePlayerControlsPolicy.kt` for specific owners

Read the active source and live contracts. Old mockups and handoff snapshots illustrate history;
they do not override the accepted Movies implementation and later owner decisions recorded here.

## 12. Home section adoption record

The owner accepted the Home model `home-design-20261009-r01`
(`HULK-Home-Design-20261009.zip`, SHA-256
`2a91f9c8a164d3761e52e0b58bb9b221f5f5ea4d65ea30da5ba61a6a96eb9a7d`) and directed its Android
implementation (`home-ui-alignment-20261010`). The adopted Home presentation reuses this
contract's foundations and adds no competing owners:

- Hero keeps the original backdrop/gradient, the real content title, plot, genre and available
  technical metadata, the physical right-aligned Arabic composition, the gold primary action on
  the existing `onOpen` route at physical right and the honest favorite state to its left. Focus
  keeps stable geometry (`scaleOnFocus = false`) with the shared edge; no focus scale, clipping or
  neighbor movement.
- Home VOD rows use `MoviesCatalogBoxedCard` / `SeriesCatalogBoxedCard` with their real footers;
  Continue Watching uses the shared `BoxedHistoryCard` with the real HistoryEntry identity, saved
  time, played fraction and existing long-press removal; live channels use a contained 16:9 logo
  card with the real channel name and `بث مباشر`, and never a VOD duration/rating/resume footer.
- One Home message zone directly below the hero presents the eligible renewal
  (`GrowthPolicy.evaluateRenewalBanner`), the eligible Operations announcement and the current
  OPTIONAL update decision. The existing automatic overlays and status banner stay unchanged
  outside Home and before Home is ready; they are suppressed only while the active Home zone owns
  the same message id/version, and an explicit card action opens that message's existing complete
  detail. No second policy evaluator, queue, downloader or persistence owner is created.

Implementation status: local Android candidate under owner visual review — **PENDING**. Model
content, titles, ratings, badges, expiry and download values in the reference boards remain
illustrative and were not copied into production data.

### 12.1 Owner correction — Home hero details formatting (2026-10-10)

The owner reviewed the R02 Android Home and corrected only the hero presentation; the rest of
section 12 remains valid. The R02 eyebrow/facts/action presentation is superseded as follows:

- The `مختار لك` eyebrow is removed from the Home hero on TV and phone (no replacement label and
  no reserved empty line).
- Home hero facts delegate to the accepted Details renderers
  (`MovieDetailsHeroMetadataRow` / `SeriesDetailsHeroMetadataRow` and their shared
  `DetailsHeroMetadataRow` entry formatting): MOVIE `year | genre | duration (clock) | quality |
  rating (star)` and SERIES `genre | quality | season count | episode count | rating (star)`,
  physical right-to-left, with the Details `|` separator, typography and quality chip. A scoped
  `wrap = true` option lets Home flow complete field groups on narrow windows; the default
  `wrap = false` keeps every existing Details caller's centered single-line appearance unchanged.
  `detailsTvDuration` / `detailsTvRating` become `internal` for reuse; their output is unchanged.
- Both Home hero actions use the accepted compact Details atoms: `compact = true`,
  `scaleOnFocus = false`, 13sp bold caption, 17dp icon, 6dp gap, 12dp/9dp padding, shared 12dp
  shape, outlined gold-heart favorite, measured captions with the existing
  `movieActionHeightDp` floor (46dp TV/normal, 42dp compact-height non-TV) and the existing
  measured narrow-layout stacked fallback. Primary remains physical right with PlayArrow at its
  physical left; `قائمتي` / `في قائمتي` labels and callbacks are preserved.

R03 Android review remains **PENDING**. This is a bounded Home hero correction; no accepted
Details/player/section history is changed.

### 12.2 Owner Model 1 hero + notification/message alignment (2026-10-10)

Owner decision 2026-10-10 20:25 Asia/Riyadh («لا خلنا على 1 ممتاز...») selected the full-bleed
Model 1 hero and authorized aligning the Notification Center and every Home-visible error,
update, announcement and renewal surface to the shared dark/gold family. Recorded scope:

- Hero Model 1: full-bleed backdrop, visual weight at physical LEFT, right-side dark
  gradient/copy; the copy block rebalances to the physical-right half of the wide composition
  (`Alignment.CenterStart`, 0.5 width) while phone/short windows keep the lower right-aligned
  copy. Existing artwork/fallback/rotation and the R03 Details facts, `|` separators, quality
  chip, icon-left placement, measured compact actions and stable focus size are retained.
- Notification Center: the shared `NotificationActionButton` uses the accepted compact atoms
  (13sp bold, 12dp/9dp padding, 12dp shape, in-bounds pale focus edge, `movieActionHeightDp`
  floor) with the gold primary / dark outlined secondary roles; TV rows keep poster right,
  text middle, compact action column left and the existing `NotificationTvFocusGraph`; the
  empty state gains the small gold bell + ivory wording; a real notification-opening failure
  renders as a scoped in-app notice instead of an unstyled Toast. Data, order, profile
  isolation, callbacks, bring-into-view and acknowledgement semantics are unchanged.
- Home messages: cards render the semantic gold icon physically LEFT of the compact wording on
  the shared dark notice surface; announcement severity stays in the theme gold family (no
  orange skins). GrowthQrDialog keeps its dark/gold surface, QR white quiet zone and compact
  Back floor. Operations REQUIRED/Maintenance/Optional/announcement surfaces reuse the same
  compact action atoms and honest update progress (unknown stays indeterminate), with the
  shared danger role for failure text. All policy, gating, IDs/version suppression,
  downloader/installer and renewal/acknowledgement ownership are preserved.
- Home no-content/error: the generic pink `ErrorNotice` call is replaced by the accepted
  `MoviesErrorNotice` / `MoviesOfflineEmptyState` family with real connectivity classification
  and existing Home refresh.

R04 Android acceptance remains **PENDING**; this record is a bounded Home/notification/message
adoption, not a whole-app redesign.

### 12.3 R05 owner hero correction — Model 1 composition rebuilt (2026-10-10)

The owner rejected the R04 hero («الهيرو ماصار نفس الي صممناه ياخي») while keeping the Model 1
direction. Section 12.2's hero-composition wording is superseded by this bounded correction;
12.2's notification/message/error alignment remains recorded and R04-review PENDING.

- Wide hero: rebuilt as one seamless cinematic band with the copy stack anchored in the lower
  band of the hero (CTA end ~40dp above the hero bottom) growing upward on the physical right at
  ~46% width; the fixed 410dp TV height is replaced by a viewport-derived height
  (`screenHeightDp * 2/3` coerced to 350–420dp). The old three-entry horizontal brush is replaced
  by an explicit physical left-to-right mask (0.08 at 0.00–0.30, 0.20 at 0.40, 0.65 at 0.48,
  0.94 at 0.54, 1.00 from 0.68) plus the separate bottom fade; phone keeps a vertical mask and
  its previous heights.
- Artwork: the hero now uses ordered real candidates (owned metadata backdrop first, then the
  item backdrop/poster fields) classified by decoded dimensions — only aspect >= 1.3 fills the
  wide hero with Crop biased to the physical left; portrait/near-square sources fall back to a
  Fit rendering bounded inside the left visual region, and an exhausted list keeps the original
  brand mark. Nothing is painted wide before the dimensions are known.
- Real plot/genre/backdrop reuse the existing bounded card-metadata payloads
  (`get_vod_info`/`get_series_info`) through the owner-keyed store: the active hero may trigger
  one marked presentation backfill with the existing cooldown/in-flight/owner protection, and the
  hero hoists that single load for both artwork and the accepted R03 Detail facts/actions. Row
  cards, completeness rules, policy/gating, navigation and all R04 notification/message/error
  surfaces are unchanged.

R05 Android visual acceptance remains **OWNER REVIEW PENDING**; this record does not label the
technical layout a Model 1 visual PASS.

### 12.4 R06 technical corrections — RTL artwork, retained fallback, fetch settling, uniform phone window (2026-10-10)

The coordinator's independent R05 review found three source defects, and the owner supplied a
phone-artwork sizing instruction. Section 12.3's artwork alignment/settling/phone-fallback
wording is superseded by this bounded correction; the R05 TV Model 1 composition, real metadata
parsing, R03 atoms and all R04 surfaces are preserved. R05/R06 Android acceptance remains
**OWNER REVIEW PENDING**; nothing here is a Model 1 visual PASS.

- Physical-left artwork alignment: hero artwork now uses
  `heroArtworkPhysicalLeftAlignment = AbsoluteAlignment.CenterLeft` for the brand fallback, the
  TV portrait/near-square Fit container and the wide Crop bias. Absolute alignment is
  direction-independent, so the app's RTL layout can no longer mirror the artwork into the dark
  copy region. RTL copy, physical-LTR mask and icon placement are unchanged.
- Retained fallback: the hero artwork classifier is a pure reducer
  (`HeroArtworkSelectionState`, `heroArtworkOnLoaded`, `heroArtworkOnFailed`). The first
  successfully decoded portrait/near-square source is retained while later candidates are tried,
  a successful landscape still wins, and the retained real image is rendered when later
  candidates fail (the brand mark only when no real image decoded). The classification pass
  reuses its successfully decoded painter for display (no second request, no separate render
  failure path), ignores superseded callbacks and keeps hidden/safe classification.
- Presentation settling: `MovieCardMetadataClient` / `SeriesCardMetadataClient` now report
  `succeeded` for a valid parsed payload, and the stores settle presentation only through the
  shared `presentationSettlesAfterFetch` policy (marker `presentation_settled_v2`). A failed,
  canceled, challenged, malformed or oversized attempt keeps cached facts/art, stays
  non-blocking and remains eligible for a later natural bounded retry after the existing
  cooldown; a legacy R05 marker alone no longer settles, so the selected hero can revalidate a
  genuinely absent presentation once. Technical completeness, cooldowns, series in-flight
  sharing, owner keying and stale-owner rejection are unchanged.
- Owner phone instruction (2026-10-10): «حتى الجوال الهيرو الخلفيه تظهر كبيره ومقربه مره وبعضها
  تجي صغار خليها نفس المقاس». Portrait phones now render every successful artwork source into
  one uniform 16:9 window (full Home-content width, height derived from that width, placed below
  the measured header/safe top inset, aspect-preserving centered Crop, lower edge blended by the
  existing vertical mask). The R05 0.56-width Fit side-poster phone presentation is removed;
  loading, retained portrait, failure and brand all stay inside that reserved window. TV keeps
  the physical-left bounded Fit portrait fallback. Other adaptive windows keep their content
  fit; no zoom, scale, stretch, source-dependent sizing or tall TV container on phones.

## 13. Login / Who's Watching / entry-subflows adoption record (2026-10-11)

The owner accepted the R03 Login model (`HULK-SA-LOGIN-PROFILES-DESIGN-R03.zip`, SHA-256
`0fba8e5f03289759338ac5f4fbf2694035a185b489f3aa2664682926c9c36bca`) and directed its Android
implementation (`login-profiles-ui-alignment-20261011`, LOCAL_ROUND R03). This record adds the
section's adopted variant to the shared contract; it does not close or rewrite the accepted
Movies/Series/Live/Home records, and it grants no merge, signing, tag, release or capture
authority.

### 13.1 Login — adopted centered composition

- Login uses ONE bounded credential form centered horizontally and vertically in the safe
  viewport on TV, phone portrait/landscape, tablet and foldable. `resolveLoginLayoutPolicy`'s
  former TV/roomy `PREMIUM_SPLIT` brand column, logo node and reserved split/logo space are
  superseded for Login only; the original HS logo assets stay unchanged for their other callers.
- The form max width is the source expression `(width * .41f).coerceIn(420.dp, 520.dp)` on TV,
  `560.dp` for non-TV width at least 600dp, `480.dp` otherwise, always clamped to the real safe
  available width after the existing page/safe insets and applied as max-width before
  `fillMaxWidth` in a loose bounded parent. One bounded scroll owner keeps fields, checks, status
  and actions reachable under IME/short height/large text.
- The form surface is matte near-black with a restrained shape and NO external elevation or
  drop shadow. Decorative background: the bundled original poster walls
  `login_poster_wall_tv.webp` (1920x1080, sha `bbe95ec6…`) for landscape/TV and
  `login_poster_wall_phone.webp` (1080x2400, sha `1acedbde…`) for portrait, drawn static, local,
  non-focusable, aspect-preserving `Crop` with no semantics, no repeats, no added shadow/glow and
  no network load. Earlier Login backdrops (gold glow, geometry/neon, single city image,
  illustrated collages, sports imagery, split brand panel) are superseded.
- Exact copy: `تسجيل الدخول`, `ادخل بيانات اشتراكك للمتابعة`, `كود الدخول`, `اسم المستخدم`,
  `كلمة المرور`, `تذكر الحساب`, `اظهار كلمة المرور`, `اشتراك جديد`. Field order keeps the auth
  contract (access code, username, password).
- Scoped Login physical placement (this section only): each field's single existing key/person/lock
  glyph sits physically RIGHT, before the Arabic wording (key warm gold; person/lock source ivory).
  Each checkbox square sits physically RIGHT of its own caption; the remember group is at physical
  LEFT and the show-password group at physical RIGHT when both fit, wrapping whole groups when they
  do not. These are Login exceptions to the shared icon-left action rule, not global reversals.
- Actions fill only the bounded form content width: primary gold Login above secondary dark
  `اشتراك جديد`, complete captions, one existing glyph physically LEFT, compact shared heights with
  the movieActionHeightDp floor, in-bounds `goldFocusEdge`. TV/wide keeps the real Growth QR/URL
  routing and narrow keeps the real website callback; auth, focus, keyboard/IME and
  remember-account/password policies are unchanged.

### 13.2 Who's Watching / management / entry options

- Picker keeps the small original HS header mark above `من يشاهد الان ؟`, the real stable-ID TV
  card row with the existing `profilePickerTvMetrics` family (card 154/176/190dp, avatar
  82/96/106dp, gaps 14/20/24dp), truthful current/Kids/protected indications and MAX_PROFILES Add
  availability; phone keeps two complete columns only when the measured footprints fit, otherwise
  one, with one scroll owner. Original avatar family only; no seeded names/profiles.
- Management/editor and entry options reuse the accepted AccountIdentityDecisionDialog bound: max
  560dp expanded (TV or width at least 720dp) / 440dp compact, clamped to safe width, applied as
  max-width before fill. Option controls, type choices and Back actions keep natural compact
  widths; fields and real lists may fill the bounded parent. The older subordinate board's
  stretched switch rows and full-width Back are superseded.
- All subordinate Login/Picker/management/PIN/parental/QR/account-choice loading, empty,
  validation, error, lockout and confirmation states keep the shared dark/gold/ivory bounded
  notice/action family, existing once-only callbacks, truthful connectivity classification and
  first-press Back/focus return.

Implementation status: owner instructed review-and-merge for this section on 2026-10-11
(«راجع وادمج»); the section publishes as one PR from `ui/login-profiles-ui-alignment-20261011`
(candidate `login-profiles-ui-alignment-20261011-r07`). Reference acceptance does not extend to
individually unexercised subordinate states, and optical/runtime limits remain as recorded in the
task's round RUNTIME-EVIDENCE files.

### 13.3 R04 owner corrections — scoped Login rules (2026-10-11)

These R04 corrections are scoped to Login and supersede only the conflicting R03 wording:

- Access-code glyph: the `كود الدخول` key uses exactly the same resolved neutral ivory icon-tint
  mapping as the `اسم المستخدم` person and `كلمة المرور` lock glyphs for normal/focus/disabled
  states. The R03 gold-key direction is superseded; no gold/goldBright/access-code-only override.
  Vector identity, 22dp size, physical RIGHT-before-wording placement and the field's own focus
  edge/cursor are unchanged. Person/lock colors are not altered to match the key.
- Caption-to-glyph gap: simple actions keep exactly ONE caption-to-glyph spacing owner
  (`EntryActionButton`'s single `Row(horizontalArrangement = Arrangement.spacedBy(6.dp))` with one
  17dp glyph/spinner slot). No duplicate Spacer, wrapper padding or second reserved slot; the
  loading replacement reuses that same single slot and adds no second gap or glyph.
- Busy primary rendering: while authentication is genuinely pending, the primary action keeps the
  theme gold fill at full opacity with the full-opacity `onPrimary` dark caption and spinner. The
  loading state is applied BEFORE the ordinary disabled treatment; only genuinely disabled
  non-loading actions use the existing dimmed surface. Eligibility/semantics/callback guards and
  the once-only submit remain unchanged, and completion/failure/cancellation restore the normal
  state through the real request lifecycle.
- Form surface: the Login form uses the opaque near-black matte gradient (alpha FF, same source
  hues) with no external elevation/drop shadow and no gold presentation frame.

### 13.4 R05 owner corrections — option anchoring, error copy, exact gap (2026-10-11)

- Login option row anchoring: when both full option groups fit one bounded form-content row, the
  complete `تذكر الحساب` group is anchored at the physical LEFT edge and the complete
  show-password group at the physical RIGHT edge (outer `Arrangement.SpaceBetween` over the full
  bounded width, or equivalent explicit physical slots). R05 supersedes any centered/relative-left
  interpretation. Inside each group the checkbox stays physically RIGHT of its own caption with the
  existing internal gap; checked state and callbacks are unchanged. When full captions do not fit
  (large font/IME/narrow), complete groups stack: show-password right-anchored in its own row,
  remember left-anchored in its own row, with reachable full captions and the stable focus graph.
- Error copy: app-authored error/validation text carries no terminal full stop or ellipsis. The
  scoped shared display formatter (`entryErrorDisplayText`) removes only trailing `.`/`…`/U+06D4
  sequences and trailing whitespace/directional marks for dynamic/server-provided error text;
  interior punctuation, decimals, domains, URLs and meaningful content stay unchanged, raw error
  identity/classification stays authoritative, and a punctuation-only result falls back to the
  existing truthful generic error wording. This supersedes the earlier "server text unchanged"
  wording only for trailing decoration of displayed error copy.
- Caption-to-glyph gap: simple actions use exactly ONE 6dp spacing owner (a single
  `Arrangement.spacedBy(6.dp)` or a single `Spacer(width = 6.dp)`, never both) with no second
  spacer, reserved leading-icon slot or icon-wrapper padding; the loading spinner reuses the same
  single slot. This concretizes the R04 open-ended "match New Subscription" wording for the Login
  action.

### 13.5 R06 owner corrections — one-space action gap, connection copy (2026-10-11)

- Login action gap: the visible caption-to-icon separation in `تسجيل الدخول` and `اشتراك جديد`
  is ONE ordinary U+0020 space, resolved from the live action caption font/style (13sp bold, theme
  IBM Plex Sans Arabic, current density/font scale) through the existing text measurement and used
  as the single spacing owner (`captionGlyphGap`). R06 supersedes R05's fixed 6dp for these two
  actions only; every other `EntryActionButton` caller keeps its default. No second spacer, literal
  edge space, reserved slot or wrapper padding; the loading spinner keeps the same single gap.
- Login connection copy: confirmed no-internet shows
  `لا يوجد اتصال بالانترنت ، تحقق من اتصالك وحاول مرة اخرى`; a service failure while availability
  is not disproved shows `تعذر الاتصال بخدمة HULK ، حاول مرة اخرى` (no period after HULK and no
  terminal punctuation). Confirmed offline is resolved from the existing authoritative
  `rememberUsableNetworkState` binding and applies only to the raw service-unavailable failure;
  validation, access-code and credential failures keep their own copy, and raw exception/status
  classification is unchanged. The confirmed-offline icon mapping is used only for confirmed
  offline; other failures keep the general error icon.

## 14. Favorites Model 01 adoption record (2026-10-11)

On 2026-10-11 22:15:46 Asia/Riyadh the owner selected Model 01 — Unified Grid after receiving the
three Favorites TV/phone model files (`نعم 1`). The accepted reference package is
`HULK-FAVORITES-MODEL-01-20261011.zip` (SHA-256
`89a267ac69d504e9faa5f0d0d09059ad074c3555ebed652b8aad1bd86d759a5f`). This record documents
**design-reference acceptance**. The Android implementation
(`favorites-ui-alignment-20261011`, LOCAL_ROUND R01) is under owner review — **PENDING**; no
Favorites merge, signing, tag or release is authorized or implied.

### 14.1 Adopted composition, naming supersession and reuse

- One unified adaptive grid. Title `المفضلة`, subtitle `كل ما حفظته في مكان واحد`, and the real
  current-projection count (`عنصر` for the mixed All projection, `فلم`/`مسلسل`/`قناة` for the real
  type filters). The compact existing Refresh action stays at physical left; no stretched pills,
  no focus scaling, no selection-driven size change.
- Compact type filters in physical RTL order `الكل` / `الافلام` / `المسلسلات` / `القنوات`, using
  the accepted Live fixed-category `FocusButton` anatomy and `rememberLiveCategoryStripMetrics`
  height/text expressions; selection is the committed gold fill and focus remains a separate
  in-bounds edge. Default is All; the selection and grid location persist through the existing
  per-account/profile navigation owner (`NavigationMemoryStore`), not a new store.
- Cards reuse `MoviesCatalogBoxedCard` / `SeriesCatalogBoxedCard` with their accepted square
  artwork, two reserved title lines, reserved footer slots and passive saved-heart badge, and the
  accepted Home live-channel variant (`LiveChannelHomeCard`) with contained 16:9 artwork, real
  name and `بث مباشر`; channels gain no VOD rating/duration/season/progress. Missing metadata stays
  absent in its reserved slot and no value is fabricated.
- Whole-Favorites states: `جاري تجهيز المفضلة` (initial unresolved loading, never a false empty),
  a compact observed refresh state over cached cards, `لم تضف اي محتوى الى المفضلة بعد`,
  `لا توجد افلام في المفضلة` / type-specific filtered-empty wording,
  `لا يوجد اتصال بالانترنت ، تحقق من اتصالك وحاول مرة اخرى` for confirmed offline, and
  `تعذر تحميل المفضلة ، حاول مرة اخرى` for a service/catalog failure; cached cards stay usable
  behind one bounded notice with the existing Retry owner. Connectivity classification uses the
  existing authoritative usable-network binding; a failed request alone is never labeled offline.
- Global app-authored naming supersession: every Favorites caption previously `قائمتي` is now
  `المفضلة` and `في قائمتي` is now `في المفضلة` — TV rail/table, mobile bottom navigation,
  smart-search destinations, the Details drawer, Home hero favorite action, Movie/Series/Kids
  Details actions and Favorites-context loading/empty/refresh wording. This supersedes only the
  literal caption strings quoted in section 12.1; the accepted Home hero geometry, measured width
  reservation, icon placement, compact action atoms and callbacks remain authoritative and
  unchanged. No server content name, enum/destination identity, persistence key/schema or stored
  favorite is renamed.
- Adaptive/input: actual usable constraints and the accepted catalog sizing expressions decide the
  column count (the mockup count is illustrative); compact phone portrait uses two columns when
  the accepted card geometry fits, otherwise one. One grid/scroll owner with stable `type:id`
  keys preserves details-return filter, item and scroll location. Removal focuses the next
  surviving stable key, then the previous, then the always-attached type selector; a removal
  long-press release cannot open the replacement card. Captures remain OFF; automated visual
  validation is NOT RUN — CAPTURE NOT AUTHORIZED until separately authorized.

### 14.2 R02 owner correction — no routine Favorites Refresh control (2026-10-11)

This supersedes only the conflicting Favorites wording in section 14.1 and the reference
requirement to display a permanent Favorites Refresh action:

- The Favorites page has NO routine Refresh control on TV or phone: the circular header Refresh
  action and its reserved header space are removed, and the whole-list empty state has no ordinary
  Refresh button. Other sections keep their existing controls.
- Reactive updates remain unchanged: Favorites still repaints from the existing state/catalog/
  favorite flows; no polling, timers, extra catalog requests or new state owner is introduced.
- Error-only recovery is preserved: the confirmed-offline / failure states and the cached-content
  error notice keep their existing Retry action, the only remaining refresh path on this page.
- With an empty projection the selected type filter and the navigation stay reachable; the
  last-removal focus fallback still returns focus to the always-attached type selector.

The R01 Favorites candidate review is **PENDING** and the R02 candidate
(`favorites-ui-alignment-20261011-r02`) supersedes it for all Favorites review scope. Captures
remain OFF; automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

## 15. Search Model 01 adoption and Home hero favorite caption correction (2026-10-12)

On 2026-10-12 01:48:18 Asia/Riyadh the owner selected Search Model 01 — Unified Grid and a
separate bounded Home hero correction. The accepted reference package is
`HULK-SEARCH-MODEL-01-20261012.zip` (SHA-256
`99de8ef20dd45200e054a1873a0a602a8961ca94e036ca076f5b593510f4105b`). This record documents
**design-reference acceptance**. The Android implementation
(`search-ui-alignment-20261012`, LOCAL_ROUND R01) is under owner review — **PENDING**; no Search
merge, signing, tag or release is authorized or implied. The active Search route is
`ProfileSmartSearchLayer.kt` (dispatched from `HulkApp.kt`); the unreachable legacy
`UnifiedSearchScreen` in `MainShellScreen.kt` is not the active owner.

### 15.1 Adopted Search composition

- One page owner (`ProfileSmartSearchLayer.kt`) with the compact real header title `البحث` and
  subtitle `ابحث في الافلام والمسلسلات والقنوات`, one compact editable input (hint
  `اكتب اسم فلم او مسلسل او قناة`, existing microphone entry and IME handling preserved, end
  action space reserved so text never runs under the microphone), compact type filters and one
  unified results grid. The existing TV rail and stable mobile navigation ownership are unchanged.
  See 15.4 for the owner's header/naming correction that supersedes the badge and the earlier
  `فيلم` wording.
- Physical RTL filter order is `الكل` / `الافلام` / `المسلسلات` / `القنوات`, using the accepted
  Live fixed-category `FocusButton` anatomy and `rememberLiveCategoryStripMetrics` height/text
  expressions, 7dp between chips, committed gold selection, a separate in-bounds focus edge and no
  focus scaling. The search filters reorder only this selector; they never touch the rail
  destination list order.
- Results heading `نتائج البحث`; any count shown is the real current filtered result count.
  Movies and Series reuse `MoviesCatalogBoxedCard` / `SeriesCatalogBoxedCard` (square artwork, two
  reserved title lines, truthful reserved footers); channels use `LiveChannelHomeCard` (contained
  16:9 logo, real name and `بث مباشر`, no VOD metadata). Card activation keeps the existing
  open/route callback and long-press keeps the observed favorite behavior.
- One grid/scroll owner with stable `type:id` keys. TV columns derive from
  `tvCatalogMetrics(boxedCards = true)` and `movieCatalogMinCellWidthDp`; compact phone portrait is
  capped at two accepted columns when both fit, otherwise one; short windows keep the scroll owner
  and the keyboard/IME reachability path.

### 15.2 History and subordinate states

- Blank query keeps the existing profile-scoped recent CONTENT history (same store, dedup,
  ordering, open/delete/clear callbacks). History cards adopt the accepted square-artwork/title/
  footer family with real stored title/type/year only, compact natural-width `فتح` / `حذف` and a
  compact `مسح الكل` shown only when history exists; no fake ContentItem or new persistence owner.
- The oversized empty rectangle is removed. Applicable states use one compact centered dark/gold
  notice family (`SmartSearchStateNotice`, surface `#11120D`, gold border/ glyph): no history
  `ابدا البحث` / `اكتب اسم فلم او مسلسل او قناة`; minimum length `اكتب حرفين على الاقل` /
  `تظهر النتائج مباشرة بعد كتابة حرفين`; pending `جاري البحث`; catalog resolving
  `جاري تحميل المحتوى`; no match `لا توجد نتائج مطابقة` / `جرب اسما اخر او غير نوع المحتوى`;
  filter-specific no match stays truthful to the selected type.
- Catalog failure/retry reuses `MoviesErrorNotice` / `MoviesOfflineEmptyState` with the existing
  authoritative usable-network binding and the existing refresh owner, and cached local results
  stay usable behind one bounded notice. A failed request alone is never labeled offline.
- Preserved intact: ranking, normalization, phonetic cross-script matching, the two-character
  threshold, debounce, cancellation, stale-result protection, profile/Kids isolation, history
  storage and voice callbacks. The activity-level voice status messages (listening/permission/
  unavailable/error) remain their existing transient system messages; converting them into the
  in-composition notice family would change activity-level voice ownership and is not part of
  R01. TV reaches input, microphone, filters, cards/history controls and the rail; Back and
  details-return restore the existing context.

### 15.3 Bounded Home hero correction

Source-confirmed cause: `HomeHeroActions` resolved
`favoriteCaption = if (isFavorite) "في المفضلة" else "المفضلة"`. For BOTH Movie and Series Home
hero on TV and non-TV the displayed caption is now the constant `المفضلة`; only the observed
`Favorite` / `FavoriteBorder` glyph changes with `isFavorite`. The accepted fixed action-width
reservation (the historical longest wording), height, spacing, physical placement, gold icon tint,
once-only callback, primary action, hero facts/artwork/rotation, focus edge and measured
narrow-layout fallback are preserved, so the action group never shrinks or reflows. Other
Details/player/card favorite captions are untouched.

### 15.4 R01 owner corrections — naming, header and catalog card sizing (2026-10-12)

The owner reviewed the R01 candidate and requested a bounded correction in the same LOCAL_ROUND.
This supersedes only the conflicting wording in 15.1/15.2 and the reference wording:

- Movie wording: app-authored Search copy uses the approved singular `فلم` everywhere (input
  hint, empty-state instruction, `ContentType.smartLabel()` movie branch, recent-content labels,
  counts and notices), matching `catalogCountUnit` / `favoritesCountUnit`. Exact input and
  empty-state instruction: `اكتب اسم فلم او مسلسل او قناة`. The reference board's `فيلم` wording
  is superseded; server titles and stored content are untouched.
- Header: the decorative circular magnifying-glass badge and its reserved spacing are removed. The
  Search header is the accepted text-only section header of `FavoritesScreen`
  (`MainShellScreen.kt`): title `البحث` at 27sp TV / `MOBILE_SECTION_TITLE_SIZE` phone, bold, with
  the muted subtitle at 11sp TV / `MOBILE_SECTION_COUNT_SIZE` phone, physical right alignment, the
  `TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP` start inset plus `tvPageSafeInsets` top inset on TV and
  the accepted phone section insets. Functional microphone and navigation Search icons stay.
- Filters: `SmartSearchFilterBar` adopts the accepted `FavoritesTypeFilterBar` anatomy — the same
  `rememberLiveCategoryStripMetrics` text/height expressions, `rememberCategorySidebarUnderlap`
  viewport/padding expressions, 7dp spacing, committed gold fill, separate in-bounds focus edge
  and stable dimensions — while keeping the Search-specific D-pad wiring.
- Catalog card sizing: the results grid and the recent-content cards use the exact accepted
  catalog expressions (`tvCatalogMetrics(boxedCards = true)`,
  `movieCatalogMinCellWidthDp`, `movieCatalogColumnCount`, the internal
  `FAVORITES_MOBILE_*` phone policy) through one shared Search layout. The previous extra parent
  horizontal padding around the grid was removed so the TV cell matches the Movies/Series catalog
  cell, and the previous Search-only miniature recent-card widths were replaced by the same
  computed catalog cell width. Single items keep the normal card size; TV short windows use the
  accepted `movieCompactArtworkHeightPx` artwork fallback with grid scrolling. Channels keep the
  approved `LiveChannelHomeCard` variant. No focus scaling, clipped footer or fabricated metadata.
- The Home hero correction in 15.3 is preserved unchanged.

Implementation status: LOCAL_ROUND R01 candidate (corrected) was reviewed by the owner and is
**REJECTED for the reported recent-content viewport/focus scope** (clipped recent section, card
sliding behind the heading, bottom actions hidden and TV focus trapped). R01's other corrections
remain valid. The R02 candidate (`search-ui-alignment-20261012-r02`, same LOCAL_ROUND) supersedes
it for that scope. Captures remain OFF.

### 15.5 R02 owner corrections — recent viewport and TV focus transfer (2026-10-12)

- Measured viewport: the TV safe insets are applied exactly once per region — the header/content
  block keeps the accepted top/start insets and now also the physical-left safe inset, the recent
  section applies the accepted bottom safe inset, and the results grid keeps its own accepted
  catalog bottom/end padding. The input border, history controls, "مسح الكل" and card focus edges
  stay inside the usable screen.
- One vertical owner: the complete recent body (heading, `مسح الكل` and cards) scrolls inside a
  single vertical scroll container instead of a LazyRow below a fixed heading. Automatic
  framework relocation is disabled for that body (`moviesNoBringIntoViewSpec`) and the accepted
  measured reveal (`focusedCardScrollCorrection`) reveals the complete card (artwork, title, type,
  buttons, padding, focus edge) whenever it fits; the existing approved
  `movieCompactArtworkHeightPx` short-window fallback shrinks only the artwork when the whole card
  cannot fit the viewport. Card width, typography and normal square artwork are unchanged — no
  miniature recent cards.
- TV focus transfer: from both `فتح` and `حذف`, one UP press reaches the attached selected type
  filter and the next UP reaches the search field. Directional events are consumed only when the
  requested focus transfer actually succeeded or the boundary is intentionally handled
  (`moveFocusTo` reports the real `requestFocus` result); LEFT/RIGHT stay stable. Obsolete reveal
  and navigation jobs (recent reveal, filter's bounded DOWN retry, results-grid navigation) are
  cancelled when focus leaves the content or the owner is disposed, so no automatic relocation
  can pull focus back to the bottom actions.
- All previous corrections are preserved: `فلم` naming, text-only section header, accepted filter
  styling, unified result grid, catalog card sizing, history/profile isolation and existing
  callbacks, and the Home hero constant `المفضلة` caption with heart-only state changes.

Implementation status: LOCAL_ROUND R02 candidate was reviewed by the owner and is **REJECTED**
for the reported Search defects (clipped/trapped recent section, missing/inoperative voice
search, and the last-card Delete LEFT fallback to Clear All). R02's accepted corrections remain
recorded. The R03 candidate (`search-ui-alignment-20261012-r03`, same LOCAL_ROUND) supersedes it.

### 15.6 R03 owner corrections — simplified page, working voice and exact D-pad boundaries (2026-10-12)

- Simplified page and raised cards: the recent-content heading `المحتوى الذي بحثت عنه مؤخرا`, its
  subtitle `المحتوى الذي تختاره يظهر هنا بدون تكرار`, the heading row, spacer and reserved height
  are removed completely and are not replaced by another heading. The type-filter toolbar is
  followed immediately by the catalog-sized history cards, so on initial entry, before any
  directional input, the complete first card (artwork, title, type and both actions) is visible.
  The compact `مسح الكل` control moves to the physical LEFT of the accepted filter toolbar whenever
  history exists, keeping its callback; its UP reaches the selected filter, DOWN the current
  content target, RIGHT the last filter chip and LEFT is a boundary.
- Working voice search: `currentVoiceSearchOwner()` bases voice ownership on the authenticated
  account/session/profile scope and the active Search destination; the notification UI readiness
  latch (which could remain false on a valid session) no longer disables voice. The microphone
  stays visible, focusable and clickable on TV and phone, TV OK activates it, and the existing
  permission, lifecycle, cancellation, request-gate and stale-owner protections are preserved.
  After activation, TV focus returns to the search field, so recognition/cancellation always
  leaves a valid focus target; transcripts still deliver through
  `updateSearchFromVoice`/`updateSearch` into the same query/results owners.
- Exact D-pad boundaries: the last history card's `حذف` no longer falls back to `مسح الكل`;
  RIGHT stays on the same card's `فتح`, LEFT moves only to the next card when it exists, and at
  the final card LEFT stays on `حذف`. From both card actions one UP reaches the selected type
  filter and the next UP reaches the search field. Moving between Open/Delete does not start
  competing reveal jobs (one reveal per focused card index); leaving the card cancels the obsolete
  relocation and keeps the single measured scroll/reveal owner with complete card visibility.
- Preserved: `فلم` naming, accepted text-only header and filter styling, catalog card sizing,
  history storage/profile isolation and callbacks, and the Home hero constant `المفضلة` caption
  with heart-only state changes.

Implementation status: LOCAL_ROUND R03 candidate was reviewed by the owner and is **REJECTED**
for the reported TV Search composition/reveal/navigation scope (full-width Search field in its own
row, oversized Open/Delete saved-content footers, incomplete second-row reveal and unreliable
upward navigation). The R04 candidate (`search-ui-alignment-20261012-r04`, same LOCAL_ROUND)
supersedes it; R03's working voice path and naming are preserved.

### 15.7 R04 owner corrections — TV Search as an accepted catalog section (2026-10-12)

- Top toolbar: Search adopts the accepted `CatalogHeader` hierarchy instead of a separate tall
  header. On TV the section title `البحث` and the truthful current count (results `نتيجة` when the
  query is eligible, saved-history `عنصر` otherwise) sit at the physical RIGHT in the accepted
  185dp title column with 27sp/10sp typography; the compact query field follows immediately beside
  it toward the physical LEFT using the accepted `Modifier.weight(1f).widthIn(max = 630.dp)`
  bounded field policy, with the existing functional microphone at the field's physical LEFT end.
  The large circular Search icon and the separate description row stay removed.
- Filters: the accepted Live fixed-category strip stays immediately below the toolbar in physical
  RTL order `الكل` / `الافلام` / `المسلسلات` / `القنوات`; the compact `مسح الكل` stays at its
  physical LEFT whenever the saved-history projection has removable entries and never deletes
  ordinary results. No permanent Refresh/Category Management controls are added; the existing
  error-only Retry is unchanged. The redundant full-width `نتائج البحث` grid heading is removed;
  the real result count now lives in the top header.
- One catalog body: both projections (saved history when the query is blank, ranked results when
  the existing eligibility/debounce contract permits) render in ONE vertical TV catalog grid on
  the accepted R10 single-scroll-owner policy — `tvCatalogMetrics(boxedCards = true)`,
  `movieCatalogMinCellWidthDp`, `movieCatalogColumnCount` producing a single `GridCells.Fixed`
  column count shared by layout and D-pad arithmetic, `moviesNoBringIntoViewSpec` scoped to the TV
  grid, and the measured `focusedCardScrollCorrection`/`revealFocusedGridItem` reveal including
  footer/actions, focus inset and TV bottom safe inset. The saved-content strip is no longer a
  horizontal `LazyRow` inside a scrolling Column. Search keeps its own memory row and never shares
  Movies/Series/Favorites navigation memory.
- Cards: results and saved history reuse `MoviesCatalogBoxedCard`/`SeriesCatalogBoxedCard` and the
  accepted contained-logo live variant, with the same square artwork, two reserved title lines,
  reserved footer slots and in-bounds focus edge. Saved history activates the card to open the
  content once and keeps its Delete action as a compact secondary control inside the uniformly
  reserved catalog footer (`footerAction`); the oversized separate Open/Delete footer row is
  removed. Deletion keeps its callback, confirmation behavior, storage ownership, select-release
  guard and surviving-item focus fallback; no new long-press meaning is introduced.
- D-pad and restoration: DOWN/LEFT/RIGHT use the RTL column order and full-card reveal; UP from
  the first row reaches the selected filter, then the query field. A partially composed card is
  never a completed reveal, movement is cancelled when direction reverses or the projection
  changes, and there are no settle sleeps or second scroll owners. The terminal Delete control
  keeps focus on LEFT (no Clear All fallback). Details return restores the same surviving
  item/filter/location through the existing per-profile navigation owner; deleting the last item
  falls back to the always-attached selected filter.
- Adaptive/state: when the toolbar genuinely cannot fit (very narrow non-TV windows) the compact
  stacked composition is used; phone-only card sizing never applies to TV. Loading, empty,
  short-query, results, saved-history, cached-error and Retry states keep their shared bounded
  notices within measured content space.
- Preserved: the working voice path and its authenticated-session/account/profile/context guards
  (`notificationUiReady` stays out of `currentVoiceSearchOwner`), `فلم` naming, query placeholder
  `اكتب اسم فلم او مسلسل او قناة`, filter naming, truthful offline/service classification, and the
  Home hero constant `المفضلة` caption with heart-only state changes.

Implementation status: LOCAL_ROUND R04 candidate under owner review — **PENDING**. Captures remain
OFF; automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

### 15.8 R05 owner corrections — exact Series field allocation and saved-card Delete reachability (2026-10-12)

- Supersession: the R04 TV toolbar policy of a `Modifier.weight(1f).widthIn(max = 630.dp)` field is
  superseded for TV. For the same window the Search field's outer geometry must equal the accepted
  `CatalogHeader` field slot on the Series/Movies catalogs; the visual comparison showed the
  weighted field did not preserve that equality.
- Exact TV allocation: the Search toolbar keeps the accepted hierarchy — 185dp title column,
  `11.dp - TV_PAGE_GUTTER` gap, then the field. The field is a fixed, measured width:
  row content width (`maxWidth - TV_PAGE_GUTTER`, with the accepted `padding(end = TV_PAGE_GUTTER)`
  row) minus title 185dp, minus the 3dp title gap, minus the real management slot (caption
  `ادارة الفئات` measured with `rememberTextMeasurer()` at 13sp bold plus the accepted compact
  `FocusButton` structure 6dp + 17dp + 24dp), minus 11dp, minus the accepted refresh touch size
  `homeHeaderActionTouchSizeDp()` (48dp), minus 11dp, minus the trailing `TV_PAGE_GUTTER` spacer —
  bounded by `120.dp` minimum and the accepted `630.dp` maximum. The management/refresh action
  space toward the physical LEFT stays unused; no hidden or fake controls, no weight or fill
  expansion on TV. The functional microphone stays inside the field at its physical LEFT end. The
  phone toolbar keeps the existing weight/stack policy.
- Measured equality (Mi Box 4, 1920x1080, density 2, verified by frame capture border analysis):
  Series field border columns x[385..1370], border rows y[48..146]; R05 Search field border
  columns x[385..1370], border rows y[48..146]; both text-field nodes share the same right edge
  (x=1342). Search renders no management or refresh controls.
- Saved-content Delete reachability: on TV the saved-history card body's DOWN moves focus to that
  card's own compact Delete (`حذف`); while a card's Delete owns focus the card's ancestor key
  handler returns false so it cannot steal the action's keys (`deleteFocusedIndex` guard). Delete
  UP returns to its own card body; Delete DOWN moves to the same-column next-row card body (no
  wrap when no next row); Delete LEFT/RIGHT move between neighboring Deletes in the accepted RTL
  column order and terminal directions stay on the Delete — never Clear All. Delete activation
  keeps its direct-removal behavior (no confirmation dialog exists), select-release guard and
  surviving-item focus fallback.
- Device verification: the complete graph above was verified with D-pad probes on the qualified
  Preview build (DOWN/UP/DOWN-next-row/LEFT/RIGHT/terminal-left/terminal-right/terminal-down
  focus dumps). Activation was not re-run in R05 to preserve real owner history; direct removal
  was verified in the R04 evidence. History count/order changed between probes while the owner was
  concurrently using the physical device; one probe accidentally sent a DPAD_CENTER on a focused
  Delete and the resulting state could not be fully attributed.
- Preserved: voice path and guards, `فلم` naming, query placeholder, filter naming, one-grid R10
  body, complete-card reveal, Clear All behavior, offline classification, and the Home hero
  `المفضلة` caption.

Implementation status: LOCAL_ROUND R05 candidate under owner review — **PENDING**. Captures remain
OFF; automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

### 15.9 RESUME correction — TV query editing keeps the keyboard (2026-10-12)

- Owner observation: on TV, typing one or two characters closed the system keyboard as results
  appeared; Backspace and further input also closed it.
- Source-confirmed cause: `results` is intentionally empty while the query/debounce/snapshot state
  settles; the `previouslyHadRows` effect requested the selected filter whenever the TV projection
  emptied, with no editing ownership. The field's focus loss then ended the local `tvEditing` state
  and hid the keyboard. Projection key changes could also trigger the grid removal/restoration
  paths to request card focus during typing.
- Correction: one authoritative TV editing owner hoisted to `SmartSearchContent` (the previous
  local flag moved, not duplicated). While editing, projection transitions request no
  filter/card/restored focus; the previouslyHadRows effect rechecks ownership after its frame
  suspension; the grid removal path no longer treats query/ranking key changes as deletions; the
  grid restore path is skipped while editing and rechecks ownership after every suspension; queued
  grid focus jobs are cancelled when editing begins; `readOnly` remains editing-driven only.
  Transient platform/IME window blur is not treated as navigation. One Back owner: the first Back
  ends editing and keeps the query/page (consumed with its release while the system keyboard is
  still visible; the later Back follows existing navigation once the keyboard closed itself). The
  explicit IME Search/Done action ends editing once and hands focus over without activating a
  result/filter/Delete.
- No keyboard re-show loops, forced focus/recomposition, arbitrary delays, disabled live results
  or changed query thresholds are used.
- Erratum: the R05 export labeled this work NEW although it was a correction inside the same
  section task; the RESUME record supersedes that label. The publication candidate exports the
  complete tracked/untracked patch with `--- /dev/null` headers for new files and binds its actual
  hash in the candidate metadata.
- Evidence corrections: the R05 runtime dumps represent different projections (6 → 5 → 4 saved
  items) and are not one uninterrupted sequence; unsupported sequence claims are NOT PROVEN. The
  R05 PNG frames were device-side screenshots taken despite Capture OFF and are recorded as such.
  The R05 `testDebugUnitTest` invocation was UP-TO-DATE; source-string compatibility assertions do
  not prove keyboard/D-pad behavior. Phone/voice re-verification remains NOT RUN. The disclosed
  accidental CENTER press and the saved-history count change remain recorded without attributing
  every change to the probe.
- Verified: TV IME regression exercised with the actual system keyboard on the qualified Preview
  candidate; see the publication evidence archive for the bounded input/focus records.

Implementation status: SECTION_PUBLISH candidate for the Search section — owner review PENDING.
Captures remain OFF; automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

## 16. Downloads Model 01 adoption record (2026-10-13)

On 2026-10-13 01:50:10 Asia/Riyadh the owner accepted Downloads Model 01 («ممتاز اعتمد هذا»).
The accepted reference package is `HULK-DOWNLOADS-MODEL-01-20261013.zip` (SHA-256
`803ca4ee06fa28c45704729ae238798ffc1ddc0996867b9cc3a4c27b7ea78957`). This record documents
**design-reference acceptance**. The Android implementation
(`downloads-ui-alignment-20261013`, LOCAL_ROUND R01) is under owner review — **PENDING**; no
Downloads merge, signing, tag or release is authorized or implied.

### 16.1 Adopted TV composition

- TV-only header: the accepted text-only section heading `التنزيلات` with the real item count
  (27sp/11sp, the Favorites header family) at physical RIGHT; a compact inline summary at
  physical LEFT with the real active count (existing QUEUED/CHECKING/DOWNLOADING/PAUSED/
  WAITING_* predicate), the real item count and the measured available storage. A failed storage
  read stays unknown (`المساحة غير متاحة`) instead of becoming a fabricated `0 MB`. The three
  oversized boxed summary widgets stay phone-only.
- TV settings toolbar at physical RIGHT keeps the existing `كل الشبكات` / `WiFi فقط`,
  `الجدولة الان` / real scheduled value and `متزامنة` + real value controls, callbacks,
  supported choices and persisted state; WiFi-only uses the real check glyph (no textual `✓`
  decoration), and the controls use the shared compact button family with the accepted semantic
  glyphs. No Search, Refresh, Category Management or new filter is added.
- TV body: one vertically scrolling full-width list, one row per stable `downloadId`. Each row's
  physical RIGHT-to-LEFT anatomy is landscape artwork (16:9, real record artwork with the
  existing brand fallback) → title with the reserved two-line slot and real film/season/episode
  metadata → status/telemetry column → action cluster. Fixed artwork/status/action columns and a
  weighted title column keep the columns aligned across rows; rows use the scoped dark surface
  and a restrained separator. No poster-card columns, hero or second selected-item panel.
- Actions keep the existing authoritative routing: COMPLETED `تشغيل`, FAILED `اعادة التحميل`,
  PAUSED/eligible WAITING_* `استئناف`, QUEUED/CHECKING/DOWNLOADING `ايقاف`, through the existing
  `onPlayDownload` / `onDownloadPrimaryAction` owners; the Tune priority control keeps its real selected
  state and the Delete/Cancel control keeps distinct `حذف التنزيل` / `الغاء التنزيل` semantics.
  The primary action is the accepted gold fill with the dark caption and one glyph at physical
  LEFT immediately beside the physically-RIGHT caption, one 6dp gap.
- Truthful telemetry: a known total on DOWNLOADING/PAUSED shows the real percent with the thin
  gold track and the real byte pair; an unknown total shows no percent, track, total or ETA; a
  completed row shows its real size with no decorative track; real errors and the real schedule
  line stay visible. `WAITING_NETWORK` keeps its waiting wording and is never presented as a
  proven outage.
- Focus: the existing `DownloadTvFocusGraph`, `DownloadFocusMoveTransaction`,
  `DownloadFocusHistory` and downloadId focus registry are reused with a single TV column;
  LEFT/RIGHT traverse PRIMARY → PRIORITY → CANCEL and back, the right boundary reaches the
  navigation rail, terminal LEFT and last-row DOWN stay bounded, UP/DOWN preserve the eligible
  action slot, and first-row UP reaches the matching toolbar control. Offscreen rows are revealed
  completely before focus is granted using real layout signals; the focused actionable node
  alone receives the in-bounds `goldFocusEdge`, with no scale or simultaneous row border.
- Wording: app-authored copy uses `فلم` and normalized `ا` (for example `ايقاف`, `اعادة`,
  `اولوية`, `الغاء`) while `استئناف`, `ئ`, `ؤ` and `ء` stay correct; no terminal full stop or
  ellipsis and numeric runs stay LTR. Server titles and stored content are untouched.

### 16.2 Preservation boundary

Phone/touch keeps its existing adaptive grid and `DownloadCard` composition. Home download cards,
Details download controls, rail icons/order, download engine/queue/scheduler/foreground
lifecycle/pause durability/file integrity, account/profile isolation, storage queries and
persistence are unchanged. This is a presentation/focus alignment; no migration, data reset or
new dependency is introduced.

Implementation status: LOCAL_ROUND R01 under owner review — **PENDING**. Captures remain OFF;
automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

### 16.3 Owner corrections — phone list, canonical names, Home card, download notices (2026-10-13)

Owner steering 2026-10-13 03:24 Asia/Riyadh (`HULK-DOWNLOADS-OWNER-CORRECTIONS-20261013.zip`,
SHA-256 `a581b0f4e36ad7fbea67efb2e97fd273c3d7a76812d47bad2fecf24204efd099`) supersedes only the
conflicting earlier wording; the accepted TV composition stays. The reference package's
`OWNER-MOBILE-ISSUE.png` records the phone rejection, `OWNER-HOME-NAME-DUPLICATION.jpeg` the
repeated name, `HOME-DOWNLOAD-CARD-MODEL.png` the Home card, and `ACCEPTED-APP-NOTICE.jpeg` the
accepted slim notice family. Android exact-candidate review remains **PENDING**.

- **Phone list**: the previous instruction to keep the phone poster grid is superseded. Phone
  Downloads now renders ONE full-content-width column in the same Model 01 family: portrait cards
  with compact real 16:9 artwork physically RIGHT, canonical title + independent metadata beside
  it, the real status/percent/track/bytes block below, and the compact gold primary + priority +
  Delete/Cancel action row below; one vertical scroll owner; a non-TV window uses the wide
  horizontal row only when the measured columns fit. The compact text-only heading + real
  count, inline summary (real active count, item count, measured storage; unknown stays unknown)
  and the existing settings controls (with the real WiFi-only check glyph) replace the three
  boxed summary widgets on phone as well.
- **Canonical name once**: one scoped display projection (`downloadDisplayIdentity`) is used by
  Home, Downloads and feedback. Series render the nonblank `seriesTitle` once with independent
  `الموسم N  •  الحلقة M`; movies render their canonical title once with `فلم`; the raw
  composite request title is never appended; fallback is the stored record title only when
  canonical fields are missing. Stored records, IDs, files, playback identity and owner scope
  are untouched; no duplicate-word stripping.
- **Home active-download card**: the compact model — canonical title + independent
  season/episode line, real state glyph/caption (`DOWNLOADING`, `PAUSED`, `WAITING_*`), real
  track and telemetry only for known totals, and one whole-card `onOpenDownloads` action whose
  `فتح التنزيلات` caption + physical-left chevron is an affordance, not a nested focusable.
  Up-to-four active entries, stable `downloadId` keys, horizontal browsing and the existing
  progress orientation are preserved; no Home Play/Pause/Delete controls.
- **Download notices**: download instructions/results/errors no longer use the system Toast.
  One scoped notice host above the destination layer renders the exact `ScopedHomeActionNotice`
  family (dark `0xF211120D`, `RoundedCornerShape(12.dp)`, 1dp gold `.45` border, 12/9dp padding,
  ivory 12sp/17sp text, 17dp gold glyph, 8dp gap, caption RIGHT / glyph LEFT, 560dp TV / 340dp
  phone max width, bottom content placement with phone navigation clearance). It is
  nonfocusable, transient (auto-clears), and cleared on owning screen/destination/profile/
  logout transitions. A typed `DownloadFeedback` result keeps the actual outcome semantics
  (preparing, queued, completed, pause, resume, retry, cancel, delete, WiFi, schedule,
  concurrency, priority, error) and never reports success for a button press or a previous
  owner's asynchronous result; unrelated `notify` Toast consumers are unchanged. The display
  layer strips terminal decoration and normalizes `أ/إ/آ → ا` without rewriting stored or
  server data. Android foreground-service notifications remain outside this scope.

Implementation status: SECTION_PUBLISH candidate under owner review — **PENDING**. Captures
remain OFF; automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

## 17. Remaining pages + unified action/error notices adoption record (2026-10-13)

The owner selected the remaining-pages bundle (`HULK-REMAINING-PAGES-DESIGNS-20261013-R02.zip`,
SHA-256 `1fa3317df8d7f1df4e32f9133db00d137a65675b3409fda9c472d571fd6dfd3a`) and directed its Android
implementation (`remaining-pages-ui-alignment-20261013`, LOCAL_ROUND R01). The accepted notice
reference is `reference/ACCEPTED-APP-NOTICE.jpeg` rendered by the canonical
`ScopedHomeActionNotice` in `MainShellScreen.kt`. This record documents **design-reference
acceptance and the adopted supersessions**; the Android candidate is under owner review —
**PENDING**; no merge, signing, tag or release is authorized or implied. Captures remain OFF.

### 17.1 Unified affected action/instruction/error presentation

- The affected Search/voice, Settings/preference/update, profile/security, notification and
  Growth/QR outcomes use the canonical compact notice atoms (`Color(0xF211120D)`,
  `RoundedCornerShape(12.dp)`, 1dp theme-gold border at .45 alpha, 12/9dp padding, ivory
  12sp/17sp text, one 8dp gap, 17dp gold semantic glyph, caption RIGHT/glyph LEFT, 560dp TV /
  340dp phone width cap). The duplicate `AppScopedNoticeCard` copy and its extra 2dp Spacer are
  removed; `ProfileAwareHulkApp` now renders the same canonical family.
- Notice events carry their own identity, so consecutive identical real outcomes remain distinct
  events and an obsolete timeout can never clear a newer notice. Ordinary transient notices
  remain nonfocusable and never steal focus, scroll or selection; status/empty/loading notices
  stay in their content slots; validation/PIN errors stay in context; genuinely blocking
  errors/destructive confirmations keep their bounded dark/gold dialogs with real owners.
- `VoiceSearchDelegate.showMessage` no longer uses Android Toast. Real voice outcomes route
  through a scoped feedback (owner + sequence) and are consumed only while the emitting
  account/session/profile/generation still owns the Search request; stale previous-owner results
  are dropped. Voice copy follows 05-MESSAGE-CONTRACT exactly and supersedes section 15.2's
  activity-level voice-status system-message exemption only — speech/permission/request/lifecycle
  ownership is unchanged.
- `SettingsProScreen` no longer uses raw Toasts; its affected results surface through the shared
  notice. App-authored Settings/voice/Growth/popup/update copy is normalized (`أ/إ/آ` → `ا`, no
  terminal periods/ellipses); stored/server content is never rewritten.

### 17.2 Search

- The merged Search layout/geometry, keyboard, voice callbacks and focus are preserved. Only the
  page background changes: `SmartSearchContent`'s vertical gradient is replaced by solid
  `LocalHulkColors.background`; unrelated theme tokens and legitimate gold control
  selection/focus are untouched. Board 08 remains illustrative.

### 17.3 Profile / entry / PIN arrangements

- The merged picker composition and management-row anatomy are preserved. The editor adopts the
  selected split: fields physical RIGHT, avatar/name preview physical LEFT with a separating
  line and a top Back action; narrow windows keep a condensed preview above the fields. Entry
  options adopt rows with the context pane at physical RIGHT and full-width option rows; PIN
  adopts keypad physical LEFT and profile context/masked slots physical RIGHT on TV
  (`PinPanel(splitTv)`), keeping every existing length/attempt/lockout/cancellation and
  `MAX_PROFILES`/primary/Kids/policy owner. A list-view profile deletion failure now surfaces its
  existing truthful message instead of being set but never shown.

### 17.4 Settings, notifications and QR

- Settings adopts the selected TV section-list/detail composition (list physical RIGHT beside
  the existing rail, one authoritative detail pane physical LEFT, selection separate from focus,
  deterministic list↔detail focus return and per-section scroll memory) and the phone
  list-then-detail page with Back restoration. All existing controls/values/callbacks/gating are
  mapped, not reinvented; toggles render a nonfocusable switch visual reflecting the persisted
  value, and cycle/open rows keep their real values.
- Notification Center TV rows replace the poster strip with the semantic gold bell circle and
  keep wide rows, unread state, actions LEFT and the untouched `NotificationTvFocusGraph`,
  keys/order/profile scope and open/read/delete/clear semantics.
- The new-episode alert adopts the bounded horizontal dark bar: gold bell glyph physical RIGHT,
  real title once with independent season/episode metadata, divider, and the existing
  primary/Later actions physical LEFT. Suppression/foreground precedence, presentation
  acknowledgement, once-only activation, popup focus/Back/trigger return and the summary
  Open Notifications route are unchanged; it remains an interactive foreground popup, not a
  passive notice.
- The existing Growth QR dialog adopts the selected horizontal TV family (copy physical RIGHT,
  actual QR with its white quiet zone physical LEFT, vertical divider, compact Back) and stacks
  vertically on narrow windows. Destination model, custom-QR validation/fallback, allowlists and
  real direct-link callbacks are unchanged; no endpoint, enum or phone number was invented.

Implementation status: LOCAL_ROUND R01 candidate under owner review — **PENDING**. Captures remain
OFF; automated visual validation is NOT RUN — CAPTURE NOT AUTHORIZED.

### 17.5 R02 owner rejection and scoped corrections (2026-10-13)

The owner reviewed the R01 Android candidate (`remaining-pages-ui-alignment-20261013-r01`) and
**REJECTED** it for the profile, PIN, Settings, Notification Center and Growth dialog surfaces
(`HULK-REMAINING-PAGES-OWNER-CORRECTIONS-20261013-R02.zip`, SHA-256
`3f9055fcd75826f920a3903f4ff39b730d2681ed729c6391c9c2f92b780a35d6`, independently reviewed
`FAIL`, coordinator review attributed separately on the VPS). The R02 candidate is under owner
review — **PENDING**. Captures remain OFF. This record supersedes only the conflicting 17.1–17.4
wording:

- **Profile pages**: the R01 statement that the merged picker composition stays unchanged is
  superseded. The picker adopts circular identity/Add targets with one equal measured footprint,
  identical avatar/Add bounds and reserved label/badge slots (focus, current or Kids state can
  never change item geometry). Every affected profile page drops its decorative gold radial
  overlay for the existing solid `LocalHulkColors.background`; the scoped dark surfaces and
  legitimate gold action/focus/selection remain. Every profile page Back is the compact control
  at physical RIGHT in the header, visibly separate from the title, caption RIGHT of one
  right-facing physical-LEFT glyph, with first-press Back/cancel and return to the originating
  control.
- **Switches**: the relative R01 `CenterStart` checked alignment is superseded. One shared narrow
  `EntrySwitchVisual` renders checked as theme-gold track with the light thumb physically LEFT
  and unchecked as the accepted muted track with the thumb physically RIGHT, independent of the
  ambient layout direction; the persisted Boolean is never negated and the row owns the single
  activation.
- **PIN**: the R01 compact stacked TV fallback is superseded. TV always uses the board-05 split
  (profile/context/masked slots RIGHT, one keypad LEFT, Back in the RIGHT header) fitted to the
  real usable window; the keypad owns focus only after a real placement signal and has a
  deterministic path from its bottom keys to Back. The confirmation-input regression is addressed
  by the production `PinEntryBuffer` (one buffer per stage, single completion, reset on stage
  change) plus numeric-remote digit delivery; attempt/lockout/cancellation policy is unchanged.
- **Settings**: first entry focuses the selected section row in the RIGHT section column; OK/LEFT
  opens the section and transfers to its first eligible detail action, RIGHT/Back returns to the
  selected section row, and modal closure restores the actual trigger. The separate 4dp selected
  marker and its reserved gap are removed while the accepted selected-row treatment and in-bounds
  focus edge remain. Row glyphs render physically LEFT beside their caption (Logout keeps its
  single glyph and confirmation). SERVICES contains only the real enabled website/subscription and
  support rows; ABOUT owns the app/device/version information and app update, and the Services
  focus chain no longer targets the uncomposed update action. The board-13 combined About
  visualization is superseded by this split.
- **Notification Center**: TV rows adopt board 06's wide row with horizontal actions at physical
  LEFT (left-to-right `مشاهدة الان`, `تعليم كمقروء`, `حذف`, only eligible actions) and one solid
  page background. `NotificationTvFocusGraph` follows the new physical geometry: LEFT/RIGHT
  traverse the action row, UP/DOWN change rows through the same eligible slot with an
  index-clamped fallback, and the existing stable-ID/eligibility/fallback/bulk-header behavior is
  preserved. The new-episode alert stays the accepted interactive bounded horizontal popup.
- **Growth dialogs**: boards 15–17 share one measured horizontal family; copy and the LTR
  domain/phone display are centered inside the RIGHT copy column, the actual QR with its white
  quiet zone is in the LEFT column behind a restrained divider, and the compact Back owns the
  centered bottom footer. The QR budget is derived after header/footer/insets so it can never
  exceed the available height; destination configs, allowlists, custom-QR fallback and direct
  routes are unchanged.

R01 remains recorded as rejected history; this record does not rewrite earlier acceptance
records and does not claim any new visual acceptance.

### 17.6 R03 owner rejection and four scoped corrections (2026-10-13)

The owner reviewed the R02 Android candidate and **REJECTED** four visual scopes: the Notification
Center row composition/icons, the Who-is-Watching logo/group/name-badge spacing, the HULK Services
icon placement, and the subscription/support QR modal composition
(`HULK-REMAINING-PAGES-OWNER-CORRECTIONS-20261013-R03.zip`, SHA-256
`2bc810e657f4b65b9d130306c86def1b41a18ae790bc8fd28091b8b1b45dc2f7`; coordinator review attributed
separately on the VPS). The R03 candidate is under owner review — **PENDING**. Captures remain
OFF. This record supersedes only the conflicting 17.5 wording for these four scopes:

- **Notification Center** (board 06): TV rows are flat full-width list rows with one restrained
  gold separator and content-measured height; the old 14dp rounded outer card, card
  background/border and poster-derived 132dp floor are removed. Bell/unread/title/season-episode/
  time remain grouped at physical RIGHT, horizontal eligible actions remain at physical LEFT
  (`مشاهدة الان` / `تعليم كمقروء` / `حذف`) and now carry their repository vectors (Play,
  MarkEmailRead, DeleteOutline) with the accepted EntryActionButton anatomy (caption RIGHT, 17dp
  glyph LEFT, one 6dp gap); Watch keeps the warm-gold primary treatment. Header Mark-all-read/
  Clear-all gain MarkEmailRead/ClearAll vectors and the approved Back glyph. The R02 horizontal
  focus graph, stable IDs, eligibility fallback, row/slot UP/DOWN navigation and callbacks are
  preserved; popup/other consumers keep shared defaults.
- **Who is Watching** (board 01, solid-black background): TV uses the existing `logoWidthDp`/
  `logoHeightDp` responsive source with usable-height/aspect bounds instead of the generic 68–88dp
  square cap. Item footprints are measured from the actual avatar/Add circles and the complete
  measured Arabic labels, with the adaptive `cardGapDp` between them (no invisible rectangular
  card width). The full name and `الحالي`/`اطفال` badge are separate measured blocks with an
  explicit 10dp/8dp gap and a reserved equal badge row for Add and non-current profiles, so the
  badge sits clearly below `الرئيسي` without clipping. Footer glyphs, callbacks, stable keys,
  MAX_PROFILES, current-profile semantics, safe Back and the phone grid are unchanged.
- **HULK Services** (owner direction): only these two rows use a narrow Services anatomy — the
  globe/support glyph begins at physical RIGHT beside the title/subtitle group, flexible space
  follows, and `اضغط OK` remains the last physical LEFT element. `SettingsMenuRow` defaults are
  unchanged; Logout keeps its physical-LEFT glyph, the separate ABOUT owner, corrected switch
  orientation and all other R02 Settings behavior.
- **Growth QR family** (boards 15–17): one bounded centered horizontal modal — compact QR pane at
  physical LEFT (budget derived after footer/insets, capped compact), narrow warm-gold divider,
  coherent copy pane at physical RIGHT with ALL heading/subtitle/instruction/destination centered
  in that pane; domain/phone keep explicit LTR; compact Back with the local gold caption sits
  centered in the bottom footer and restores its trigger on first press. Real encoder/configured
  destinations/allowlists/fallback and phone adaptive stacking are preserved; sample board QR/
  phone/domain are never reproduced.

R01/R02 remain recorded history; this record does not rewrite earlier decisions and does not claim
visual acceptance of the R03 candidate.

### 17.7 R04 corrections — picker logo, PIN confirmation input, review blockers (2026-10-13)

The owner added two defects to the pending correction packet (TV picker HS logo still too small;
Profile PIN confirmation accepts no digits after the first four) and the independent R03
publication review recorded two broken focus bindings plus two stale Compatibility guards
(`HULK-REMAINING-PAGES-R04-CORRECTIONS-20261013.zip`, SHA-256
`4a6824400b5a3b0f1d213900df78c771537daece4e31731ea0cac8d569cb600d`; attributed reviewer records
kept separately on the VPS). The R04 candidate is under owner review — **PENDING**. Captures
remain OFF. This record scopes only these corrections:

- **Picker TV logo**: both original logo resources have square canvases, so the R03 rectangular
  `logoWidthDp x logoHeightDp` slot height-limited the square drawable. This caller now uses the
  preferred SQUARE side from `logoWidthDp` (106/122/136dp) bounded to the real safe window and the
  space left after the unchanged title/subtitle/circles/footer neighbours. Original drawable,
  aspect, Fit scaling, colours, bytes and the phone caller are unchanged.
- **PIN confirmation input (on-device root cause)**: an isolated non-persistent Compose UI fixture
  reproduced the owner failure on the Mi Box: the confirmation stage rendered, owned keypad focus
  and clickable keys, but presses delivered to the previous stage's state, so the new stage's
  buffer/callback never received digits (and the mismatch error never appeared). The correction
  recreates the keypad subtree per stage (`key(title, resetToken)`), so each stage owns its own
  input and completion path; the buffer/stage/reset lifetime, attempt/lockout, cancellation,
  profile isolation and storage owners are unchanged.
- **Back focus bindings**: the split-TV `EntryBackAction` now binds the existing
  `cancelFocusRequester`, and `PinKey` no longer consumes a directional key when the focus request
  failed; the Growth QR `EntryBackAction` binds the existing `backRequester` in both wide and
  stacked branches. Layout/callbacks/QR centering/encoder/config/allowlists/fallback unchanged.
- **Compatibility guards**: the two stale assertions were updated to the adopted architecture
  (transient stage-scoped `PinEntryBuffer` transient ownership; Settings section-first entry with
  the eligible attached detail path) without deleting, skipping or weakening the security and
  navigation guarantees.

Reviewed on-device workflow coverage (isolated in-memory fixture, Mi Box 4): first four digits
advance once, confirmation begins empty and owns keypad focus, all four digits are accepted via
tap and supported numeric-remote keys, masked slots update, backspace removes exactly one digit,
mismatch stays editable for retry, matching confirmation saves once, cancel never saves, bottom
keypad row reaches the visible Back, direct PinPanel stage change completes both stages, and the
QR Back receives focus and dismisses once. R01–R03 records are preserved; this record supersedes
only the conflicting R03 logo-slot wording and adds the confirmed PIN stage-input cause.

## 18. Owner-directed icon-system R03 candidate (2026-10-16)

The owner authorized Codex to complete the icon package and in-app framed brand fallbacks
from the PR #351 merge, preserving R02 flat gold and increased TV banner spacing.
For this bounded brand scope, historical original-colour/immutable-byte wording is superseded:
HS/wordmark path identity remains; coloured brand fills use theme gold #E6C352, opaque brand
backdrops use #030402. System monochrome/notification masks remain system-tinted.
Adaptive icon padding is independent of in-app mark padding. Framed brand artwork is centered
and aspect-preserving, without launcher Crop, extra graphics scaling or dark Icon tint.
Actual media/channel images, outer frame geometry, semantic state colours, navigation and
PR #351 heart/focus corrections remain unchanged. R03 owner acceptance is PENDING; source
verification and static artwork inspection are not Android runtime or device visual acceptance.
