# HULK SA — Project Context and Official Roadmap

Last updated: 2026-07-25

## Source of truth

- Repository: `azoozrm/-HULK-SA-Android`
- Active working branch: `v0913-fixes`
- Never modify `main`.
- Build APKs only through GitHub Actions.
- After a successful build, deliver the APK only unless the user explicitly asks for reports or source archives.
- Current stable installed version: `v0.9.1.12`.
- Current pending stabilization version: `v0.9.1.14`.
- Latest `v0.9.1.14` workflow attempts failed before producing an APK or artifact; diagnose and fix the workflow/build path before retrying.

## Product decisions already adopted

### Implemented

- Smart Home.
- Hero content area.
- Continue Watching.
- Watch History foundation.
- Basic recommendations.
- Basic unified search.
- Recently Added rows.
- Highest-rated rows.
- Because You Watched.
- Suggested For You.
- Last watched channel.
- Favorites.
- Downloads and download management.
- Live TV channel surfing:
  - Up: previous channel.
  - Down: next channel.
  - Continuous surfing.
  - Hide OSD while surfing.
  - Show controls only with OK.

### Removed or excluded

- EPG: excluded because the server does not provide EPG data.
- HULK Cloud: removed from the roadmap.
- Downloads as a future phase: removed because downloads are already implemented.
- Stream Inspector from customer UI: removed; engineering diagnostics belong only in HULK Operations.
- Audio investigation: stopped because provider streams are the known source of audio limitations.

## Official execution order

1. Stabilize and successfully build `v0.9.1.14`.
2. Profiles and personalization linked to the existing Smart Home.
3. Recommendation engine.
4. Advanced Search.
5. Professional details pages.
6. First production version of HULK AI.
7. Player Pro and Live TV Pro.
8. Collections and Kids Mode.
9. Account and advanced settings.
10. Android TV premium UX.
11. Smart Playback and Cinema Mode.
12. X-Ray and ratings/awards.
13. HULK Operations, developed gradually.

# Official Roadmap

## Phase 1 — Stability and Foundation

- Fix GitHub Actions and produce a successful `v0.9.1.14` APK.
- Stabilize Continue Watching.
- Improve Watch History.
- Expand basic search safely.
- Verify Recently Added uses real item timestamps.
- Review current recommendations and reduce random or duplicate results.
- Fix missing posters and incomplete metadata.
- Review every screen.
- Improve startup speed, image loading, caching, memory use, and D-pad stability.
- Prevent freezes, leaks, broken focus, and player regressions.
- Do not introduce a major new system before this phase is stable.

## Phase 2 — Profiles and Personalization

Profiles must be implemented before personalized greetings and recommendations.

### Multi Profile

- Multiple profiles.
- Profile name.
- Avatar.
- Profile selection at app launch.
- Edit and delete profile.
- Optional PIN.
- Primary profile.
- Foundation for Kids profile.

Each profile has independent:

- Continue Watching.
- Watch History.
- Favorites.
- Last watched channel.
- Recommendations.
- Search History.
- Collections.
- Playback preferences.

### Existing Smart Home personalization

Do not rebuild Smart Home from scratch. Improve and connect the current implementation to profiles:

- Greeting such as `مساء الخير يا عزوز`.
- Profile-aware Hero.
- Because You Watched.
- Suggested For You.
- Most watched.
- Added today.
- Live matches now when available in actual server content.
- Personalized ordering of rows.
- Prevent the same item from appearing repeatedly across rows.
- Hide empty rows automatically.
- Improve image and row loading.
- Improve D-pad focus behavior.

## Phase 3 — Recommendation Engine

Use real profile activity:

- Watch history.
- Watch duration.
- Completion or early abandonment.
- Favorite genres.
- Favorite actors and directors when metadata exists.
- Favorites.
- Search history.
- Similar content.
- Preferred years and languages.

Examples of generated rows:

- Because you watched Interstellar.
- Action movies for you.
- Short series for the weekend.
- Continue these first.
- New releases matching your taste.

## Phase 4 — Advanced Search

One global search covering:

- Movie.
- Series.
- Episode.
- Channel.
- Actor.
- Director.
- Writer.
- Genre.
- Year.
- Country.
- Language.
- Quality.

Capabilities:

- Arabic and English search.
- Normalize Arabic hamza variants and spacing.
- Suggestions while typing.
- Instant results.
- Search history.
- Typo-tolerant matching.
- Relevance ranking.
- Clear content-type labels.
- Filters for all, movies, series, episodes, channels, year, genre, rating, country, language, quality, actor, director, and newest.

Actor/director search must depend on real Xtream metadata and a verified local index; never add fake filters that return no real results.

## Phase 5 — Professional Details Pages

- Backdrop.
- Poster.
- Arabic and English title.
- Synopsis.
- Trailer.
- Images.
- Cast.
- Director and writer.
- Year and runtime.
- Genres.
- Country and language.
- Age rating when available.
- Available quality and sources when genuinely available.
- Seasons and episodes.
- Ratings and awards from valid sources only.
- Similar content.
- Watch, resume, favorite, and add-to-collection actions.

## Phase 6 — HULK AI

HULK AI is a product-wide intelligence layer, not merely a chat page.

Locations:

- Home.
- Search.
- Details pages.
- Live TV.
- Player when appropriate.

Example requests:

- `ابي فيلم اكشن جديد بدون رعب ومدته اقل من ساعتين`.
- `عندي ساعة فقط رشح لي شي`.
- `ابي مسلسل قصير اخلصه في الويكند`.
- `ابي شي يشبه Interstellar`.
- `ابي فيلم عائلي يضحك`.
- `ابي اخر مباراة لريال مدريد الموجودة في السيرفر`.

Behavior:

- Return only real content present in the HULK library.
- Briefly explain why each recommendation fits.
- Open the selected content directly.
- Build watch lists.
- Filter by duration, genre, year, rating, mood, and profile preferences.
- Never invent unavailable content.

Later extensions:

- What should I watch now?
- Previous-season recap.
- Find a specific episode.
- Compare two titles.
- Build a full movie night.
- Natural voice search.

## Phase 7 — Player Pro

Show options only when the source actually supports them:

- Auto quality.
- Quality selection.
- Server/source selection.
- Audio track selection.
- Language and 5.1 labels.
- Subtitle management.
- Subtitle size, color, and position.
- Playback speed: 0.75, 1, 1.25, 1.5, 2x.
- Aspect Ratio.
- Better buffering.
- Accurate resume.
- Full remote control.
- Fallback to an alternate real source when available.

## Phase 8 — Live TV Pro

No EPG.

- Channel preview when technically feasible.
- Return to last channel.
- Previous-channel shortcut.
- Quick Zapping.
- Mini channel list over playback.
- Advanced favorites.
- Recent channels.
- Picture in Picture.
- Browse while channel continues.
- Multi View up to four streams only when device and server capacity allow.
- Faster switching.
- Graceful handling of dead channels.

## Phase 9 — Collections

- Watchlist.
- User-created collections.
- Watch Later.
- Family Movies.
- Weekend Series.
- Favorites remain separate.
- Fast add/remove.
- Per-profile collections.
- AI-generated collections.

## Phase 10 — Kids Mode

Kids Mode belongs to profiles:

- Kids profile.
- PIN to exit.
- Hide inappropriate content.
- Hide blocked channels and sections.
- Simplified interface.
- Kids recommendations.
- Block account/settings access.
- Age/rating controls.
- Optional watch-time limits later.

## Phase 11 — Account and Subscription

Display only real server-supported information and actions:

- Subscription information.
- Username.
- Connection status.
- Expiry date.
- Remaining days.
- Allowed connections.
- Linked devices if the server exposes them.
- Renewal link.
- Password change only if backend supports it.
- Logout.
- Logout-all-devices only if genuinely supported.

## Phase 12 — Settings Pro

- Language.
- Theme.
- Default quality.
- Autoplay.
- Next episode.
- Skip Intro.
- Skip Credits.
- Subtitle settings.
- Font size.
- Audio settings.
- UI animations.
- Navigation sounds.
- Accessibility.
- Reduce-motion mode for weaker devices.
- Clear cache.
- App information.
- Connection test.

## Phase 13 — Android TV Premium UX

- Focus scaling.
- Clear focus borders.
- Smooth transitions.
- Reliable focus restoration.
- Return to the same item after closing details.
- Stable navigation speed.
- Optional subtle navigation sound.
- Prevent random focus jumps.
- Improve D-pad behavior on every screen.
- Apple TV-inspired motion without heavy effects.
- Reduced effects for weak devices.

TV remotes generally do not provide physical vibration, so use subtle visual and sound feedback instead.

## Phase 14 — Smart Playback

- Skip Intro.
- Skip Credits.
- Next episode.
- Autoplay.
- Countdown to next episode.
- Store intro/credits timing only through a verifiable method.
- Allow disabling these features.

Do not fake intro detection.

## Phase 15 — Cinema Mode

- Gradual screen dimming.
- Short HULK logo animation.
- Cinematic transition.
- Start content immediately afterward.
- Optional disable setting.
- Do not replay the intro when resuming or switching source.

## Phase 16 — X-Ray

Start with title-level information, then improve only when reliable scene timing data exists:

- Cast names.
- Character names.
- Short actor information.
- Other actor works available in HULK.
- Title rating.
- Episode/movie information.
- Similar content.

## Phase 17 — Ratings and Awards

Use only real and legally accessible data:

- TMDB.
- IMDb when available through a valid source.
- Rotten Tomatoes when legally available.
- Metacritic when legally available.
- Awards and nominations.
- Ranking within genre.

Never display fabricated external ratings.

## Phase 18 — HULK Operations

Developer-only operations room, hidden from normal users:

- App diagnostics.
- API and login tests.
- Xtream section tests.
- Poster/image validation.
- Player and stream checks.
- Channel startup timing.
- Memory usage.
- Crash logs.
- Network errors.
- Cache state.
- Search/index tests.
- Continue Watching tests.
- Export diagnostic report.
- Feature Flags for staged rollouts.

## Additional UI/content rules

- Product name: `HULK SA`.
- Preserve approved logo and colors.
- Arabic labels must remain correct and readable; do not corrupt hamzas or Arabic words.
- In player controls, right should rewind/back and left should seek forward according to the approved RTL remote behavior.
- `قائمتي` must remain without unwanted diacritics or malformed spelling.
- The running-download card on Home must be clickable.
- Downloads must show device storage.
- Active downloads must provide pause/resume controls, not only a static `جاري التحميل` label.

## New-chat recovery instruction

At the beginning of another conversation, retrieve this file from branch `v0913-fixes`, inspect the latest commits, workflow runs, artifacts, and relevant source files before making claims or continuing development. GitHub is the authoritative source for code and build state; conversational memory is supporting context only.
