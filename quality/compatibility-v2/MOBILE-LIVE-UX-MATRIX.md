# v1.6 Mobile Live UX + Final Qualification

This batch qualifies the existing mobile Live TV Pro experience after merged PR #189. It is intentionally regression/qualification focused: no Player or Main UI production code is changed unless a concrete mobile bug is reproduced.

## Automated gate

- Phone window remains edge-to-edge with transparent system bars and display-cutout support.
- Small phones stay portrait outside Player and use sensor-landscape in Player; large-screen Android devices remain adaptive.
- Live main content uses safe-drawing insets and the stable mobile bottom navigation respects navigation-bar insets.
- Adaptive classification continues to derive from the live window size for compact, medium and expanded layouts.
- Live Channel Browser keeps separate compact stacked and wider two-pane layouts.
- Live touch tap toggles controls without rebuilding playback state.
- Live vertical swipe keeps the established channel-change behavior and routes adjacent changes through queued/coalesced zapping.
- Back closes Channel Browser / Picture Size before exiting playback.

## Physical phone / adaptive matrix

### Phone portrait — Live page

- Live page fills the available width without unintended black gutters.
- Status bar and display cutout/notch do not cover content.
- Bottom navigation remains visible, stable and above the gesture/navigation area.
- Category/channel content is not clipped at either horizontal edge.
- Opening a Live channel transitions cleanly into Player.

### Phone landscape — Live Player

- Player uses the full display and remains immersive.
- Controls fit without clipping.
- Previous / Play-Pause / Next / Reload / Mute / Picture Size remain usable by touch.
- Picture Size opens and closes cleanly.
- Channel Browser fits the landscape window and remains usable.
- Returning from Player restores the expected app orientation without flicker or a stuck landscape state.

### Touch UX

- Single tap shows/hides Player controls once.
- Vertical swipe changes one relative Live channel per completed gesture.
- Rapid consecutive swipes retain queued/coalesced zapping and settle on the last intended channel.
- Taps on controls do not accidentally open Channel Browser.
- Taps inside Channel Browser do not accidentally toggle Player controls behind it.
- Back closes the top-most overlay before leaving Player.

### Adaptive regression

Validate when hardware is available:

- Phone portrait.
- Phone landscape.
- Tablet portrait/landscape.
- Foldable compact and expanded states.
- TV 720p regression.
- TV 1080p regression.
- TV 4K regression.

## Preserved

- PR #186 Live TV Pro Foundation.
- PR #187 Channel Zapping Pro.
- PR #188 Live Player Controls Pro.
- PR #189 TV Remote + Focus Qualification.
- Existing Recovery / Stability implementation remains untouched in this batch.
- No package, Application ID, version, versionCode, signing, endpoint, reseller API/auth, brand, logo, colors, App Name or ABI changes.
- No v1.7 work.
