package sa.hulksa.player.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMacrobenchmarkApi::class)
@RunWith(AndroidJUnit4::class)
class FirstEntryNavigationBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Gate 3A.5 corrected first-entry navigation measurement.
     *
     * `CompilationMode.Ignore` is used deliberately: on Xiaomi API 28 the default
     * Macrobenchmark compilation reset reinstall the target package and wipes its app data.
     * Ignoring compilation management preserves the device's existing compilation/profile
     * state and the target's persistent data.
     *
     * The HOME reset/setup is performed in `setupBlock`, outside the measured journey: a fresh
     * task recreates the activity so Direct Entry lands on the authenticated main TV HOME
     * screen with no element focused. The measured journey is then the proven pure D-pad path
     * `DOWN, RIGHT, DOWN, RIGHT, RIGHT, DOWN, CENTER` that enters the navigation rail and
     * selects `MainDestination.LIVE` ("البث المباشر").
     *
     * Each measured iteration proves: target window foreground, Optional Update Overlay
     * owned/absent, starting destination HOME, resulting destination LIVE. Any iteration that
     * cannot prove the journey fails instead of recording contaminated frames.
     */
    @Test
    fun firstEntryNavigationFromTvMain() = benchmarkRule.measureRepeated(
        packageName = TargetApp.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            pressHome()
            // HOME reset/setup outside the measured journey: fresh task -> Direct Entry -> HOME.
            startActivityAndWait { intent ->
                intent.component = TargetApp.tvMainComponent()
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            device.waitForIdle()
        },
    ) {
        startActivityAndWait { intent ->
            intent.component = TargetApp.tvMainComponent()
        }

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
