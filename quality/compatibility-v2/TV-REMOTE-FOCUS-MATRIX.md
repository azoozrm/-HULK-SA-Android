# v1.6 TV Remote + Focus Qualification

This batch qualifies the already-implemented Live TV Pro remote/focus behavior after PR #188. It does not reopen or rebuild network-disconnect recovery; Live, Movie, and Series reconnect behavior is preserved as existing functionality.

## Automated gate

- Picture Size / Simple Options receives focus immediately when opened.
- OK inside an option panel cannot fall through and open the channel browser over it.
- Channel Up / Media Next use the same queued Next-channel path.
- Channel Down / Media Previous use the same queued Previous-channel path.
- Last Channel remains available.
- Browser and option overlays isolate playback-surface key handling.
- Main Live category row restores its focus context when re-entered from the channel list.

## Physical TV / receiver matrix

Test on 720p, 1080p, and 4K when available.

1. Playback surface, controls hidden
   - OK opens channel browser.
   - Up / Channel+ / Media Next changes to the next channel.
   - Down / Channel- / Media Previous changes to the previous channel.
   - Left/Right reveals player controls without losing focus.

2. Live controls visible
   - Left/Right moves through the single control row.
   - OK activates the focused action once.
   - Picture Size opens with focus already inside Fit/Zoom/Fill.
   - Selecting a Picture Size option does not open the channel browser behind it.
   - Back closes the current overlay before leaving playback.

3. Channel browser
   - Focus stays inside browser while it is open.
   - Returning upward from the first channel returns to the active/selected category context.
   - Favorites and recent categories keep their current navigation context.
   - Back closes the browser and returns focus to playback.

4. Regression only
   - Rapid zapping remains queued/coalesced.
   - Channel-change indicator remains stable.
   - Existing network disconnect/reconnect recovery is not redesigned in this batch.
