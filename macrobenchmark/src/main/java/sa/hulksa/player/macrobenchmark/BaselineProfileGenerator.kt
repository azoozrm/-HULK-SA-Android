package sa.hulksa.player.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gate 3B baseline profile generation.
 *
 * Reuses the proven Gate 3A.5 first-entry navigation contract owned by
 * [TvFirstEntryNavigation]. From the authenticated benchmark target's Direct Entry HOME
 * screen it owns/rejects the Optional Update Overlay, proves HOME before navigation,
 * executes the exact pure D-pad journey ([TvFirstEntryNavigation.JOURNEY_KEYS]) into
 * `MainDestination.LIVE`, and proves LIVE after navigation.
 *
 * Generation fails closed: an unauthenticated target, an unowned Optional Update Overlay,
 * or an unproven HOME/LIVE destination aborts the run instead of emitting a contaminated
 * profile. This class intentionally does not define a second navigation sequence.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(TargetApp.PACKAGE) {
        startActivityAndWait { intent ->
            intent.component = TargetApp.tvMainComponent()
        }
        device.waitForIdle()

        require(TvFirstEntryNavigation.awaitForegroundWindow(device, TargetApp.PACKAGE)) {
            "Target package window did not become foreground"
        }
        require(TvFirstEntryNavigation.ownOptionalUpdate(device)) {
            "Optional Update Overlay was present and could not be dismissed: " +
                TvFirstEntryNavigation.observedSummary(device)
        }
        require(!TvFirstEntryNavigation.isOptionalUpdatePresent(device)) {
            "Optional Update Overlay is still present before the D-pad sequence"
        }
        require(TvFirstEntryNavigation.awaitHomeDestination(device)) {
            "Starting destination HOME was not proven before the D-pad sequence: " +
                TvFirstEntryNavigation.observedSummary(device)
        }

        TvFirstEntryNavigation.JOURNEY_KEYS.forEach { keyCode ->
            device.pressKeyCode(keyCode)
            device.waitForIdle()
        }

        require(TvFirstEntryNavigation.awaitLiveDestination(device)) {
            "Destination LIVE was not proven after the D-pad sequence: " +
                TvFirstEntryNavigation.observedSummary(device)
        }
    }
}
