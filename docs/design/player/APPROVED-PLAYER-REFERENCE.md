# HULK SA — Approved Unified Player Reference

Reference ID: HULK-PLAYER-20261004.
Owner decision: ACCEPTED DESIGN on 2026-10-04.
Owner instruction: “اعتمد النموذج بالمرجع عشان نسوي باقي المشغلات زيه”.
Scope of approval: the three final corrected Movies-player boards and the explicit rules below are the visual baseline for future Movies, Series and Live player work.
This approval does not certify an APK, authorize merge/release, or claim runtime/adaptive qualification.

## 1. Exact visual baseline

| Board | Screens and states | SHA-256 |
| --- | --- | --- |
| [01-player-more-preview.png](reference/20261004/01-player-more-preview.png) | Playback; More; seek preview; clean viewing | d26302109b13abbd2068ee5e6f350f0cf8134aa9132046043c4106f6b2fac3a0 |
| [02-player-options.png](reference/20261004/02-player-options.png) | Speed; picture size; go to time; locked | fdb925d46abc989982b28915a067728baeb1679ace8e8af9daf4f1337d2e67ae |
| [03-player-dialogs-states.png](reference/20261004/03-player-dialogs-states.png) | Resume; offline; error; loading; unlock; ended | 6d4aae03365a3012fbeb9e539a5e07fbcaf03176a525000d4c81a0a4c79c244a |

These are the final corrected image bytes from HULK-Movies-Player-TV-Designs-20261004.zip (archive SHA-256: 67ae543703ce9b0caa3e77daa2daf48f2a6408c5bedb54f1d05977a6da61f7e6).
Fourteen TV reference states across three boards. No separate phone mockups are included.
Illustrative titles, still frames and timestamps are not product data or bundled playback assets.

Suggested durable repository location:
- docs/design/player/APPROVED-PLAYER-REFERENCE.md — this contract.
- docs/design/player/reference/20261004/ — the three unchanged PNGs.
The executor should add relative image links in the repository copy and one explicit read pointer in the existing repository-local contracts section of AGENTS.md. This proposed location is not a claim that the reference has already been committed or merged.

## 2. One visual language across screens

Use the same player composition and physical control order on TV and phone. Adapt sizes, spacing, safe insets and caption wrapping to the actual window; do not create a different phone arrangement or move More and Favorite into different corners.
Use the current HULK logo asset, IBM Plex Sans Arabic typography and LocalHulkColors. Do not redraw the logo, fork the theme or sample colors from compressed reference images.
The video remains the main surface. Controls sit over a restrained bottom gradient, with a thin timeline and compact tools, without a tall full-width outlined container or oversized circular play button.
Film/series identity and Back belong in the upper area shown by the reference. Long real Arabic titles must remain readable.

### Color roles

| Role | Existing token/value |
| --- | --- |
| Accent, progress, semantic menu glyphs, focus edge | gold — #E6C352 |
| Light accent | goldBright — #FFF0A8 |
| Background | background — #030402 |
| Panel | surface — #111108 |
| Primary text | text — #FFF9EB |
| Secondary text | textMuted — #B8B3A4 |
| Text on solid primary gold | onPrimary — #100E04 |

Focus, selection and favorite state are distinct. Focus draws a thin gold edge/subtle backing without moving or enlarging layout. A selected option uses a check in its reserved check column. An active favorite uses a filled gold heart. Primary dialog actions may use the solid-gold treatment shown in the boards.

## 3. Physical RTL icon rules — authoritative correction

These rules govern menu, option and dialog rows. Physical RIGHT and LEFT mean screen coordinates, not a logical Start/End guess.

| Row type | Physical RIGHT | Physical LEFT |
| --- | --- | --- |
| Opens a child page / shows a value / selectable option | Semantic icon, with Arabic text immediately to its left | Arrow, current value and/or selected check in the trailing column |
| Direct action with one icon and no arrow/value/check | Arabic label | One semantic action icon |
| Numeric option without a semantic glyph | Numeric value | Selected check; empty column reserved otherwise |
| Text-only secondary action | Caption as shown by the reference | Do not add a decorative icon |

For selectable rows, reserve the check column on every row. Selecting another row never moves the semantic icon.
Each glyph appears once. No duplicate clock, speed, resize or restart icon in both columns.
Back chevrons are physically RIGHT-facing; opening chevrons at the physical-left edge face LEFT. Do not auto-mirror media or resize glyphs.
Bottom transport buttons retain the icon-above-caption composition shown by the boards; the menu-row rule does not move transport glyphs beside captions.

### Base More menu

| Row | Semantic glyph | Arrangement |
| --- | --- | --- |
| الانتقال الى وقت | Clock | Glyph right; label to its left; opening chevron far left |
| السرعة | Speedometer | Glyph right; label to its left; current value and chevron far left |
| حجم الصورة | Picture-frame glyph | Glyph right; label to its left; current value and chevron far left |
| من البداية | Restart | Label right; one restart glyph far left |
| قفل التحكم | Lock | Label right; one lock glyph far left |

Picture-size options use distinct glyphs: ملائم = frame; تكبير = magnifier with plus; ملء الشاشة = four corners. All three glyphs stay right; the selected check stays left.
Speed choices retain 0.75x, 1x, 1.25x, 1.5x and 2x where supported by the current player.
Go-to-time input displays physical LTR HH:MM:SS, with actual duration validation and full Arabic action captions.

## 4. Transport and preview

The common Movies/Series physical order from LEFT to RIGHT is:
المزيد → رجوع 10 ث → تشغيل / ايقاف مؤقت → تقديم 10 ث → المفضلة.
Preserve this order on phone, tablet, foldable and TV.
The timeline is physical LTR regardless of Arabic layout: past/start left, future/end right. The two ten-second controls perform -10 and +10 seconds respectively; preserve the existing engine and hardware-input owners.
Position the preview over the chosen timeline coordinate. The preview pointer and thumb share that coordinate until edge clamping requires the bubble to move while its pointer stays aligned.
Use only a real available frame. If decoding is unavailable, show a compact truthful time-only fallback; never an empty black image rectangle, fabricated still or silent whole-file download.
Cancel stale preview work when media/episode/target changes. Preview is an intent, not a second committed playback position.

## 5. Exclusive foreground states and focus

Render one authoritative foreground presentation at a time: ordinary playback, pending Resume, loading, typed error, unlock, or episode/end decision as applicable.
Resume and error decisions hide header/transport chrome and remove those nodes from focus, semantics and hit testing. Background taps and media/navigation keys cannot activate the player behind a modal.
Resume has full-caption اكمل المشاهدة, من البداية and رجوع actions, truthful saved time, and the reference progress treatment. Controls appear only after a committed Resume/Restart decision.
Opening remote content already known offline shows the offline presentation from first composition, without flashing the legacy error strip, Resume or transport controls. An unknown connectivity state must not be falsely labelled offline.
Show one error card. Offline copy/icons require actual connectivity loss; a server/media failure retains truthful error classification and copy. A failed retry does not add a second notice.
Foreground menus own focus. Back returns child → originating More row → More trigger; Back from Resume/error follows the existing single exit. Focus transfer waits for a real attached/layout target, not arbitrary sleeps.
The Series wrapper must not intercept media-next/previous and change episodes behind Resume, error, child panels, lock or end decisions.
No focus-driven scaling, reverse scroll correction, late second focus request or changed card geometry.

## 6. Section-specific capabilities

| Section | Keep its current capabilities and owners |
| --- | --- |
| Movies | Film identity, film progress/resume, current favorite identity, seek, available source/track controls and end actions |
| Series | Real series/season/episode identity, per-episode progress/resume, available previous/next callbacks, existing next-episode countdown/cancel and autoplay settings, correct series favorite identity |
| Live | Current channel identity, next/previous/last-channel behavior and current live-only controls; no fabricated seek/progress when the stream does not support it |

This permanent reference is task-neutral. The latest owner instruction and current task packet define one executable player scope. Approval of the shared look does not authorize a simultaneous three-player redesign.
For Series, preserve the same five transport slots. Existing الحلقة السابقة and الحلقة التالية actions may be placed in More as direct single-icon actions: text right, skip glyph left. Show/enable them only for authoritative available neighbors. Do not invent an episode browser, skip-intro, quality level, subtitle track, source or autoplay feature.
Keep all currently exposed capabilities reachable. Additional capability rows follow the same row rules and only bind to existing authoritative data/callbacks.
Retain the existing ordering and exactly-once transition ownership for episode changes; do not replace it with UI-owned navigation or duplicate timers.

## 7. Adaptive implementation and qualification

Use real available constraints, current adaptive utilities, safe drawing/IME/system-bar insets, TV overscan, aspect ratio, density and relevant fold/window changes.
Review phone portrait/landscape, tablet, foldable, TV 720p/1080p/4K source behavior. No fixed TV-derived dimensions, tiny text, clipped captions, enlarged full-screen dock or modal overflowing safe bounds.
Keep headers/actions stable and scroll only a constrained long menu body when necessary. Keep all options reachable by touch and D-pad.
Visual approval of these TV boards is not runtime PASS on other devices. Every untested layout/runtime state remains explicitly NOT RUN/BLOCKED as appropriate.

## 8. Authority and adoption boundary

The latest owner-approved icon rule above supersedes earlier inconsistent icon placements and earlier all-icons-left More drafts.
This document is a visual/product reference, subordinate to current owner scope and live repository engineering contracts. It adds no merge, release, signing, capture or device-data authority.
Design acceptance is ACCEPTED. Acceptance of a newly implemented APK remains PENDING until the owner reviews that exact candidate.
Future work should read this file and the matching image bytes directly, rather than relying on another account's chat history.

