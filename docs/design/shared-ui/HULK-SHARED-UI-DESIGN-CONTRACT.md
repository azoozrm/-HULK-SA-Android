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
