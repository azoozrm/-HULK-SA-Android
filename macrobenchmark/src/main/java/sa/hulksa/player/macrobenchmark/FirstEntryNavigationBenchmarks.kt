package sa.hulksa.player.macrobenchmark

import android.view.KeyEvent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstEntryNavigationBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun firstEntryNavigationFromTvMain() = benchmarkRule.measureRepeated(
        packageName = TargetApp.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait { intent ->
            intent.component = TargetApp.tvMainComponent()
        }
        device.waitForIdle()
        repeat(3) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            device.waitForIdle()
        }
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
        device.waitForIdle()
    }
}
